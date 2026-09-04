/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 */

package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/** Applies the hard preferred-or-allowed window union and exclusions. */
@Component
public class WindowMatcher {

	public boolean isAllowed(ZonedDateTime candidate, List<AvailabilityWindow> windows) {
		List<AvailabilityWindow> positive = windows.stream()
			.filter(window -> window.kind() != WindowKind.EXCLUDED)
			.toList();
		boolean insidePositiveUniverse = positive.isEmpty()
				|| positive.stream().anyMatch(window -> matches(window, candidate));
		boolean excluded = windows.stream()
			.filter(window -> window.kind() == WindowKind.EXCLUDED)
			.anyMatch(window -> matches(window, candidate));
		return insidePositiveUniverse && !excluded;
	}

	public List<LocalDate> matchingDates(AvailabilityWindow window, LocalDate horizonStart, LocalDate horizonEnd) {
		List<LocalDate> dates = new ArrayList<>();
		for (LocalDate date = horizonStart; !date.isAfter(horizonEnd); date = date.plusDays(1)) {
			if (matchesDate(window, date)) {
				dates.add(date);
			}
		}
		return dates;
	}

	private static boolean matches(AvailabilityWindow window, ZonedDateTime candidate) {
		if (!matchesDate(window, candidate.toLocalDate())) {
			return false;
		}
		boolean afterStart = window.startTime() == null || !candidate.toLocalTime().isBefore(window.startTime());
		boolean beforeEnd = window.endTime() == null || candidate.toLocalTime().isBefore(window.endTime());
		return afterStart && beforeEnd;
	}

	private static boolean matchesDate(AvailabilityWindow window, LocalDate date) {
		if (window.dateVal() != null) {
			return window.dateVal().equals(date);
		}
		if (window.startDate() != null || window.endDate() != null) {
			boolean afterStart = window.startDate() == null || !date.isBefore(window.startDate());
			boolean beforeEnd = window.endDate() == null || !date.isAfter(window.endDate());
			return afterStart && beforeEnd;
		}
		return window.dayOfWeek() == null || window.dayOfWeek() == date.getDayOfWeek();
	}

}
