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

package org.springframework.samples.petclinic;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "logging.level.sql=DEBUG")
@ActiveProfiles("test")
public class PetClinicIntegrationTests {

	private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

	@LocalServerPort
	int port;

	@Autowired
	private VetRepository vets;

	@Test
	void findAll() {
		vets.findAll();
		vets.findAll(); // served from cache
	}

	@Test
	void ownerDetails() throws Exception {
		HttpResponse<String> result = staffClient().send(request("/owners/1").GET().build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(result.statusCode()).isEqualTo(200);
	}

	@Test
	void ownerList() throws Exception {
		HttpResponse<String> result = staffClient().send(request("/owners?lastName=").GET().build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(result.statusCode()).isEqualTo(200);
	}

	private HttpClient staffClient() throws Exception {
		CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
		HttpClient client = HttpClient.newBuilder()
			.cookieHandler(cookies)
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();
		HttpResponse<String> login = client.send(request("/login").GET().build(), HttpResponse.BodyHandlers.ofString());
		var matcher = CSRF.matcher(login.body());
		assertThat(matcher.find()).isTrue();
		String form = "username=staff&password=staff123&_csrf="
				+ URLEncoder.encode(matcher.group(1), StandardCharsets.UTF_8);
		HttpRequest submit = request("/login").header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString(form))
			.build();
		assertThat(client.send(submit, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(302);
		return client;
	}

	private HttpRequest.Builder request(String path) {
		return HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + path));
	}

	public static void main(String[] args) {
		SpringApplication.run(PetClinicApplication.class, "--spring.docker.compose.lifecycle-management=NONE");
	}

}
