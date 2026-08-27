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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.vet.Vet;

import static org.assertj.core.api.Assertions.assertThat;

class EffectiveAvailabilityResolverTests {

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
	void recurringSingleShiftProducesSingleInterval() {
		LocalDate monday = LocalDate.of(2026, 6, 1); // Monday
		VetWeeklyShift shift = new VetWeeklyShift(this.vet, 1, LocalTime.of(9, 0), LocalTime.of(17, 0));

		List<InstantInterval> intervals = EffectiveAvailabilityResolver.resolve(monday, ZONE, List.of(), List.of(),
				List.of(shift), List.of());

		assertThat(intervals).hasSize(1);
		assertThat(intervals.get(0).duration()).isEqualTo(Duration.ofHours(8));
	}

	@Test
	void splitShiftProducesTwoDistinctIntervalsWithUnavailableBreak() {
		LocalDate tuesday = LocalDate.of(2026, 6, 2); // Tuesday
		VetWeeklyShift morning = new VetWeeklyShift(this.vet, 2, LocalTime.of(9, 0), LocalTime.of(12, 0));
		VetWeeklyShift afternoon = new VetWeeklyShift(this.vet, 2, LocalTime.of(13, 0), LocalTime.of(17, 0));

		List<InstantInterval> intervals = EffectiveAvailabilityResolver.resolve(tuesday, ZONE, List.of(), List.of(),
				List.of(morning, afternoon), List.of());

		assertThat(intervals).hasSize(2);
		assertThat(intervals.get(0).duration()).isEqualTo(Duration.ofHours(3));
		assertThat(intervals.get(1).duration()).isEqualTo(Duration.ofHours(4));
		assertThat(Duration.between(intervals.get(0).end(), intervals.get(1).start())).isEqualTo(Duration.ofHours(1));
	}

	@Test
	void offDayProducesNoIntervals() {
		LocalDate sunday = LocalDate.of(2026, 6, 7); // Sunday
		// Vet has shift only on Monday
		VetWeeklyShift mondayShift = new VetWeeklyShift(this.vet, 1, LocalTime.of(9, 0), LocalTime.of(17, 0));

		List<InstantInterval> intervals = EffectiveAvailabilityResolver.resolve(sunday, ZONE, List.of(), List.of(),
				List.of(mondayShift), List.of());

		assertThat(intervals).isEmpty();
	}

	@Test
	void clinicClosureOverridesAllShiftsAndExceptions() {
		LocalDate date = LocalDate.of(2026, 6, 1);
		VetWeeklyShift shift = new VetWeeklyShift(this.vet, 1, LocalTime.of(9, 0), LocalTime.of(17, 0));
		VetAvailabilityException addException = new VetAvailabilityException(this.vet, date, ExceptionType.ADD,
				LocalTime.of(18, 0), LocalTime.of(20, 0));
		ClinicClosure closure = new ClinicClosure(date, date);

		List<InstantInterval> intervals = EffectiveAvailabilityResolver.resolve(date, ZONE, List.of(closure), List.of(),
				List.of(shift), List.of(addException));

		assertThat(intervals).isEmpty();
	}

	@Test
	void clinicClosureSpanningRangeOverridesDates() {
		LocalDate from = LocalDate.of(2026, 12, 24);
		LocalDate to = LocalDate.of(2026, 12, 26);
		ClinicClosure closure = new ClinicClosure(from, to);

		VetWeeklyShift shift = new VetWeeklyShift(this.vet, 4, LocalTime.of(9, 0), LocalTime.of(17, 0));

		List<InstantInterval> intervals = EffectiveAvailabilityResolver.resolve(LocalDate.of(2026, 12, 25), ZONE,
				List.of(closure), List.of(), List.of(shift), List.of());

		assertThat(intervals).isEmpty();
	}

	@Test
	void vetLeaveOverridesShiftsAndExceptionsForVet() {
		LocalDate date = LocalDate.of(2026, 6, 1);
		VetWeeklyShift shift = new VetWeeklyShift(this.vet, 1, LocalTime.of(9, 0), LocalTime.of(17, 0));
		VetAvailabilityException addException = new VetAvailabilityException(this.vet, date, ExceptionType.ADD,
				LocalTime.of(18, 0), LocalTime.of(20, 0));
		VetLeave leave = new VetLeave(this.vet, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5));

		List<InstantInterval> intervals = EffectiveAvailabilityResolver.resolve(date, ZONE, List.of(), List.of(leave),
				List.of(shift), List.of(addException));

