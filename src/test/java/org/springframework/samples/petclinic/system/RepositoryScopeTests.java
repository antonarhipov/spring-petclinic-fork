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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryScopeTests {

	private static final String MAVEN_NAMESPACE = "http:" + "//maven.apache.org/POM/4.0.0";

	private static final List<Path> PERMANENT_DELETIONS = List.of(Path.of("build.gradle"), Path.of("settings.gradle"),
			Path.of("docker-compose.yml"), Path.of("k8s/db.yml"), Path.of("k8s/petclinic.yml"),
			Path.of("src/main/resources/application-mysql.properties"),
			Path.of("src/main/resources/application-postgres.properties"),
			Path.of("src/main/resources/db/mysql/data.sql"),
			Path.of("src/main/resources/db/mysql/petclinic_db_setup_mysql.txt"),
			Path.of("src/main/resources/db/mysql/schema.sql"), Path.of("src/main/resources/db/mysql/user.sql"),
			Path.of("src/main/resources/db/postgres/data.sql"),
			Path.of("src/main/resources/db/postgres/petclinic_db_setup_postgres.txt"),
			Path.of("src/main/resources/db/postgres/schema.sql"), Path.of("src/main/resources/db/h2/data.sql"),
			Path.of("src/main/resources/db/h2/schema.sql"),
			Path.of("src/test/java/org/springframework/samples/petclinic/MySqlIntegrationTests.java"),
			Path.of("src/test/java/org/springframework/samples/petclinic/MysqlTestApplication.java"),
			Path.of("src/test/java/org/springframework/samples/petclinic/PostgresIntegrationTests.java"));

	@Test
	@Tag("AC-129")
	void ac129_only_h2_maven_and_allowed_dependencies_remain() throws Exception {
		Set<String> dependencies = directMavenDependencies();
		List<String> repositoryPaths = repositoryPaths();
		String readme = Files.readString(Path.of("README.md"));

		assertThat(dependencies).contains("com.h2database:h2", "org.flywaydb:flyway-core",
				"org.springframework.boot:spring-boot-starter-security",
				"org.springframework.security:spring-security-test",
				"org.springframework.ai:spring-ai-starter-model-ollama");
		assertThat(dependencies).noneMatch(this::isForbiddenDependency);
		assertThat(repositoryPaths).noneMatch(this::isForbiddenRepositoryPath);
		assertThat(Files.readString(Path.of("src/main/resources/application.properties")))
			.contains("jdbc:h2:file:./data/petclinic", "spring.ai.ollama.chat.model",
					"spring.ai.ollama.chat.temperature=0", "spring.ai.retry.max-attempts=0")
			.doesNotContain("spring.sql.init");
		assertThat(Files.readString(Path.of("src/test/resources/application-test.properties")))
			.contains("jdbc:h2:mem:petclinic-${random.uuid}", "spring.flyway.enabled=true")
			.doesNotContain("spring.sql.init", "spring.ai.");
		assertThat(readme)
			.contains("Maven", "H2", "Ollama 0.13.1 or newer", "`ministral-3:14b`", "`localhost:11434`",
					"accepts English input only")
			.doesNotContain("Gradle", "MySQL", "PostgreSQL", "Docker Compose", "Kubernetes");
	}

	@Test
	@Tag("AC-129")
	void ac129_every_permanent_deletion_is_absent() {
		assertThat(PERMANENT_DELETIONS).allMatch(Files::notExists);
	}

	private Set<String> directMavenDependencies() throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http:" + "//apache.org/xml/features/disallow-doctype-decl", true);
		factory.setNamespaceAware(true);
		var document = factory.newDocumentBuilder().parse(Path.of("pom.xml").toFile());
		var dependencyNodes = document.getElementsByTagNameNS(MAVEN_NAMESPACE, "dependency");
		java.util.LinkedHashSet<String> coordinates = new java.util.LinkedHashSet<>();
		for (int index = 0; index < dependencyNodes.getLength(); index++) {
			Element dependency = (Element) dependencyNodes.item(index);
			if (dependency.getParentNode().getParentNode() == document.getDocumentElement()) {
				coordinates.add(childText(dependency, "groupId") + ":" + childText(dependency, "artifactId"));
			}
		}
		return coordinates;
	}

	private String childText(Element element, String name) {
		return element.getElementsByTagNameNS(MAVEN_NAMESPACE, name).item(0).getTextContent().trim();
	}

	private boolean isForbiddenDependency(String coordinate) {
		String normalized = coordinate.toLowerCase();
		return normalized.contains("mysql") || normalized.contains("postgres") || normalized.contains("testcontainers")
				|| normalized.contains("docker-compose") || normalized.contains("spring-batch")
				|| normalized.contains("spring-retry") || normalized.contains("resilience4j")
				|| normalized.contains("quartz");
	}

	private List<String> repositoryPaths() throws IOException {
		try (Stream<Path> paths = Files.walk(Path.of("."))) {
			return paths.map(path -> Path.of(".").relativize(path).toString().replace('\\', '/'))
				.filter(path -> !path.isEmpty())
				.filter(path -> !path.equals(".git") && !path.startsWith(".git/"))
				.filter(path -> !path.equals(".agents") && !path.startsWith(".agents/"))
				.filter(path -> !path.equals("target") && !path.startsWith("target/"))
				.filter(path -> !path.equals("data") && !path.startsWith("data/"))
				.toList();
		}
	}

	private boolean isForbiddenRepositoryPath(String path) {
		String normalized = path.toLowerCase();
		String fileName = Path.of(normalized).getFileName().toString();
		return normalized.contains("/db/mysql/") || normalized.contains("/db/postgres/") || fileName.contains("mysql")
				|| fileName.contains("postgres") || normalized.equals("k8s") || normalized.startsWith("k8s/")
				|| fileName.equals("docker-compose.yml") || fileName.equals("docker-compose.yaml")
				|| fileName.equals("compose.yml") || fileName.equals("compose.yaml") || fileName.endsWith(".gradle")
				|| fileName.endsWith(".gradle.kts") || fileName.equals("gradlew") || fileName.equals("gradlew.bat")
				|| fileName.contains("gradle") || normalized.equals("gradle") || normalized.startsWith("gradle/");
	}

}
