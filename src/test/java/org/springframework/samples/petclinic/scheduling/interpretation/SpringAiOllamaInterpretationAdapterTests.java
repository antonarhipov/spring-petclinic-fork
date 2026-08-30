package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiOllamaInterpretationAdapterTests {

	@Test
	void usesNativeSchemaAndCapturesRawResponse() {
		StructuredChatGateway gateway = (prompt, schema, deadline) -> {
			assertThat(schema).contains("schemaVersion");
			return new InterpretationPort.InterpretationCallResult("{\"ok\":true}", "gemma4:latest", "gemma4:digest",
					false);
		};
		SpringAiOllamaInterpretationAdapter adapter = new SpringAiOllamaInterpretationAdapter(gateway);
		assertThat(adapter.maxFrameworkRetries()).isEqualTo(1);
		InterpretationPort.InterpretationCallResult result = adapter.interpret(request(Instant.now().plusSeconds(10)));
		assertThat(result.rawResponse()).contains("ok");
		assertThat(result.requestedModel()).isEqualTo("gemma4:latest");
		assertThat(result.resolvedModel()).isEqualTo("gemma4:digest");
	}

	@Test
	void coordinatorRetriesTransientOnceAndStopsOnUncertainty() {
		AtomicInteger calls = new AtomicInteger();
		InterpretationPort port = request -> {
			int n = calls.incrementAndGet();
			if (n == 1) {
				return new InterpretationPort.InterpretationCallResult(null, "gemma4:latest", null, true);
			}
			return new InterpretationPort.InterpretationCallResult(validUncertainJson(), "gemma4:latest", "digest",
					false);
		};
		InterpretationCoordinator coordinator = new InterpretationCoordinator(port, new InterpretationSchemaValidator(),
				java.time.Clock.systemUTC());
		InterpretationExecutionEvidence evidence = coordinator.interpret(request(Instant.now().plusSeconds(10)));
		assertThat(calls.get()).isEqualTo(2);
		assertThat(evidence.lastValidation().classification())
			.isEqualTo(InterpretationClassification.VALID_NEEDS_STAFF);
		evidence = coordinator.interpret(request(Instant.now().plusSeconds(10)));
		assertThat(calls.get()).isEqualTo(3);
	}

	private InterpretationPort.InterpretationCallRequest request(Instant deadline) {
		ClinicVocabulary vocabulary = new ClinicVocabulary(Set.of(30), Set.of("dentistry"), Set.of("carter"), Map.of(),
				Map.of(), "America/Chicago");
		return new InterpretationPort.InterpretationCallRequest("Need a checkup", Instant.now(), deadline, "prompt",
				"schema", vocabulary);
	}

	private String validUncertainJson() {
		return """
				{
				  "schemaVersion": "1.0",
				  "visitReason": "Maybe checkup",
				  "durationMinutes": 30,
				  "careType": "GENERAL",
				  "requiredSpecialtyCode": null,
				  "allowedWindows": [{
				    "sourcePhrase": "soon",
				    "resolvedStart": "2026-09-07T08:00:00-05:00",
				    "resolvedEnd": "2026-09-11T12:00:00-05:00",
				    "resolution": "RESOLVED",
				    "fallbackAllowed": false
				  }],
				  "preferredWindows": [],
				  "excludedWindows": [],
				  "preferredVeterinarianCode": null,
				  "veterinarianPreferenceStrength": "NONE",
				  "urgency": "UNRESOLVED",
				  "unresolvedDates": [],
				  "uncertainties": [{"fieldPath":"/urgency","code":"AMBIGUOUS_URGENCY"}]
				}
				""";
	}

}
