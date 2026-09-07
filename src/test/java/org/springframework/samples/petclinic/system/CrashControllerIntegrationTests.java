/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

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
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.samples.petclinic.PetClinicApplication;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration Test for {@link CrashController}.
 *
 * @author Alex Lutz
 */
// NOT Waiting https://github.com/spring-projects/spring-boot/issues/5574
@SpringBootTest(classes = PetClinicApplication.class, webEnvironment = RANDOM_PORT,
		properties = "management.endpoints.access.default=none")
@ActiveProfiles("test")
class CrashControllerIntegrationTests {

	private static final Pattern CSRF = Pattern.compile(
			"name=[\"']_csrf[\"'][^>]*value=[\"']([^\"']+)[\"']|value=[\"']([^\"']+)[\"'][^>]*name=[\"']_csrf[\"']");

	@LocalServerPort
	private int port;

	@Test
	void triggerExceptionJsonDoesNotDiscloseExceptionDetails() throws Exception {
		Browser staff = browser();
		staff.login("staff", "staff123");
		HttpResponse<String> response = staff.get("/oups", "application/json");

		assertThat(response.statusCode()).isEqualTo(500);
		assertThat(response.body()).contains("\"status\":500", "\"error\":\"Internal Server Error\"")
			.doesNotContain("Expected: controller used", "java.lang.RuntimeException");
	}

	@Test
	void triggerExceptionHtmlUsesLocalizedApplicationLayoutWithoutDetails() throws Exception {
		Browser staff = browser();
		staff.login("staff", "staff123");
		HttpResponse<String> response = staff.get("/oups", "text/html");

		assertThat(response.statusCode()).isEqualTo(500);
		assertThat(response.body())
			.contains("<nav", "<main", "Something happened...", "An internal server error occurred.")
			.doesNotContain("Expected: controller used", "java.lang.RuntimeException", "Whitelabel Error Page",
					"This application has no explicit mapping for");
	}

	private Browser browser() {
		return new Browser(this.port);
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

		private HttpResponse<String> get(String path, String accept) throws Exception {
			return this.client.send(
					HttpRequest.newBuilder(this.baseUri.resolve(path)).header("Accept", accept).GET().build(),
					HttpResponse.BodyHandlers.ofString());
		}

		private void login(String username, String password) throws Exception {
			HttpResponse<String> loginPage = get("/login", "text/html");
			Map<String, String> form = new LinkedHashMap<>();
			form.put("username", username);
			form.put("password", password);
			form.put("_csrf", csrf(loginPage.body()));
			String body = form.entrySet()
				.stream()
				.map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
				.reduce((left, right) -> left + "&" + right)
				.orElse("");
			HttpRequest request = HttpRequest.newBuilder(this.baseUri.resolve("/login"))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
			assertThat(this.client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(302);
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
