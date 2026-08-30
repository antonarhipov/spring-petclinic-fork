package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.Instant;

public interface InterpretationPort {

	InterpretationCallResult interpret(InterpretationCallRequest request);

	record InterpretationCallRequest(String sourceText, Instant submittedAt, Instant deadline, String prompt,
			String outputSchema, ClinicVocabulary vocabulary) {
	}

	record InterpretationCallResult(String rawResponse, String requestedModel, String resolvedModel,
			boolean transientFailure) {
	}

}
