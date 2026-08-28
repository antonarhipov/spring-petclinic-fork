package org.springframework.samples.petclinic.scheduling.ai;

import org.jspecify.annotations.Nullable;

public record InterpretationResponse(String visitReason, Integer durationMinutes, String careType,
		@Nullable String requiredSpecialty, String urgency, @Nullable String preferredVeterinarian) {

	public InterpretationResponse {
		requiredSpecialty = normalizeOptional(requiredSpecialty);
		preferredVeterinarian = normalizeOptional(preferredVeterinarian);
	}

	private static @Nullable String normalizeOptional(@Nullable String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim();
		return normalized.isEmpty() || "null".equalsIgnoreCase(normalized) ? null : normalized;
	}

}
