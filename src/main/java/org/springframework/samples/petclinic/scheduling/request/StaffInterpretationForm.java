package org.springframework.samples.petclinic.scheduling.request;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class StaffInterpretationForm {

	private String careType;

	private String specialty;

	private String specialtyLabel;

	private String durationMinutes;

	private String preferredVetId;

	private String preferredWindows;

	private String allowedWindows;

	private String excludedWindows;

	public static StaffInterpretationForm empty() {
		return new StaffInterpretationForm();
	}

	public static StaffInterpretationForm from(StaffQueueQueryService.InterpretationView interpretation) {
		StaffInterpretationForm form = empty();
		if (interpretation == null) {
			return form;
		}
		form.careType = interpretation.careType() != null ? interpretation.careType().name() : null;
		form.specialty = interpretation.specialty();
		form.specialtyLabel = interpretation.specialtyLabel();
		form.durationMinutes = interpretation.durationMinutes() != null ? interpretation.durationMinutes().toString()
				: null;
		form.preferredVetId = interpretation.preferredVetId() != null ? interpretation.preferredVetId().toString()
				: null;
		form.preferredWindows = format(interpretation.preferredWindows());
		form.allowedWindows = format(interpretation.allowedWindows());
		form.excludedWindows = format(interpretation.excludedWindows());
		return form;
	}

	public ParseResult parse() {
		List<String> errors = new ArrayList<>();
		CareType parsedCareType = null;
		try {
			parsedCareType = this.careType == null || this.careType.isBlank() ? null
					: CareType.valueOf(this.careType.strip());
		}
		catch (IllegalArgumentException ex) {
			errors.add("scheduling.staff.interpretation.care.invalid");
		}
		if (parsedCareType == null) {
			errors.add("scheduling.staff.interpretation.care.required");
		}

		String parsedSpecialty = normalized(this.specialty);
		String parsedSpecialtyLabel = normalized(this.specialtyLabel);
		if (parsedCareType == CareType.SPECIALTY && parsedSpecialty == null) {
			errors.add("scheduling.staff.interpretation.specialty.required");
		}
		if (parsedCareType == CareType.GENERAL && parsedSpecialty != null) {
			errors.add("scheduling.staff.interpretation.specialty.unexpected");
		}
		if ("OTHER".equals(parsedSpecialty) && parsedSpecialtyLabel == null) {
			errors.add("scheduling.staff.interpretation.specialtyLabel.required");
		}

		Integer parsedDuration = parsePositiveInteger(this.durationMinutes,
				"scheduling.staff.interpretation.duration.invalid", errors);
		Integer parsedVetId = parsePositiveInteger(this.preferredVetId, "scheduling.staff.interpretation.vet.invalid",
				errors);
		List<WindowValue> preferred = parseWindows(this.preferredWindows, "PREFERRED", errors);
		List<WindowValue> allowed = parseWindows(this.allowedWindows, "ALLOWED", errors);
		List<WindowValue> excluded = parseWindows(this.excludedWindows, "EXCLUDED", errors);
		if (preferred.isEmpty() && allowed.isEmpty()) {
			errors.add("scheduling.staff.interpretation.windows.required");
		}

		if (!errors.isEmpty()) {
			return new ParseResult(null, List.copyOf(errors));
		}
		return new ParseResult(new Values(parsedCareType, parsedSpecialty, parsedSpecialtyLabel, parsedDuration,
				parsedVetId, preferred, allowed, excluded), List.of());
	}

	private static Integer parsePositiveInteger(String value, String messageKey, List<String> errors) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			int parsed = Integer.parseInt(value.strip());
			if (parsed > 0) {
				return parsed;
			}
		}
		catch (NumberFormatException ex) {
			// Report the localized validation message below.
		}
		errors.add(messageKey);
		return null;
	}

	private static List<WindowValue> parseWindows(String source, String kind, List<String> errors) {
		List<WindowValue> windows = new ArrayList<>();
		if (source == null || source.isBlank()) {
			return windows;
		}
		for (String line : source.split("\\R")) {
			if (line.isBlank()) {
				continue;
			}
			String[] parts = line.strip().split("[\\s,]+", -1);
			if (parts.length != 3) {
				errors.add("scheduling.staff.interpretation.windows.invalid");
				continue;
			}
			try {
				DayOfWeek weekday = null;
				LocalDate date = null;
				try {
					weekday = DayOfWeek.valueOf(parts[0].toUpperCase(java.util.Locale.ROOT));
				}
				catch (IllegalArgumentException ex) {
					date = LocalDate.parse(parts[0]);
				}
				LocalTime start = LocalTime.parse(parts[1]);
				LocalTime end = LocalTime.parse(parts[2]);
				if (!end.isAfter(start)) {
					throw new IllegalArgumentException("Window end must be after start");
				}
				windows.add(new WindowValue(kind, weekday, date, start, end));
			}
			catch (IllegalArgumentException ex) {
				errors.add("scheduling.staff.interpretation.windows.invalid");
			}
		}
		return windows;
	}

	private static String format(List<StaffQueueQueryService.WindowView> windows) {
		return windows.stream()
			.map(window -> (window.weekday() != null ? window.weekday().name() : window.date().toString()) + " "
					+ window.startTime() + " " + window.endTime())
			.reduce((left, right) -> left + System.lineSeparator() + right)
			.orElse("");
	}

	private static String normalized(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	public String getCareType() {
		return this.careType;
	}

	public void setCareType(String careType) {
		this.careType = careType;
	}

	public String getSpecialty() {
		return this.specialty;
	}

	public void setSpecialty(String specialty) {
		this.specialty = specialty;
	}

	public String getSpecialtyLabel() {
		return this.specialtyLabel;
	}

	public void setSpecialtyLabel(String specialtyLabel) {
		this.specialtyLabel = specialtyLabel;
	}

	public String getDurationMinutes() {
		return this.durationMinutes;
	}

	public void setDurationMinutes(String durationMinutes) {
		this.durationMinutes = durationMinutes;
	}

	public String getPreferredVetId() {
		return this.preferredVetId;
	}

	public void setPreferredVetId(String preferredVetId) {
		this.preferredVetId = preferredVetId;
	}

	public String getPreferredWindows() {
		return this.preferredWindows;
	}

	public void setPreferredWindows(String preferredWindows) {
		this.preferredWindows = preferredWindows;
	}

	public String getAllowedWindows() {
		return this.allowedWindows;
	}

	public void setAllowedWindows(String allowedWindows) {
		this.allowedWindows = allowedWindows;
	}

	public String getExcludedWindows() {
		return this.excludedWindows;
	}

	public void setExcludedWindows(String excludedWindows) {
		this.excludedWindows = excludedWindows;
	}

	public record ParseResult(Values values, List<String> errors) {
	}

	public record Values(CareType careType, String specialty, String specialtyLabel, Integer durationMinutes,
			Integer preferredVetId, List<WindowValue> preferredWindows, List<WindowValue> allowedWindows,
			List<WindowValue> excludedWindows) {
	}

	public record WindowValue(String kind, DayOfWeek weekday, LocalDate date, LocalTime startTime, LocalTime endTime) {
	}

}
