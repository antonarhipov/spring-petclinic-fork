package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.lang.Nullable;
import org.springframework.samples.petclinic.scheduling.request.CareType;

import io.swagger.v3.oas.annotations.media.Schema;

public record OllamaInterpretationResponse(boolean understood, @Nullable CareType careType, @Nullable String specialty,
		@Nullable String specialtyLabel, @Nullable Integer durationMinutes, @Nullable Integer preferredVetId,
		List<WindowValue> preferredWindows, List<WindowValue> allowedWindows, List<WindowValue> excludedWindows) {

	static final String DAY_PATTERN = "^(?:[0-9]{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12][0-9]|3[01])|MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY)$";

	static final String TIME_PATTERN = "^(?:[01][0-9]|2[0-3]):[0-5][0-9]$";

	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

	public OllamaInterpretationResponse {
		preferredWindows = List.copyOf(preferredWindows);
		allowedWindows = List.copyOf(allowedWindows);
		excludedWindows = List.copyOf(excludedWindows);
	}

	InterpretationResult.ModelOutput toModelOutput() {
		return new InterpretationResult.ModelOutput(this.understood, this.careType, this.specialty, this.specialtyLabel,
				this.durationMinutes, this.preferredVetId, convert(this.preferredWindows), convert(this.allowedWindows),
				convert(this.excludedWindows));
	}

	private static List<InterpretationResult.Window> convert(List<WindowValue> windows) {
		return windows.stream().map(WindowValue::toWindow).toList();
	}

	public record WindowValue(@Schema(
			description = "Absolute clinic-local date as YYYY-MM-DD, or an uppercase weekday only for recurring weekly availability",
			pattern = DAY_PATTERN) String day,
			@Schema(description = "Clinic-local start time in exact HH:mm format, without seconds or an offset",
					pattern = TIME_PATTERN) String start,
			@Schema(description = "Clinic-local end time in exact HH:mm format, without seconds or an offset",
					pattern = TIME_PATTERN) String end) {

		public WindowValue {
			if (day == null || !day.matches(DAY_PATTERN)) {
				throw new IllegalArgumentException("Window day must be YYYY-MM-DD or an uppercase weekday");
			}
			if (start == null || !start.matches(TIME_PATTERN) || end == null || !end.matches(TIME_PATTERN)) {
				throw new IllegalArgumentException("Window times must use exact clinic-local HH:mm format");
			}
			if (!parseTime(start).isBefore(parseTime(end))) {
				throw new IllegalArgumentException("A window requires an increasing clinic-local time range");
			}
		}

		InterpretationResult.Window toWindow() {
			if (Character.isDigit(this.day.charAt(0))) {
				try {
					return new InterpretationResult.Window(null, LocalDate.parse(this.day), parseTime(this.start),
							parseTime(this.end));
				}
				catch (DateTimeParseException exception) {
					throw new IllegalArgumentException("Window day must be a valid clinic-local date", exception);
				}
			}
			return new InterpretationResult.Window(DayOfWeek.valueOf(this.day), null, parseTime(this.start),
					parseTime(this.end));
		}

		private static LocalTime parseTime(String value) {
			return LocalTime.parse(value, TIME_FORMAT);
		}

	}

}
