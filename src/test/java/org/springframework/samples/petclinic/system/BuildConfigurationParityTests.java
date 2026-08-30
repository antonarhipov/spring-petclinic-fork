package org.springframework.samples.petclinic.system;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuildConfigurationParityTests {

	@Test
	void mavenAndGradleShareJava21SpringAiAndTimefoldCoordinates() throws Exception {
		String pom = Files.readString(Path.of("pom.xml"));
		String gradle = Files.readString(Path.of("build.gradle"));

		assertThat(pom).contains("<java.version>21</java.version>");
		assertThat(pom).contains("<spring-ai.version>2.0.1</spring-ai.version>");
		assertThat(pom).contains("<timefold.version>2.5.0</timefold.version>");
		assertThat(pom).contains("spring-ai-starter-model-ollama");
		assertThat(pom).contains("timefold-solver-spring-boot-starter");
		assertThat(pom).contains("spring-boot-starter-parent");
		assertThat(pom).contains("<version>4.1.0</version>");

		assertThat(gradle).contains("JavaLanguageVersion.of(21)");
		assertThat(gradle).contains("springAiVersion = \"2.0.1\"");
		assertThat(gradle).contains("timefoldVersion = \"2.5.0\"");
		assertThat(gradle).contains("spring-ai-starter-model-ollama");
		assertThat(gradle).contains("timefold-solver-spring-boot-starter");
		assertThat(gradle).contains("id 'org.springframework.boot' version '4.1.0'");
	}

}
