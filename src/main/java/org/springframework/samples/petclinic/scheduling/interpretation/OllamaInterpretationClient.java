package org.springframework.samples.petclinic.scheduling.interpretation;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.samples.petclinic.config.OllamaConfiguration.OllamaProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OllamaInterpretationClient implements InterpretationClient {

	private static final Logger log = LoggerFactory.getLogger(OllamaInterpretationClient.class);

	private final RestClient restClient;

	private final OllamaProperties properties;

	private final ObjectMapper objectMapper;

	public OllamaInterpretationClient(RestClient ollamaRestClient, OllamaProperties properties,
			ObjectMapper objectMapper) {
		this.restClient = ollamaRestClient;
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	@Override
	public InterpretationCandidate interpret(InterpretationPrompt prompt) {
		String systemPrompt = buildSystemPrompt(prompt);
		Map<String, Object> requestPayload = Map.of("model", this.properties.getModel(), "prompt", prompt.prose(),
				"system", systemPrompt, "format", "json", "stream", false);

		try {
			log.info("Sending interpretation request to Ollama (model: {})", this.properties.getModel());
			Map<?, ?> response = this.restClient.post()
				.uri("/api/generate")
				.body(requestPayload)
				.retrieve()
				.body(Map.class);

			if (response == null || !response.containsKey("response")) {
				throw new IllegalStateException("Empty or invalid response received from Ollama");
			}

			String jsonResponse = (String) response.get("response");
			log.debug("Received raw Ollama response: {}", jsonResponse);
			return this.objectMapper.readValue(jsonResponse, InterpretationCandidate.class);
		}
		catch (Exception ex) {
			log.warn("Ollama interpretation failed: {}", ex.getMessage());
			throw new RuntimeException("Failed to interpret request via Ollama: " + ex.getMessage(), ex);
		}
	}

	private String buildSystemPrompt(InterpretationPrompt prompt) {
		return """
				You are an expert veterinary scheduling assistant.
				Extract appointment intent from owner prose into strict JSON matching schema version 1.
				Rules:
				- Pet: %s (%s)
				- Reference Time (submittedAt): %s, Clinic Zone: %s
				- Allowed Durations: %s
				- Available Vets: %s
				- Available Specialties: %s
				- Output MUST be valid JSON adhering to the scheduling interpretation schema.
				""".formatted(prompt.petName(), prompt.petTypeName(), prompt.submittedAt(), prompt.clinicZone(),
				prompt.allowedDurations(), prompt.availableVets(), prompt.availableSpecialties());
	}

}
