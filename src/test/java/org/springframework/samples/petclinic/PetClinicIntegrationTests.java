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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "logging.level.sql=DEBUG")
public class PetClinicIntegrationTests {

	@LocalServerPort
	int port;

	@Autowired
	private VetRepository vets;

	@Autowired
	private RestTemplateBuilder builder;

	@Test
	void findAll() {
		vets.findAll();
		vets.findAll(); // served from cache
	}

	@Test
	void ownerDetailsRequiresAuthentication() {
		RestTemplate template = nonRedirectingTemplate();
		ResponseEntity<String> result = template.exchange(RequestEntity.get("/owners/1").build(), String.class);
		// Staff-only route: unauthenticated callers are redirected to the login form.
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FOUND);
		assertThat(result.getHeaders().getLocation()).isNotNull();
		assertThat(result.getHeaders().getLocation().toString()).contains("/login");
	}

	@Test
	void ownerListRequiresAuthentication() {
		RestTemplate template = nonRedirectingTemplate();
		ResponseEntity<String> result = template.exchange(RequestEntity.get("/owners?lastName=").build(), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FOUND);
		assertThat(result.getHeaders().getLocation()).isNotNull();
		assertThat(result.getHeaders().getLocation().toString()).contains("/login");
	}

	@Test
	void publicHomeIsAccessible() {
		RestTemplate template = builder.baseUri("http://localhost:" + port).build();
		ResponseEntity<String> result = template.exchange(RequestEntity.get("/").build(), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	private RestTemplate nonRedirectingTemplate() {
		java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
			.followRedirects(java.net.http.HttpClient.Redirect.NEVER)
			.build();
		org.springframework.http.client.JdkClientHttpRequestFactory requestFactory = new org.springframework.http.client.JdkClientHttpRequestFactory(
				httpClient);
		RestTemplate template = new RestTemplate(requestFactory);
		template.setUriTemplateHandler(builder.baseUri("http://localhost:" + port).build().getUriTemplateHandler());
		return template;
	}

	public static void main(String[] args) {
		SpringApplication.run(PetClinicApplication.class, "--spring.docker.compose.lifecycle-management=NONE");
	}

}