		assertThat(intervals).isEmpty();
	}

	@Test
	void replaceExceptionOverridesRecurringShift() {
		LocalDate date = LocalDate.of(2026, 6, 1);
		VetWeeklyShift regularShift = new VetWeeklyShift(this.vet, 1, LocalTime.of(9, 0), LocalTime.of(17, 0));
		VetAvailabilityException replace = new VetAvailabilityException(this.vet, date, ExceptionType.REPLACE,
				LocalTime.of(10, 0), LocalTime.of(14, 0));

		List<InstantInterval> intervals = EffectiveAvailabilityResolver.resolve(date, ZONE, List.of(), List.of(),
				List.of(regularShift), List.of(replace));

		assertThat(intervals).hasSize(1);
		assertThat(intervals.get(0).duration()).isEqualTo(Duration.ofHours(4));
	}

	@Test
	void fullDayRemoveExceptionClearsRecurringShift() {
		LocalDate date = LocalDate.of(2026, 6, 1);
		VetWeeklyShift regularShift = new VetWeeklyShift(this.vet, 1, LocalTime.of(9, 0), LocalTime.of(17, 0));
		VetAvailabilityException remove = new VetAvailabilityException(this.vet, date, ExceptionType.REMOVE, null,
				null);

		List<InstantInterval> intervals = EffectiveAvailabilityResolver.resolve(date, ZONE, List.of(), List.of(),
				List.of(regularShift), List.of(remove));

		assertThat(intervals).isEmpty();
	}

	@Test
	void partialRemoveExceptionSubtractsFromRecurringShift() {
		LocalDate date = LocalDate.of(2026, 6, 1);
		VetWeeklyShift regularShift = new VetWeeklyShift(this.vet, 1, LocalTime.of(9, 0), LocalTime.of(17, 0));
		VetAvailabilityException removeLunch = new VetAvailabilityException(this.vet, date, ExceptionType.REMOVE,
				LocalTime.of(12, 0), LocalTime.of(13, 0));

		List<InstantInterval> intervals = EffectiveAvailabilityResolver.resolve(date, ZONE, List.of(), List.of(),
				List.of(regularShift), List.of(removeLunch));

		assertThat(intervals).hasSize(2);
		assertThat(intervals.get(0).duration()).isEqualTo(Duration.ofHours(3)); // 09:00-12:00
		assertThat(intervals.get(1).duration()).isEqualTo(Duration.ofHours(4)); // 13:00-17:00
	}

	@Test
	void addExceptionOnOtherwiseOffDayAddsAvailability() {
		LocalDate sunday = LocalDate.of(2026, 6, 7);
		VetAvailabilityException addSunday = new VetAvailabilityException(this.vet, sunday, ExceptionType.ADD,
				LocalTime.of(10, 0), LocalTime.of(14, 0));

		List<InstantInterval> intervals = EffectiveAvailabilityResolver.resolve(sunday, ZONE, List.of(), List.of(),
				List.of(), List.of(addSunday));

		assertThat(intervals).hasSize(1);
		assertThat(intervals.get(0).duration()).isEqualTo(Duration.ofHours(4));
	}

	@Test
	void addExceptionOnWorkingDayUnionsAvailability() {
		LocalDate date = LocalDate.of(2026, 6, 1);
		VetWeeklyShift regularShift = new VetWeeklyShift(this.vet, 1, LocalTime.of(9, 0), LocalTime.of(17, 0));
		VetAvailabilityException addEvening = new VetAvailabilityException(this.vet, date, ExceptionType.ADD,
				LocalTime.of(17, 0), LocalTime.of(19, 0));

		List<InstantInterval> intervals = EffectiveAvailabilityResolver.resolve(date, ZONE, List.of(), List.of(),
				List.of(regularShift), List.of(addEvening));

		// Adjacent intervals 09:00-17:00 and 17:00-19:00 merge into 09:00-19:00
		assertThat(intervals).hasSize(1);
		assertThat(intervals.get(0).duration()).isEqualTo(Duration.ofHours(10));
	}

	@Test
	void overlappingShiftsMergeDeterministically() {
		LocalDate date = LocalDate.of(2026, 6, 1);
		VetWeeklyShift shift1 = new VetWeeklyShift(this.vet, 1, LocalTime.of(9, 0), LocalTime.of(13, 0));
		VetWeeklyShift shift2 = new VetWeeklyShift(this.vet, 1, LocalTime.of(12, 0), LocalTime.of(17, 0));

		List<InstantInterval> intervals = EffectiveAvailabilityResolver.resolve(date, ZONE, List.of(), List.of(),
				List.of(shift1, shift2), List.of());

		assertThat(intervals).hasSize(1);
		assertThat(intervals.get(0).duration()).isEqualTo(Duration.ofHours(8));
	}

	@Test
	void precedenceOrderClosureOverLeaveOverExceptionsOverShifts() {
		LocalDate date = LocalDate.of(2026, 6, 1);
		ClinicClosure closure = new ClinicClosure(date, date);
		VetLeave leave = new VetLeave(this.vet, date, date);
		VetAvailabilityException add = new VetAvailabilityException(this.vet, date, ExceptionType.ADD,
				LocalTime.of(8, 0), LocalTime.of(20, 0));
		VetWeeklyShift shift = new VetWeeklyShift(this.vet, 1, LocalTime.of(9, 0), LocalTime.of(17, 0));

		// When closure exists -> empty
		assertThat(EffectiveAvailabilityResolver.resolve(date, ZONE, List.of(closure), List.of(leave), List.of(shift),
				List.of(add)))
			.isEmpty();

		// When closure removed, leave exists -> empty
		assertThat(EffectiveAvailabilityResolver.resolve(date, ZONE, List.of(), List.of(leave), List.of(shift),
				List.of(add)))
			.isEmpty();

		// When leave removed, add + shift exist -> active
		List<InstantInterval> active = EffectiveAvailabilityResolver.resolve(date, ZONE, List.of(), List.of(),
				List.of(shift), List.of(add));
		assertThat(active).isNotEmpty();
	}

}
