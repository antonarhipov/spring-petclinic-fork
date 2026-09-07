package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.lang.Nullable;
import org.springframework.samples.petclinic.scheduling.request.CareType;

public record InterpretationResult(ModelOutput output, String rawJson) {

	public InterpretationResult {
		if (output == null || rawJson == null) {
			throw new IllegalArgumentException("Interpretation output and raw JSON are required");
		}
	}

	public record ModelOutput(boolean understood, @Nullable CareType careType, @Nullable String specialty,
			@Nullable String specialtyLabel, @Nullable Integer durationMinutes, @Nullable Integer preferredVetId,
			List<Window> preferredWindows, List<Window> allowedWindows, List<Window> excludedWindows) {

		public ModelOutput {
			preferredWindows = List.copyOf(preferredWindows);
			allowedWindows = List.copyOf(allowedWindows);
			excludedWindows = List.copyOf(excludedWindows);
		}

	}

	public record Window(@Nullable DayOfWeek weekday, @Nullable LocalDate date, LocalTime start, LocalTime end) {
	}

}
