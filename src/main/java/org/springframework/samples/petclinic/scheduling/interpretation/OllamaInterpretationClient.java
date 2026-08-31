package org.springframework.samples.petclinic.scheduling.interpretation;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.samples.petclinic.config.OllamaConfiguration.OllamaProperties;
import org.springframework.samples.petclinic.config.SensitiveLoggingConfiguration;
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
			log.info(
					"Sending interpretation request to Ollama (model: {}):\n--- System Prompt ---\n{}\n--- User Prompt (Prose) ---\n{}",
					this.properties.getModel(), systemPrompt, prompt.prose());
			Map<?, ?> response = this.restClient.post()
				.uri("/api/generate")
				.body(requestPayload)
				.retrieve()
				.body(Map.class);

			if (response == null || !response.containsKey("response")) {
				throw new IllegalStateException("Empty or invalid response received from Ollama");
			}

			String jsonResponse = (String) response.get("response");
			log.info("Received Ollama response:\n{}", jsonResponse);
			return this.objectMapper.readValue(jsonResponse, InterpretationCandidate.class);
		}
		catch (Exception ex) {
			log.warn("Ollama interpretation failed; category={}", ex.getClass().getSimpleName(), ex);
			throw new IllegalStateException("Automated interpretation is temporarily unavailable", ex);
		}
	}

	private String buildSystemPrompt(InterpretationPrompt prompt) {
		return """
				You are an expert veterinary scheduling assistant.
				Extract appointment intent from owner prose into strict JSON matching schema version 1.
				Context:
				- Pet: %s (%s)
				- Reference Time (submittedAt): %s, Clinic Zone: %s
				- Allowed Durations in minutes: %s
				- Available Vets: %s
				- Available Specialties: %s

				Output MUST be a single JSON object adhering strictly to this JSON format:
				{
				  "schemaVersion": "1",
				  "visitReason": "<summarized visit reason>",
				  "urgency": "ROUTINE" | "PRIORITY" | "EMERGENCY_SUSPECTED",
				  "urgencySignals": [],
				  "durationMinutes": 30,
				  "preferredVeterinarian": {"id": 1, "name": "vet name"} or null,
				  "requiredSpecialty": {"id": 1, "name": "specialty name"} or null,
				  "availability": [
				    {
				      "classification": "PREFERRED" or "ACCEPTABLE",
				      "shape": "ONE_OFF" or "WEEKLY",
				      "date": "YYYY-MM-DD" (for ONE_OFF) or null,
				      "rangeStart": "YYYY-MM-DD" (for WEEKLY) or null,
				      "rangeEnd": "YYYY-MM-DD" (for WEEKLY) or null,
				      "weekdays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"] (for WEEKLY) or null,
				      "startTime": "HH:mm",
				      "endTime": "HH:mm",
				      "sourceText": "<exact text from owner prose for this window>",
				      "resolutionNote": null
				    }
				  ],
				  "contradictions": [],
				  "unresolved": []
				}
				Important rules:
				- schemaVersion MUST be "1".
				- durationMinutes MUST be one of the Allowed Durations (%s).
				- visitReason MUST not be empty.
				- urgency MUST be ROUTINE, PRIORITY, or EMERGENCY_SUSPECTED.
				- availability windows MUST have classification, shape, valid startTime, endTime, and non-empty sourceText.
				- For ONE_OFF shape, date (YYYY-MM-DD) is required. For WEEKLY shape, rangeStart, rangeEnd, and weekdays are required.
				- Output only the raw JSON object, no Markdown code blocks or wrapping commentary.
				"""
			.formatted(prompt.petName(), prompt.petTypeName(), prompt.submittedAt(), prompt.clinicZone(),
					prompt.allowedDurations(), prompt.availableVets(), prompt.availableSpecialties(),
					prompt.allowedDurations());
	}

}
