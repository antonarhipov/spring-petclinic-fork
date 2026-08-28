package org.springframework.samples.petclinic.scheduling.ai;

public record InterpretationResponse(String visitReason, Integer durationMinutes, String careType,
		String requiredSpecialty, String urgency, String preferredVeterinarian) {
}
