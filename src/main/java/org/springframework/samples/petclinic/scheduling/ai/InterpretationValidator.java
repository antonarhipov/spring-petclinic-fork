package org.springframework.samples.petclinic.scheduling.ai;

import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class InterpretationValidator {

	private static final Set<Integer> DURATIONS = Set.of(15, 30, 45, 60);

	public void validate(InterpretationResponse response) {
		if (response == null || blank(response.visitReason()) || blank(response.careType())
				|| response.durationMinutes() == null) {
			throw new IllegalArgumentException("Interpretation is missing required scheduling fields");
		}
		if (!DURATIONS.contains(response.durationMinutes())) {
			throw new IllegalArgumentException("Interpretation duration is not configured");
		}
		if ("UNCERTAIN".equalsIgnoreCase(response.urgency()) || "UNCERTAIN".equalsIgnoreCase(response.careType())) {
			throw new IllegalArgumentException("Safety-critical interpretation uncertainty requires staff review"
					+ " (careType=" + response.careType() + ", urgency=" + response.urgency() + ")");
		}
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}

}
