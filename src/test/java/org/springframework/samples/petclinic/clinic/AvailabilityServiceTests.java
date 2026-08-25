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

package org.springframework.samples.petclinic.clinic;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.samples.petclinic.scheduling.ai.SymbolicWindow;
import org.springframework.samples.petclinic.scheduling.solver.VetAvailability;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetDateException;
import org.springframework.samples.petclinic.vet.VetDateExceptionRepository;
import org.springframework.samples.petclinic.vet.VetDateExceptionType;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.samples.petclinic.vet.VetWeeklyShift;
import org.springframework.samples.petclinic.vet.VetWeeklyShiftRepository;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AvailabilityServiceTests {

	@Autowired
	private AvailabilityService availabilityService;

	@Autowired
	private ClinicSettingsRepository clinicSettingsRepository;

	@Autowired
	private PartOfDayRepository partOfDayRepository;

	@Autowired
	private VetRepository vetRepository;

	@Autowired
	private VetWeeklyShiftRepository vetWeeklyShiftRepository;

	@Autowired
	private VetDateExceptionRepository vetDateExceptionRepository;

	@Autowired
	private ClinicClosureRepository clinicClosureRepository;

	private Vet vet1;

	@BeforeEach
	void setUp() {
		vet1 = vetRepository.findById(1).orElseThrow();
	}

	@Test
	void shouldComputeRegularWeeklyShiftsAvailability() {
		// Vet 1 has seeded Mon-Fri 08:00 - 17:00 shifts
		// Tuesday 2026-09-01
		LocalDate tuesday = LocalDate.of(2026, 9, 1);
		assertThat(tuesday.getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);

		List<VetAvailability> avail = availabilityService.getVetAvailability(1, tuesday);
		assertThat(avail).hasSize(1);
		assertThat(avail.get(0).startTime()).isEqualTo(LocalDateTime.of(2026, 9, 1, 8, 0));
		assertThat(avail.get(0).endTime()).isEqualTo(LocalDateTime.of(2026, 9, 1, 17, 0));

		// Sunday 2026-09-06 (no shift)
		LocalDate sunday = LocalDate.of(2026, 9, 6);
		List<VetAvailability> sundayAvail = availabilityService.getVetAvailability(1, sunday);
		assertThat(sundayAvail).isEmpty();
	}

	@Test
	void shouldSubtractFullDayAndPartialClinicClosures() {
		LocalDate date = LocalDate.of(2026, 9, 1);

		// Full day closure
		ClinicClosure fullDay = new ClinicClosure(date, date, "Holiday");
		clinicClosureRepository.save(fullDay);

		List<VetAvailability> closedAvail = availabilityService.getVetAvailability(1, date);
		assertThat(closedAvail).isEmpty();

		clinicClosureRepository.delete(fullDay);

		// Partial day closure: 12:00 to 13:30 (e.g. staff all-hands meeting)
		ClinicClosure partial = new ClinicClosure(date, date, "Meeting", LocalTime.of(12, 0), LocalTime.of(13, 30));
		clinicClosureRepository.save(partial);

		List<VetAvailability> partialAvail = availabilityService.getVetAvailability(1, date);
		assertThat(partialAvail).hasSize(2);
		assertThat(partialAvail.get(0).startTime()).isEqualTo(LocalDateTime.of(2026, 9, 1, 8, 0));
		assertThat(partialAvail.get(0).endTime()).isEqualTo(LocalDateTime.of(2026, 9, 1, 12, 0));
		assertThat(partialAvail.get(1).startTime()).isEqualTo(LocalDateTime.of(2026, 9, 1, 13, 30));
		assertThat(partialAvail.get(1).endTime()).isEqualTo(LocalDateTime.of(2026, 9, 1, 17, 0));
	}

	@Test
	void shouldSubtractFullDayAndPartialVetLeave() {
		LocalDate date = LocalDate.of(2026, 9, 2); // Wednesday

		// Partial leave: 08:00 to 10:00 (late arrival)
		VetDateException partialLeave = new VetDateException(vet1, date, date, VetDateExceptionType.LEAVE,
				LocalTime.of(8, 0), LocalTime.of(10, 0), "Doctor Appointment");
		vetDateExceptionRepository.save(partialLeave);

		List<VetAvailability> avail = availabilityService.getVetAvailability(1, date);
		assertThat(avail).hasSize(1);
		assertThat(avail.get(0).startTime()).isEqualTo(LocalDateTime.of(2026, 9, 2, 10, 0));
		assertThat(avail.get(0).endTime()).isEqualTo(LocalDateTime.of(2026, 9, 2, 17, 0));

		// Full day leave
		VetDateException fullDayLeave = new VetDateException(vet1, date, date, VetDateExceptionType.LEAVE, null, null,
				"PTO");
		vetDateExceptionRepository.save(fullDayLeave);

		List<VetAvailability> noAvail = availabilityService.getVetAvailability(1, date);
		assertThat(noAvail).isEmpty();
	}

	@Test
	void shouldApplyModifiedHoursAndExtraShifts() {
		LocalDate date = LocalDate.of(2026, 9, 3); // Thursday

		// Modified hours: 10:00 to 14:00 (overrides the 08:00-17:00 shift)
		VetDateException modified = new VetDateException(vet1, date, date, VetDateExceptionType.MODIFIED_HOURS,
				LocalTime.of(10, 0), LocalTime.of(14, 0), "Special schedule");
		vetDateExceptionRepository.save(modified);

		List<VetAvailability> avail = availabilityService.getVetAvailability(1, date);
		assertThat(avail).hasSize(1);
		assertThat(avail.get(0).startTime()).isEqualTo(LocalDateTime.of(2026, 9, 3, 10, 0));
		assertThat(avail.get(0).endTime()).isEqualTo(LocalDateTime.of(2026, 9, 3, 14, 0));

		vetDateExceptionRepository.delete(modified);

		// Extra shift on Saturday: 09:00 to 13:00
		LocalDate saturday = LocalDate.of(2026, 9, 5);
		VetDateException extra = new VetDateException(vet1, saturday, saturday, VetDateExceptionType.EXTRA,
				LocalTime.of(9, 0), LocalTime.of(13, 0), "Weekend cover");
		vetDateExceptionRepository.save(extra);

		List<VetAvailability> satAvail = availabilityService.getVetAvailability(1, saturday);
		assertThat(satAvail).hasSize(1);
		assertThat(satAvail.get(0).startTime()).isEqualTo(LocalDateTime.of(2026, 9, 5, 9, 0));
		assertThat(satAvail.get(0).endTime()).isEqualTo(LocalDateTime.of(2026, 9, 5, 13, 0));
	}

	@Test
	void shouldHandleSplitShiftsCorrectly() {
		vetWeeklyShiftRepository.deleteByVetId(1);
		VetWeeklyShift morning = new VetWeeklyShift(vet1, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0));
		VetWeeklyShift afternoon = new VetWeeklyShift(vet1, DayOfWeek.MONDAY, LocalTime.of(13, 0), LocalTime.of(17, 0));
		vetWeeklyShiftRepository.save(morning);
		vetWeeklyShiftRepository.save(afternoon);

		LocalDate monday = LocalDate.of(2026, 8, 31);
		List<VetAvailability> avail = availabilityService.getVetAvailability(1, monday);
		assertThat(avail).hasSize(2);
		assertThat(avail.get(0).startTime()).isEqualTo(LocalDateTime.of(2026, 8, 31, 8, 0));
		assertThat(avail.get(0).endTime()).isEqualTo(LocalDateTime.of(2026, 8, 31, 12, 0));
		assertThat(avail.get(1).startTime()).isEqualTo(LocalDateTime.of(2026, 8, 31, 13, 0));
		assertThat(avail.get(1).endTime()).isEqualTo(LocalDateTime.of(2026, 8, 31, 17, 0));
	}

	@Test
	void shouldInterpretNowAndHorizonInClinicTimeZoneAcrossDst() {
		// DST Spring Forward: 2026-03-08 in America/New_York (UTC-5 -> UTC-4)
		// Instant corresponding to 2026-03-08 01:30 EST: 2026-03-08T06:30:00Z
		Instant dstSpringInstant = Instant.parse("2026-03-08T06:30:00Z");
		Clock dstClock = Clock.fixed(dstSpringInstant, ZoneId.of("UTC"));
		availabilityService.setClock(dstClock);

		ZonedDateTime nowInClinic = availabilityService.getNow();
		assertThat(nowInClinic.getZone()).isEqualTo(ZoneId.of("America/New_York"));
		assertThat(nowInClinic.toLocalDate()).isEqualTo(LocalDate.of(2026, 3, 8));
		assertThat(availabilityService.getToday()).isEqualTo(LocalDate.of(2026, 3, 8));
		assertThat(availabilityService.getHorizonEndDate()).isEqualTo(LocalDate.of(2026, 3, 22));

		// DST Fall Back: 2026-11-01 in America/New_York (UTC-4 -> UTC-5)
		// Instant corresponding to 2026-11-01 01:30 EDT: 2026-11-01T05:30:00Z
		Instant dstFallInstant = Instant.parse("2026-11-01T05:30:00Z");
		availabilityService.setClock(Clock.fixed(dstFallInstant, ZoneId.of("UTC")));

		ZonedDateTime fallNow = availabilityService.getNow();
		assertThat(fallNow.getZone()).isEqualTo(ZoneId.of("America/New_York"));
		assertThat(fallNow.toLocalDate()).isEqualTo(LocalDate.of(2026, 11, 1));
		assertThat(availabilityService.getToday()).isEqualTo(LocalDate.of(2026, 11, 1));
		assertThat(availabilityService.getHorizonEndDate()).isEqualTo(LocalDate.of(2026, 11, 15));

		// Reset clock
		availabilityService.setClock(Clock.systemDefaultZone());
	}

	@Test
	void shouldResolveSymbolicWindowsUsingPartsOfDay() {
		LocalDate start = LocalDate.of(2026, 9, 1); // Tuesday
		LocalDate end = LocalDate.of(2026, 9, 7); // Monday

		// Monday MORNING window within range
		SymbolicWindow mondayMorning = new SymbolicWindow(DayOfWeek.MONDAY, null, "MORNING");
		List<AvailabilityService.DateTimeRange> resolved = availabilityService.resolveSymbolicWindow(mondayMorning,
				start, end);

		assertThat(resolved).hasSize(1);
		assertThat(resolved.get(0).start()).isEqualTo(LocalDateTime.of(2026, 9, 7, 8, 0));
		assertThat(resolved.get(0).end()).isEqualTo(LocalDateTime.of(2026, 9, 7, 12, 0));

		// Overlap check
		assertThat(resolved.get(0).overlaps(LocalDateTime.of(2026, 9, 7, 9, 0), LocalDateTime.of(2026, 9, 7, 9, 30)))
			.isTrue();
		assertThat(resolved.get(0).overlaps(LocalDateTime.of(2026, 9, 7, 14, 0), LocalDateTime.of(2026, 9, 7, 14, 30)))
			.isFalse();
	}

}
