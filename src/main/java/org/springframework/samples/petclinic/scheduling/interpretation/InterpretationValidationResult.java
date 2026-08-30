package org.springframework.samples.petclinic.scheduling.interpretation;

import java.util.List;
import java.util.Map;

public record InterpretationValidationResult(InterpretationClassification classification,
		AppointmentInterpretationV1 recognized, Map<String, Object> unknownFields, List<String> issueCodes,
		String recognizedJson) {

	public boolean reviewable() {
		return this.classification == InterpretationClassification.VALID_REVIEWABLE;
	}

}
