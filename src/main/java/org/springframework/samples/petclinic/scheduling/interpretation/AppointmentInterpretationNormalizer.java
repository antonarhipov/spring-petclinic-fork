/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

import org.springframework.samples.petclinic.calendar.ClinicSettings;
import org.springframework.stereotype.Component;

@Component
public class AppointmentInterpretationNormalizer {

	public AppointmentInterpretation normalize(AppointmentInterpretation interpretation, ClinicSettings settings) {
		if (interpretation == null || isBlank(interpretation.careType()) || interpretation.urgency() == null) {
			throw new IllegalArgumentException("Care type and urgency are required");
		}
		if (interpretation.preferredVetId() != null && interpretation.preferredVetId() <= 0) {
			throw new IllegalArgumentException("Preferred veterinarian id must be positive");
		}

		return new AppointmentInterpretation(interpretation.careType().trim(),
				blankToNull(interpretation.requiredSpecialty()),
				settings.clampDuration(interpretation.estimatedDurationMin()),
				normalizeWindows(interpretation.preferred(), settings),
				normalizeWindows(interpretation.allowed(), settings),
				normalizeWindows(interpretation.excluded(), settings), interpretation.preferredVetId(),
				interpretation.urgency());
	}

	private List<InterpretationWindow> normalizeWindows(List<InterpretationWindow> windows, ClinicSettings settings) {
		if (windows == null) {
			return List.of();
		}
		return windows.stream().map(window -> normalizeWindow(window, settings)).distinct().toList();
	}

	private InterpretationWindow normalizeWindow(InterpretationWindow window, ClinicSettings settings) {
		if (window == null || window.dayOfWeek() == null) {
			throw new IllegalArgumentException("Every scheduling window needs a day of week");
		}

		String dayPart = blankToNull(window.dayPart());
		LocalTime start;
		LocalTime end;
		if (dayPart != null) {
			dayPart = dayPart.toLowerCase(Locale.ROOT);
			LocalTime[] range = dayPartRange(dayPart, settings);
			start = range[0];
			end = range[1];
		}
		else {
			start = window.start();
			end = window.end();
			if (start == null || end == null || !start.isBefore(end)) {
				throw new IllegalArgumentException("An explicit scheduling window must have start before end");
			}
		}

		return new InterpretationWindow(window.dayOfWeek(), dayPart, start, end, settings.getZone().getId());
	}

	private LocalTime[] dayPartRange(String dayPart, ClinicSettings settings) {
		return switch (dayPart) {
			case "morning" -> new LocalTime[] { settings.getMorningStart(), settings.getMorningEnd() };
			case "afternoon" -> new LocalTime[] { settings.getAfternoonStart(), settings.getAfternoonEnd() };
			case "evening" -> new LocalTime[] { settings.getEveningStart(), settings.getEveningEnd() };
			default -> throw new IllegalArgumentException("Unknown clinic day part: " + dayPart);
		};
	}

	private static String blankToNull(String value) {
		return isBlank(value) ? null : value.trim();
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

}
