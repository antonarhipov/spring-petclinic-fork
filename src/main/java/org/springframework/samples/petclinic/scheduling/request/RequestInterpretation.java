package org.springframework.samples.petclinic.scheduling.request;

/**
 * A persisted interpretation is represented by its current request revision. This view
 * preserves the explicit domain name used by the scheduling workflow.
 */
public record RequestInterpretation(String visitReason, int durationMinutes, String careType, String requiredSpecialty,
		String urgency) {
}
