package org.springframework.samples.petclinic;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = PetClinicApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class PetClinicConcurrencyTests {

	private static final Pattern CSRF = Pattern.compile(
			"name=[\"']_csrf[\"'][^>]*value=[\"']([^\"']+)[\"']|value=[\"']([^\"']+)[\"'][^>]*name=[\"']_csrf[\"']");

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	public void testDuplicatePetNameRaceConditionIsBlocked() throws Exception {
		int ownerId = 1;
		String duplicatePetName = "ConcurrencyTestPet";
		this.jdbc.update("delete from pets where owner_id = ? and lower(name) = lower(?)", ownerId, duplicatePetName);
		int initialPetCount = petCount(ownerId);

		int threadCount = 2;
		ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
		CountDownLatch readyLatch = new CountDownLatch(threadCount);
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch doneLatch = new CountDownLatch(threadCount);

		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failureCount = new AtomicInteger(0);

		for (int i = 0; i < threadCount; i++) {
			executorService.submit(() -> {
				try {
					Browser browser = new Browser(this.port);
					browser.login("staff", "staff123");
					HttpResponse<String> form = browser.get("/owners/" + ownerId + "/pets/new");
					readyLatch.countDown();
					startLatch.await(); // Wait to start simultaneously
					HttpResponse<String> response = browser.post("/owners/" + ownerId + "/pets/new", form,
							Map.of("name", duplicatePetName, "birthDate", "2020-01-01", "type", "cat"));

					if (response.statusCode() == 302) {
						successCount.incrementAndGet();
					}
					else {
						assertThat(response.statusCode()).isEqualTo(200);
						assertThat(response.body()).contains("is already in use");
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
			startLatch.countDown();
			assertThat(ready).isTrue();
			boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
			assertThat(completed).isTrue();
		}
		finally {
			startLatch.countDown();
			executorService.shutdownNow();
		}

		try {
			assertThat(successCount.get()).isEqualTo(1);
			assertThat(failureCount.get()).isEqualTo(1);
			assertThat(petCount(ownerId)).isEqualTo(initialPetCount + 1);
			assertThat(
					this.jdbc.queryForObject("select count(*) from pets where owner_id = ? and lower(name) = lower(?)",
							Integer.class, ownerId, duplicatePetName))
				.isOne();
		}
		finally {
			this.jdbc.update("delete from pets where owner_id = ? and lower(name) = lower(?)", ownerId,
					duplicatePetName);
		}
	}

	private int petCount(int ownerId) {
		return this.jdbc.queryForObject("select count(*) from pets where owner_id = ?", Integer.class, ownerId);
	}

	private static final class Browser {

		private final URI baseUri;

		private final HttpClient client;

		private Browser(int port) {
			this.baseUri = URI.create("http://localhost:" + port);
			CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
			this.client = HttpClient.newBuilder()
				.cookieHandler(cookies)
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
		}

		private HttpResponse<String> get(String path) throws Exception {
			return this.client.send(HttpRequest.newBuilder(this.baseUri.resolve(path)).GET().build(),
					HttpResponse.BodyHandlers.ofString());
		}

		private void login(String username, String password) throws Exception {
			HttpResponse<String> loginPage = get("/login");
			HttpResponse<String> response = post("/login", loginPage,
					Map.of("username", username, "password", password));
			assertThat(response.statusCode()).isEqualTo(302);
		}

		private HttpResponse<String> post(String path, HttpResponse<String> page, Map<String, String> values)
				throws Exception {
			Map<String, String> form = new LinkedHashMap<>(values);
			form.put("_csrf", csrf(page.body()));
			String body = form.entrySet()
				.stream()
				.map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
				.reduce((left, right) -> left + "&" + right)
				.orElse("");
			HttpRequest request = HttpRequest.newBuilder(this.baseUri.resolve(path))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
			return this.client.send(request, HttpResponse.BodyHandlers.ofString());
		}

		private String csrf(String html) {
			var matcher = CSRF.matcher(html);
			if (!matcher.find()) {
				throw new IllegalStateException("No CSRF token in page");
			}
			return matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
		}

		private String encode(String value) {
			return URLEncoder.encode(value, StandardCharsets.UTF_8);
		}

	}

}
