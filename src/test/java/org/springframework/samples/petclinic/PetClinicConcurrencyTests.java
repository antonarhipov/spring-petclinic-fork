package org.springframework.samples.petclinic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class PetClinicConcurrencyTests {

	@LocalServerPort
	private int port;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private RestTemplateBuilder restTemplateBuilder;

	private String extractCsrfToken(String html) {
		if (html == null) {
			return null;
		}
		Matcher matcher = Pattern.compile("name=[\"']_csrf[\"']\\s+value=[\"']([^\"']+)[\"']").matcher(html);
		if (matcher.find()) {
			return matcher.group(1);
		}
		matcher = Pattern.compile("value=[\"']([^\"']+)[\"']\\s+name=[\"']_csrf[\"']").matcher(html);
		if (matcher.find()) {
			return matcher.group(1);
		}
		return null;
	}

	@Test
	public void testDuplicatePetNameRaceConditionIsBlocked() throws Exception {
		int ownerId = 1;
		Optional<Owner> initialOwnerOpt = ownerRepository.findById(ownerId);
		assertThat(initialOwnerOpt).isPresent();
		Owner owner = initialOwnerOpt.get();

		int initialPetCount = owner.getPets().size();
		String duplicatePetName = "ConcurrencyTestPet";

		// Ensure duplicate pet name does not exist yet
		assertThat(owner.getPet(duplicatePetName)).isNull();

		java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
			.followRedirects(java.net.http.HttpClient.Redirect.NEVER)
			.build();
		RestTemplate template = restTemplateBuilder.baseUri("http://localhost:" + port)
			.requestFactory(() -> new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient))
			.build();

		// 1. Get /login to get initial CSRF and session
		ResponseEntity<String> loginPageResponse = template.getForEntity("/login", String.class);
		String initialCsrf = extractCsrfToken(loginPageResponse.getBody());
		String initialCookie = null;
		List<String> cookies = loginPageResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
		if (cookies != null && !cookies.isEmpty()) {
			initialCookie = cookies.get(0).split(";")[0];
		}

		// 2. Perform form login with initial session and CSRF
		HttpHeaders loginHeaders = new HttpHeaders();
		loginHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		if (initialCookie != null) {
			loginHeaders.set(HttpHeaders.COOKIE, initialCookie);
		}
		MultiValueMap<String, String> loginMap = new LinkedMultiValueMap<>();
		loginMap.add("username", "staff");
		loginMap.add("password", "staff123");
		if (initialCsrf != null) {
			loginMap.add("_csrf", initialCsrf);
			loginHeaders.set("X-CSRF-TOKEN", initialCsrf);
		}
		ResponseEntity<String> loginResponse = template.postForEntity("/login",
				new HttpEntity<>(loginMap, loginHeaders), String.class);

		// Authenticated session cookie
		String authSessionCookie = initialCookie;
		List<String> authCookies = loginResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
		if (authCookies != null && !authCookies.isEmpty()) {
			authSessionCookie = authCookies.get(0).split(";")[0];
		}
		final String sessionCookie = authSessionCookie;

		// 3. Get /owners/1/pets/new form with authenticated session
		HttpHeaders formHeaders = new HttpHeaders();
		if (sessionCookie != null) {
			formHeaders.set(HttpHeaders.COOKIE, sessionCookie);
		}
		ResponseEntity<String> formResponse = template.exchange("/owners/" + ownerId + "/pets/new", HttpMethod.GET,
				new HttpEntity<>(formHeaders), String.class);
		String formCsrf = extractCsrfToken(formResponse.getBody());
		final String finalCsrf = (formCsrf != null) ? formCsrf : initialCsrf;

		int threadCount = 2;
		ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
		CountDownLatch readyLatch = new CountDownLatch(threadCount);
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch doneLatch = new CountDownLatch(threadCount);

		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failureCount = new AtomicInteger(0);

		for (int i = 0; i < threadCount; i++) {
			executorService.submit(() -> {
				readyLatch.countDown();
				try {
					startLatch.await(); // Wait to start simultaneously

					HttpHeaders headers = new HttpHeaders();
					headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
					if (sessionCookie != null) {
						headers.set(HttpHeaders.COOKIE, sessionCookie);
					}

					MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
					map.add("name", duplicatePetName);
					map.add("birthDate", "2020-01-01");
					map.add("type", "cat");
					if (finalCsrf != null) {
						headers.set("X-CSRF-TOKEN", finalCsrf);
						map.add("_csrf", finalCsrf);
					}

					HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

					ResponseEntity<String> response = template.postForEntity("/owners/" + ownerId + "/pets/new",
							request, String.class);

					String body = response.getBody();
					// If the response page contains the duplicate validation error, it
					// was blocked
					if ((response.getStatusCode().is3xxRedirection() || response.getStatusCode().is2xxSuccessful())
							&& (body == null || !body.contains("is already in use"))) {
						successCount.incrementAndGet();
					}
					else {
						failureCount.incrementAndGet();
					}
				}
				catch (Exception e) {
					failureCount.incrementAndGet();
				}
				finally {
					doneLatch.countDown();
				}
			});
		}

		try {
			boolean ready = readyLatch.await(10, TimeUnit.SECONDS);
			assertThat(ready).isTrue();
			startLatch.countDown();
			boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
			assertThat(completed).isTrue();
		}
		finally {
			executorService.shutdown();
		}

		Owner updatedOwner = ownerRepository.findById(ownerId).get();
		int newPetCount = updatedOwner.getPets().size();

		System.out.println("--- Concurrency Test Assertions ---");
		System.out.println("Successful additions: " + successCount.get());
		System.out.println("Failed additions: " + failureCount.get());
		System.out.println("Original Pet Count: " + initialPetCount);
		System.out.println("Final Pet Count: " + newPetCount);

		// With the fix, exactly ONE concurrent request must succeed
		assertThat(successCount.get()).isEqualTo(1);
		assertThat(newPetCount).isEqualTo(initialPetCount + 1);

		long countWithDuplicateName = updatedOwner.getPets()
			.stream()
			.filter(p -> duplicatePetName.equalsIgnoreCase(p.getName()))
			.count();
		assertThat(countWithDuplicateName).isEqualTo(1);
	}

}
