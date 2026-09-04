/* Copyright 2012-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package org.springframework.samples.petclinic.scheduling.solver;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.scheduling.TestClockConfig;
import org.springframework.samples.petclinic.scheduling.solver.SlotBoundaryService.TimeRange;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.samples.petclinic.scheduling.TestClockConfig.PINNED_DATE_TIME;

@SpringBootTest
@Import(TestClockConfig.class)
@Transactional
class SlotBoundaryTests {

	@Autowired
	private SlotBoundaryService boundaries;

	@Test
	@Tag("AC-49")
	void tokensResolvePerWeekdayAndEmptyDrops_AC49() {
		Map<DayOfWeek, Map<String, Optional<TimeRange>>> expected = Map.of(DayOfWeek.MONDAY,
				Map.of("MORNING", range(9, 12), "AFTERNOON", range(12, 17), "EVENING", Optional.empty()),
				DayOfWeek.TUESDAY,
				Map.of("MORNING", range(9, 12), "AFTERNOON", range(12, 17), "EVENING", Optional.empty()),
				DayOfWeek.WEDNESDAY,
				Map.of("MORNING", range(9, 12), "AFTERNOON", range(12, 17), "EVENING", range(17, 18)),
				DayOfWeek.THURSDAY,
				Map.of("MORNING", range(9, 12), "AFTERNOON", range(12, 17), "EVENING", Optional.empty()),
				DayOfWeek.FRIDAY,
				Map.of("MORNING", range(10, 12), "AFTERNOON", range(12, 16), "EVENING", Optional.empty()),
				DayOfWeek.SATURDAY,
				Map.of("MORNING", Optional.empty(), "AFTERNOON", Optional.empty(), "EVENING", Optional.empty()),
				DayOfWeek.SUNDAY,
				Map.of("MORNING", Optional.empty(), "AFTERNOON", Optional.empty(), "EVENING", Optional.empty()));

		for (DayOfWeek weekday : DayOfWeek.values()) {
			for (String token : new String[] { "MORNING", "AFTERNOON", "EVENING" }) {
				assertThat(this.boundaries.resolvePartOfDay(token, weekday)).as("%s %s", weekday, token)
					.isEqualTo(expected.get(weekday).get(token));
			}
		}
		assertThat(this.boundaries.resolvePartOfDay("EVENING", DayOfWeek.MONDAY)).isEmpty();
	}

	@Test
	@Tag("AC-50")
	void afterLeadBoundaryAllowed_AC50() {
		assertThat(this.boundaries.isBookableByLeadTime(PINNED_DATE_TIME.plusHours(2).plusMinutes(15))).isTrue();
	}

	@Test
	@Tag("AC-51")
	void exactFirstGridPointAllowed_AC51() {
		ZonedDateTime exactBoundary = PINNED_DATE_TIME.plusHours(2);
		assertThat(this.boundaries.earliestBookableSlot()).isEqualTo(exactBoundary);
		assertThat(this.boundaries.isBookableByLeadTime(exactBoundary)).isTrue();
	}

	@Test
	@Tag("AC-52")
	void beforeLeadBoundaryExcluded_AC52() {
		assertThat(this.boundaries.isBookableByLeadTime(PINNED_DATE_TIME.plusHours(2).minusMinutes(15))).isFalse();
	}

	private static Optional<TimeRange> range(int startHour, int endHour) {
		return Optional.of(new TimeRange(LocalTime.of(startHour, 0), LocalTime.of(endHour, 0)));
	}

}
