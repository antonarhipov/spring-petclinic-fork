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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.samples.petclinic.support.StaffHttpSupport;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		properties = { "petclinic.account.bootstrap-enabled=true",
				"spring.flyway.locations=classpath:db/migration/${database},classpath:db/demo/${database}",
				"spring.docker.compose.enabled=false" })
@ActiveProfiles("postgres")
@Testcontainers(disabledWithoutDocker = true)
@DisabledInNativeImage
@DisabledInAotMode
public class PostgresIntegrationTests {

	@ServiceConnection
	@Container
	static PostgreSQLContainer container = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

	@LocalServerPort
	int port;

	@Autowired
	private VetRepository vets;

	public static void main(String[] args) {
		new SpringApplicationBuilder(PetClinicApplication.class) //
			.profiles("postgres") //
			.properties( //
					"spring.docker.compose.start.arguments=postgres" //
			) //
			.listeners(new PropertiesLogger()) //
			.run(args);
	}

	@Test
	void findAll() throws Exception {
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

	static class PropertiesLogger implements ApplicationListener<ApplicationPreparedEvent> {

		private static final Log log = LogFactory.getLog(PropertiesLogger.class);

		private ConfigurableEnvironment environment;

		private boolean isFirstRun = true;

		@Override
		public void onApplicationEvent(ApplicationPreparedEvent event) {
			if (isFirstRun) {
				environment = event.getApplicationContext().getEnvironment();
				printProperties();
			}
			isFirstRun = false;
		}

		public void printProperties() {
			for (EnumerablePropertySource<?> source : findPropertiesPropertySources()) {
				log.info("PropertySource: " + source.getName());
				String[] names = source.getPropertyNames();
				Arrays.sort(names);
				for (String name : names) {
					String resolved = environment.getProperty(name);

					assertNotNull(resolved, "resolved environment property: " + name + " is null.");

					Object sourceProperty = source.getProperty(name);

					assertNotNull(sourceProperty, "source property was expecting an object but is null.");

					assertNotNull(sourceProperty.toString(), "source property toString() returned null.");

					String value = sourceProperty.toString();
					if (resolved.equals(value)) {
						log.info(name + "=" + resolved);
					}
					else {
						log.info(name + "=" + value + " OVERRIDDEN to " + resolved);
					}
				}
			}
		}

		private List<EnumerablePropertySource<?>> findPropertiesPropertySources() {
			List<EnumerablePropertySource<?>> sources = new LinkedList<>();
			for (PropertySource<?> source : environment.getPropertySources()) {
				if (source instanceof EnumerablePropertySource enumerable) {
					sources.add(enumerable);
				}
			}
			return sources;
		}

	}

}
