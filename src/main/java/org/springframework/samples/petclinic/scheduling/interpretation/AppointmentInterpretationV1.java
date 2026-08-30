package org.springframework.samples.petclinic.scheduling.interpretation;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AppointmentInterpretationV1(@NotBlank String schemaVersion, @NotBlank @Size(max = 500) String visitReason,
		@NotNull Integer durationMinutes, @NotBlank String careType, String requiredSpecialtyCode,
		@NotNull @Valid List<WindowV1> allowedWindows, @NotNull @Valid List<WindowV1> preferredWindows,
		@NotNull @Valid List<WindowV1> excludedWindows, String preferredVeterinarianCode,
		@NotBlank String veterinarianPreferenceStrength, @NotBlank String urgency,
		@NotNull @Valid List<UnresolvedDateV1> unresolvedDates, @NotNull @Valid List<UncertaintyV1> uncertainties) {

	public record WindowV1(@NotBlank @Size(max = 300) String sourcePhrase, String resolvedStart, String resolvedEnd,
			@NotBlank String resolution, @NotNull Boolean fallbackAllowed) {
	}

	public record UnresolvedDateV1(@NotBlank String sourcePhrase, @NotBlank String reason) {
	}

	public record UncertaintyV1(@NotBlank String fieldPath, @NotBlank String code) {
	}

}
