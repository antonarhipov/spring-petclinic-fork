/*
 * Copyright 2012-2026 the original author or authors.
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

package org.springframework.samples.petclinic;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Random-port smoke test proving public and authenticated application wiring (AC-139).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SmokeTests {

	private static final Pattern CSRF_VALUE = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

	@LocalServerPort
	private int port;

	@Test
	void seededOwnerCanLoadLoginAndAuthenticatedPage() throws Exception {
		HttpClient client = HttpClient.newBuilder()
			.cookieHandler(new CookieManager())
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
		URI loginUri = URI.create("http://localhost:" + this.port + "/login");
		HttpResponse<String> loginPage = client.send(HttpRequest.newBuilder(loginUri).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(loginPage.statusCode()).isEqualTo(200);
		Matcher csrf = CSRF_VALUE.matcher(loginPage.body());
		assertThat(csrf.find()).isTrue();

		String form = "username=george&password=george123&_csrf="
				+ URLEncoder.encode(csrf.group(1), StandardCharsets.UTF_8);
		HttpResponse<String> authenticated = client.send(HttpRequest.newBuilder(loginUri)
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString(form))
			.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(authenticated.statusCode()).isEqualTo(200);
		assertThat(authenticated.uri().getPath()).isEqualTo("/my/appointments");
		assertThat(authenticated.body()).contains("My Appointments", "george");

		HttpResponse<String> ownerPage = client.send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + "/my/appointments")).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(ownerPage.statusCode()).isEqualTo(200);
		assertThat(ownerPage.body()).contains("My Appointments");
	}

}
