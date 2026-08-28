package org.springframework.samples.petclinic.scheduling.ai;

import java.util.List;

import org.jspecify.annotations.Nullable;

public record InterpretationResponse(String visitReason, Integer durationMinutes, String careType,
		@Nullable String requiredSpecialty, String urgency, @Nullable String preferredVeterinarian,
		@Nullable List<InterpretationAvailabilityWindow> preferredWindows) {

	public InterpretationResponse {
		requiredSpecialty = normalizeOptional(requiredSpecialty);
		preferredVeterinarian = normalizeOptional(preferredVeterinarian);
		preferredWindows = preferredWindows == null ? List.of() : List.copyOf(preferredWindows);
	}

	private static @Nullable String normalizeOptional(@Nullable String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim();
		return normalized.isEmpty() || "null".equalsIgnoreCase(normalized) ? null : normalized;
	}

}
