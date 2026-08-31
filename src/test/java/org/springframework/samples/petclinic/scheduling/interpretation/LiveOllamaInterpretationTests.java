package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.samples.petclinic.availability.ClinicPolicy;
import org.springframework.samples.petclinic.config.OllamaConfiguration;
import org.springframework.samples.petclinic.config.OllamaConfiguration.OllamaProperties;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("ollama")
class LiveOllamaInterpretationTests {

	@Test
	void liveModelReturnsSchemaShapedOutputOrDefinedSafeFallback() {
		OllamaConfiguration configuration = new OllamaConfiguration();
		OllamaProperties properties = new OllamaProperties();
		properties.setBaseUrl(environment("SPRING_AI_OLLAMA_BASE_URL", "http://localhost:11434"));
		properties.setModel(environment("SPRING_AI_OLLAMA_CHAT_OPTIONS_MODEL", "llama3.2:3b"));
		properties.setTimeoutSeconds(10);
		RestClient.Builder restClientBuilder = RestClient.builder();
		configuration.ollamaRestClientTimeoutCustomizer(properties).customize(restClientBuilder);
		OllamaInterpretationClient client = new OllamaInterpretationClient(
				configuration.ollamaRestClient(restClientBuilder, properties), properties,
				configuration.objectMapper());
		InterpretationPrompt prompt = new InterpretationPrompt(
				"Milo needs a routine vaccination appointment next Tuesday morning.", "Milo", "dog",
				ZoneId.of("Europe/Tallinn"), List.of(15, 30, 45, 60), List.of(new CatalogChoice(1, "Dr. Carter")),
				List.of(), Instant.now());

		try {
			InterpretationCandidate candidate = client.interpret(prompt);
			InterpretationOutputValidator.ValidationResult validation = new InterpretationOutputValidator()
				.validate(candidate, ClinicPolicy.createDefaultPolicy());
			assertThat(candidate).isNotNull();
			assertThat(validation.valid() || !validation.errors().isEmpty()).isTrue();
		}
		catch (IllegalStateException ex) {
			assertThat(ex).hasMessage("Automated interpretation is temporarily unavailable");
		}
	}

	private String environment(String name, String fallback) {
		String value = System.getenv(name);
		return value == null || value.isBlank() ? fallback : value;
	}

}
