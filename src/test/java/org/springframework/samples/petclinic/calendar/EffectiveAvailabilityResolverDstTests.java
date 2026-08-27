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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.vet.Vet;

import static org.assertj.core.api.Assertions.assertThat;

class EffectiveAvailabilityResolverDstTests {

	private static final ZoneId ZONE = ZoneId.of("Europe/Amsterdam");

	private Vet vet;

	@BeforeEach
	void setUp() {
		this.vet = new Vet();
		this.vet.setId(1);
		this.vet.setFirstName("James");
		this.vet.setLastName("Carter");
	}

	@Test
	void springForwardDaySpanningTransitionProducesContinuousThreeHourInterval() {
		// Europe/Amsterdam DST spring-forward: 2026-03-29 (clocks jump 02:00 -> 03:00)
		LocalDate springForwardDate = LocalDate.of(2026, 3, 29); // Sunday (day 7)
		VetWeeklyShift nightShift = new VetWeeklyShift(this.vet, 7, LocalTime.of(1, 0), LocalTime.of(5, 0));

		List<InstantInterval> intervals = EffectiveAvailabilityResolver.resolve(springForwardDate, ZONE, List.of(),
				List.of(), List.of(nightShift), List.of());

		assertThat(intervals).hasSize(1);
		InstantInterval interval = intervals.get(0);

		// 01:00 CET (UTC+1) = 00:00 UTC
		// 05:00 CEST (UTC+2) = 03:00 UTC
		// Real elapsed instant duration = 3 hours (not 4 hours)
		assertThat(interval.start()).isEqualTo(Instant.parse("2026-03-29T00:00:00Z"));
		assertThat(interval.end()).isEqualTo(Instant.parse("2026-03-29T03:00:00Z"));
		assertThat(interval.duration()).isEqualTo(Duration.ofHours(3));
	}

	@Test
	void springForwardDayNormalWorkingHoursProducesContinuousNineHourInterval() {
		LocalDate springForwardDate = LocalDate.of(2026, 3, 29);
		VetWeeklyShift dayShift = new VetWeeklyShift(this.vet, 7, LocalTime.of(8, 0), LocalTime.of(17, 0));

		List<InstantInterval> intervals = EffectiveAvailabilityResolver.resolve(springForwardDate, ZONE, List.of(),
				List.of(), List.of(dayShift), List.of());

		assertThat(intervals).hasSize(1);
		InstantInterval interval = intervals.get(0);

		// 08:00 CEST (UTC+2) = 06:00 UTC
		// 17:00 CEST (UTC+2) = 15:00 UTC
		// Real elapsed duration = 9 hours
		assertThat(interval.start()).isEqualTo(Instant.parse("2026-03-29T06:00:00Z"));
		assertThat(interval.end()).isEqualTo(Instant.parse("2026-03-29T15:00:00Z"));
		assertThat(interval.duration()).isEqualTo(Duration.ofHours(9));
	}

	@Test
	void fallBackDaySpanningTransitionProducesContinuousFiveHourInterval() {
		// Europe/Amsterdam DST fall-back: 2026-10-25 (clocks jump 03:00 -> 02:00, 1h
		// overlap)
		LocalDate fallBackDate = LocalDate.of(2026, 10, 25); // Sunday (day 7)
		VetWeeklyShift nightShift = new VetWeeklyShift(this.vet, 7, LocalTime.of(1, 0), LocalTime.of(5, 0));

		List<InstantInterval> intervals = EffectiveAvailabilityResolver.resolve(fallBackDate, ZONE, List.of(),
				List.of(), List.of(nightShift), List.of());

		assertThat(intervals).hasSize(1);
		InstantInterval interval = intervals.get(0);

		// 01:00 CEST (UTC+2) = 2026-10-24T23:00:00Z
		// 05:00 CET (UTC+1) = 2026-10-25T04:00:00Z
		// Real elapsed instant duration = 5 hours (not 4 hours)
		assertThat(interval.start()).isEqualTo(Instant.parse("2026-10-24T23:00:00Z"));
		assertThat(interval.end()).isEqualTo(Instant.parse("2026-10-25T04:00:00Z"));
		assertThat(interval.duration()).isEqualTo(Duration.ofHours(5));
	}

	@Test
	void fallBackDayNormalWorkingHoursProducesContinuousNineHourInterval() {
		LocalDate fallBackDate = LocalDate.of(2026, 10, 25);
		VetWeeklyShift dayShift = new VetWeeklyShift(this.vet, 7, LocalTime.of(8, 0), LocalTime.of(17, 0));

		List<InstantInterval> intervals = EffectiveAvailabilityResolver.resolve(fallBackDate, ZONE, List.of(),
				List.of(), List.of(dayShift), List.of());

		assertThat(intervals).hasSize(1);
		InstantInterval interval = intervals.get(0);

		// 08:00 CET (UTC+1) = 07:00 UTC
		// 17:00 CET (UTC+1) = 16:00 UTC
		// Real elapsed duration = 9 hours
		assertThat(interval.start()).isEqualTo(Instant.parse("2026-10-25T07:00:00Z"));
		assertThat(interval.end()).isEqualTo(Instant.parse("2026-10-25T16:00:00Z"));
		assertThat(interval.duration()).isEqualTo(Duration.ofHours(9));
	}

}
