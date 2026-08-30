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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.samples.petclinic.support.StaffHttpSupport;
import org.springframework.samples.petclinic.vet.VetRepository;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		properties = { "logging.level.sql=DEBUG", "petclinic.account.bootstrap-enabled=true" })
public class PetClinicIntegrationTests {

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
		HttpClient client = StaffHttpSupport.loginAsAdmin(this.port);
		HttpResponse<String> result = client.send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + "/owners/1")).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(result.statusCode()).isEqualTo(200);
	}

	@Test
	void ownerList() throws Exception {
		HttpClient client = StaffHttpSupport.loginAsAdmin(this.port);
		HttpResponse<String> result = client.send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + "/owners?lastName=")).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(result.statusCode()).isEqualTo(200);
	}

	public static void main(String[] args) {
		SpringApplication.run(PetClinicApplication.class, "--spring.docker.compose.lifecycle-management=NONE");
	}

}
