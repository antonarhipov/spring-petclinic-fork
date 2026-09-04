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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.scheduling.TestClockConfig;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.samples.petclinic.scheduling.TestClockConfig.ZONE;

@SpringBootTest
@Import(TestClockConfig.class)
@Transactional
class PromptAndWindowTests {

	@Autowired
	private InterpretationNormalizer normalizer;

	@Autowired
	private InterpretationPromptFactory promptFactory;

	@Autowired
	private WindowMatcher windowMatcher;

	@Test
	@Tag("AC-44")
	void outsideBoundsClampAndMissingDefaults_AC44() {
		assertThat(normalizeDuration(14)).isEqualTo(15);
		assertThat(normalizeDuration(61)).isEqualTo(60);
		assertThat(normalizeDuration(null)).isEqualTo(30);
	}

	@Test
	@Tag("AC-45")
	void promptContainsClockHoursTokensVetsAndSpecialties_AC45() {
		String prompt = this.promptFactory.create("Dental pain", "Monday morning");

		assertThat(prompt).contains("currentDateTime=2026-09-07T09:00+02:00[Europe/Amsterdam]",
				"horizonEnd=2026-10-07T23:59:59.999999999+02:00[Europe/Amsterdam]", "MONDAY=09:00-17:00",
				"TUESDAY=09:00-17:00", "WEDNESDAY=09:00-18:00", "THURSDAY=09:00-17:00", "FRIDAY=10:00-16:00",
				"SATURDAY=CLOSED", "SUNDAY=CLOSED", "MORNING=09:00-12:00", "AFTERNOON=12:00-17:00",
				"EVENING=17:00-18:00", "1=James Carter specialties=[]", "2=Helen Leary specialties=[radiology]",
				"3=Linda Douglas specialties=[dentistry, surgery]", "4=Rafael Ortega specialties=[surgery]",
				"5=Henry Stevens specialties=[radiology]", "6=Sharon Jenkins specialties=[]",
				"offeredSpecialties=[radiology, surgery, dentistry]", "reason=Dental pain",
				"availability=Monday morning");
	}

	@Test
	@Tag("AC-46")
	void weekdayDateAndRangeWindowsRecur_AC46() {
		LocalDate start = LocalDate.of(2026, 9, 7);
		LocalDate end = LocalDate.of(2026, 9, 20);
		AvailabilityWindow weekday = AvailabilityWindow.preferredDayOfWeek(DayOfWeek.MONDAY, LocalTime.of(9, 0),
				LocalTime.of(10, 0), null);
		AvailabilityWindow date = AvailabilityWindow.preferred(LocalDate.of(2026, 9, 10), LocalTime.of(9, 0),
				LocalTime.of(10, 0), null);
		AvailabilityWindow range = AvailabilityWindow.allowed(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 14),
				LocalTime.of(9, 0), LocalTime.of(10, 0), null);

		assertThat(this.windowMatcher.matchingDates(weekday, start, end)).containsExactly(LocalDate.of(2026, 9, 7),
				LocalDate.of(2026, 9, 14));
		assertThat(this.windowMatcher.matchingDates(date, start, end)).containsExactly(LocalDate.of(2026, 9, 10));
		assertThat(this.windowMatcher.matchingDates(range, start, end)).containsExactly(LocalDate.of(2026, 9, 12),
				LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 14));
	}

	@Test
	@Tag("AC-47")
	void preferredOrAllowedDefinesHardUnion_AC47() {
		AvailabilityWindow preferred = AvailabilityWindow.preferred(LocalDate.of(2026, 9, 8), LocalTime.of(9, 0),
				LocalTime.of(10, 0), null);
		AvailabilityWindow allowed = AvailabilityWindow.allowed(LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 9),
				LocalTime.of(14, 0), LocalTime.of(16, 0), null);

		assertThat(this.windowMatcher.isAllowed(at(2026, 9, 8, 9, 30), List.of(preferred, allowed))).isTrue();
		assertThat(this.windowMatcher.isAllowed(at(2026, 9, 9, 15, 0), List.of(preferred, allowed))).isTrue();
		assertThat(this.windowMatcher.isAllowed(at(2026, 9, 10, 12, 0), List.of(preferred, allowed))).isFalse();
	}

	@Test
	@Tag("AC-48")
	void noPositiveWindowsAllowsWholeHorizon_AC48() {
		AvailabilityWindow exclusion = AvailabilityWindow.excluded(LocalDate.of(2026, 9, 12), LocalTime.of(9, 0),
				LocalTime.of(10, 0), null);

		assertThat(this.windowMatcher.isAllowed(at(2026, 9, 20, 13, 15), List.of())).isTrue();
		assertThat(this.windowMatcher.isAllowed(at(2026, 9, 20, 13, 15), List.of(exclusion))).isTrue();
		assertThat(this.windowMatcher.isAllowed(at(2026, 9, 12, 9, 30), List.of(exclusion))).isFalse();
	}

	private Integer normalizeDuration(Integer duration) {
		return this.normalizer
			.normalize(new InterpretationResult("summary", duration, CareType.GENERAL, null, null, false, List.of(),
					"{}", "model", "v1"))
			.estimatedMinutes();
	}

	private static ZonedDateTime at(int year, int month, int day, int hour, int minute) {
		return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZONE);
	}

}
