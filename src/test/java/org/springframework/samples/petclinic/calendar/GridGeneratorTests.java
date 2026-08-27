/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.calendar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.samples.petclinic.vet.Vet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GridGeneratorTests {

	private static final ZoneId ZONE = ZoneId.of("Europe/Amsterdam");

	@Mock
	private EffectiveAvailabilityResolver availabilityResolver;

	@Mock
	private ClinicSettingsRepository clinicSettingsRepository;

	private GridGenerator gridGenerator;

	private ClinicSettings defaultSettings;

	@BeforeEach
	void setUp() {
		this.defaultSettings = new ClinicSettings();
		this.gridGenerator = new GridGenerator(this.availabilityResolver, this.clinicSettingsRepository);
	}

	@Test
	void candidateStartsAlignToGranularityAndEnforceContinuousFit() {
		LocalDate date = LocalDate.of(2026, 6, 1);
		Instant start = ZonedDateTime.of(date, LocalTime.of(9, 0), ZONE).toInstant();
		Instant end = ZonedDateTime.of(date, LocalTime.of(12, 0), ZONE).toInstant();
		InstantInterval interval = new InstantInterval(start, end);

		// 3-hour interval (09:00 - 12:00), 15-min granularity, 30-min visit duration
		List<Instant> starts = GridGenerator.generateCandidateStarts(List.of(interval), 30, 15);

		// Starts from 09:00 to 11:30 (11 starts: 09:00, 09:15, 09:30, 09:45, 10:00,
		// 10:15, 10:30, 10:45, 11:00, 11:15, 11:30)
		assertThat(starts).hasSize(11);
		assertThat(starts.get(0)).isEqualTo(start);

		Instant lastStart = starts.get(starts.size() - 1);
		Instant expectedLastStart = ZonedDateTime.of(date, LocalTime.of(11, 30), ZONE).toInstant();
		assertThat(lastStart).isEqualTo(expectedLastStart);

		// Verify 11:45 is NOT included (11:45 + 30m = 12:15 > 12:00)
		Instant excludedStart = ZonedDateTime.of(date, LocalTime.of(11, 45), ZONE).toInstant();
		assertThat(starts).doesNotContain(excludedStart);
	}

	@Test
	void continuousFitRuleExcludesStartsStraddlingBreakInSplitShift() {
		LocalDate date = LocalDate.of(2026, 6, 1);
		Instant mStart = ZonedDateTime.of(date, LocalTime.of(9, 0), ZONE).toInstant();
		Instant mEnd = ZonedDateTime.of(date, LocalTime.of(12, 0), ZONE).toInstant();
		Instant aStart = ZonedDateTime.of(date, LocalTime.of(13, 0), ZONE).toInstant();
		Instant aEnd = ZonedDateTime.of(date, LocalTime.of(17, 0), ZONE).toInstant();

		InstantInterval morning = new InstantInterval(mStart, mEnd);
		InstantInterval afternoon = new InstantInterval(aStart, aEnd);

		// 45-min duration, 15-min granularity
		List<Instant> starts = GridGenerator.generateCandidateStarts(List.of(morning, afternoon), 45, 15);

		// Morning starts: 09:00, 09:15, 09:30, 09:45, 10:00, 10:15, 10:30, 10:45, 11:00,
		// 11:15 (10 starts)
		// Afternoon starts: 13:00, 13:15, 13:30, 13:45, 14:00, 14:15, 14:30, 14:45,
		// 15:00, 15:15, 15:30, 15:45, 16:00, 16:15 (14 starts)
		assertThat(starts).hasSize(24);

		// Starts that would cross the 12:00-13:00 break are excluded:
		Instant straddle1 = ZonedDateTime.of(date, LocalTime.of(11, 30), ZONE).toInstant(); // 11:30
																							// +
																							// 45m
																							// =
																							// 12:15
																							// >
																							// 12:00
		Instant straddle2 = ZonedDateTime.of(date, LocalTime.of(11, 45), ZONE).toInstant(); // 11:45
																							// +
																							// 45m
																							// =
																							// 12:30
																							// >
																							// 12:00
		Instant breakStart = ZonedDateTime.of(date, LocalTime.of(12, 0), ZONE).toInstant();
		Instant breakMid = ZonedDateTime.of(date, LocalTime.of(12, 30), ZONE).toInstant();

		assertThat(starts).doesNotContain(straddle1, straddle2, breakStart, breakMid);
	}

	@Test
	void durationExceedingContinuousBlockYieldsNoStarts() {
		LocalDate date = LocalDate.of(2026, 6, 1);
		Instant start = ZonedDateTime.of(date, LocalTime.of(9, 0), ZONE).toInstant();
		Instant end = ZonedDateTime.of(date, LocalTime.of(10, 0), ZONE).toInstant(); // 60
																						// min
																						// block
		InstantInterval interval = new InstantInterval(start, end);

		// Requested duration = 90 min (exceeds 60 min block)
		List<Instant> starts = GridGenerator.generateCandidateStarts(List.of(interval), 90, 15);

		assertThat(starts).isEmpty();
	}

	@Test
	void candidateSlotsEnforceHorizonBounds() {
		LocalDate fromDate = LocalDate.of(2026, 6, 1);
		Vet vet = new Vet();
		vet.setId(1);

		ClinicSettings settings = new ClinicSettings();
		settings.setBookingHorizonDays(5); // 5-day horizon for test
		settings.setDefaultVisitMin(30);
		settings.setGridGranularityMin(15);

		Instant dailyStart = ZonedDateTime.of(fromDate, LocalTime.of(9, 0), ZONE).toInstant();
		Instant dailyEnd = ZonedDateTime.of(fromDate, LocalTime.of(10, 0), ZONE).toInstant();
		InstantInterval interval = new InstantInterval(dailyStart, dailyEnd);

		given(this.availabilityResolver.resolve(eq(1), any(LocalDate.class), eq(settings)))
			.willReturn(List.of(interval));

		List<SlotCandidate> slots = this.gridGenerator.generateCandidateSlots(1, fromDate, 30, settings);

		// For each of the 5 days, a 1-hour block with 30-min duration has 3 starts
		// (09:00, 09:15, 09:30)
		// 5 days * 3 slots = 15 candidate slots
		assertThat(slots).hasSize(15);
	}

}
