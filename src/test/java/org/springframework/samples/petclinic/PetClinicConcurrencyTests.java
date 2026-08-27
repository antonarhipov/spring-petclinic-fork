package org.springframework.samples.petclinic;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
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

	private String extractCsrfToken(String html) {
		if (html == null) {
			return null;
		}
		Pattern pattern = Pattern.compile("name=[\"']_csrf[\"'][^>]*value=[\"']([^\"']+)[\"']");
		Matcher matcher = pattern.matcher(html);
		if (matcher.find()) {
			return matcher.group(1);
		}
		Pattern pattern2 = Pattern.compile("value=[\"']([^\"']+)[\"'][^>]*name=[\"']_csrf[\"']");
		Matcher matcher2 = pattern2.matcher(html);
		if (matcher2.find()) {
			return matcher2.group(1);
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

		CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
		HttpClient httpClient = HttpClient.newBuilder()
			.cookieHandler(cookieManager)
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
		RestTemplate template = new RestTemplate(new JdkClientHttpRequestFactory(httpClient));

		String baseUrl = "http://localhost:" + port;

		// 1. Fetch /login to get initial CSRF token
		ResponseEntity<String> loginPageResponse = template.getForEntity(baseUrl + "/login", String.class);
		String loginCsrfToken = extractCsrfToken(loginPageResponse.getBody());

		// 2. Perform POST /login to authenticate as george
		HttpHeaders loginPostHeaders = new HttpHeaders();
		loginPostHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		MultiValueMap<String, String> loginForm = new LinkedMultiValueMap<>();
		loginForm.add("username", "george");
		loginForm.add("password", "george123");
		if (loginCsrfToken != null) {
			loginForm.add("_csrf", loginCsrfToken);
		}
		HttpEntity<MultiValueMap<String, String>> loginPostEntity = new HttpEntity<>(loginForm, loginPostHeaders);
		template.postForEntity(baseUrl + "/login", loginPostEntity, String.class);

		// 3. Fetch /owners/1/pets/new to get form CSRF token
		ResponseEntity<String> formResponse = template.getForEntity(baseUrl + "/owners/" + ownerId + "/pets/new",
				String.class);
		String formCsrfToken = extractCsrfToken(formResponse.getBody());

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

					MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
					map.add("name", duplicatePetName);
					map.add("birthDate", "2020-01-01");
					map.add("type", "cat");
					if (formCsrfToken != null) {
						map.add("_csrf", formCsrfToken);
					}

					HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

					ResponseEntity<String> response = template
						.postForEntity(baseUrl + "/owners/" + ownerId + "/pets/new", request, String.class);

					String body = response.getBody();
					// If the response redirected to Owner Information and does not have
					// form errors, it succeeded.
					boolean isSuccess = response.getStatusCode().is2xxSuccessful() && body != null
							&& body.contains("Owner Information") && !body.contains("has-error");
					if (isSuccess) {
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
