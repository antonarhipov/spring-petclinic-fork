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

package org.springframework.samples.petclinic.vet;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.samples.petclinic.clinic.ClinicClosure;
import org.springframework.samples.petclinic.clinic.ClinicClosureRepository;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class VetAvailabilityModelTests {

	@Autowired
	private VetRepository vetRepository;

	@Autowired
	private VetWeeklyShiftRepository vetWeeklyShiftRepository;

	@Autowired
	private VetDateExceptionRepository vetDateExceptionRepository;

	@Autowired
	private ClinicClosureRepository clinicClosureRepository;

	@Test
	void shouldLoadSeededWeeklyShifts() {
		List<VetWeeklyShift> shifts = vetWeeklyShiftRepository.findByVetId(1);
		assertThat(shifts).hasSize(5);
		assertThat(shifts.get(0).getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
		assertThat(shifts.get(0).getStartTime()).isEqualTo(LocalTime.of(8, 0));
		assertThat(shifts.get(0).getEndTime()).isEqualTo(LocalTime.of(17, 0));
	}

	@Test
	void shouldPersistAndRoundTripSplitShifts() {
		Vet vet = vetRepository.findById(1).orElseThrow();
		vetWeeklyShiftRepository.deleteByVetId(1);

		VetWeeklyShift morningShift = new VetWeeklyShift(vet, DayOfWeek.MONDAY, LocalTime.of(8, 0),
				LocalTime.of(12, 0));
		VetWeeklyShift afternoonShift = new VetWeeklyShift(vet, DayOfWeek.MONDAY, LocalTime.of(13, 0),
				LocalTime.of(17, 0));

		vetWeeklyShiftRepository.save(morningShift);
		vetWeeklyShiftRepository.save(afternoonShift);

		List<VetWeeklyShift> mondayShifts = vetWeeklyShiftRepository.findByVetIdAndDayOfWeek(1, DayOfWeek.MONDAY);
		assertThat(mondayShifts).hasSize(2);
		assertThat(mondayShifts).extracting(VetWeeklyShift::getStartTime)
			.containsExactlyInAnyOrder(LocalTime.of(8, 0), LocalTime.of(13, 0));
		assertThat(mondayShifts).extracting(VetWeeklyShift::getEndTime)
			.containsExactlyInAnyOrder(LocalTime.of(12, 0), LocalTime.of(17, 0));
	}

	@Test
	void shouldPersistAndQueryVetDateExceptions() {
		Vet vet = vetRepository.findById(2).orElseThrow();
		LocalDate today = LocalDate.now();

		VetDateException leaveException = new VetDateException(vet, today, today.plusDays(2),
				VetDateExceptionType.LEAVE, null, null, "Vacation");
		vetDateExceptionRepository.save(leaveException);

		VetDateException extraException = new VetDateException(vet, today.plusDays(5), today.plusDays(5),
				VetDateExceptionType.EXTRA, LocalTime.of(18, 0), LocalTime.of(20, 0), "Overtime");
		vetDateExceptionRepository.save(extraException);

		List<VetDateException> exceptions = vetDateExceptionRepository.findByVetIdAndDateRange(2, today,
				today.plusDays(10));
		assertThat(exceptions).hasSize(2);

		List<VetDateException> vacationOnly = vetDateExceptionRepository.findByVetIdAndDateRange(2, today,
				today.plusDays(1));
		assertThat(vacationOnly).hasSize(1);
		assertThat(vacationOnly.get(0).getType()).isEqualTo(VetDateExceptionType.LEAVE);
		assertThat(vacationOnly.get(0).isFullDay()).isTrue();
	}

	@Test
	void shouldPersistAndQueryClinicClosures() {
		LocalDate date = LocalDate.of(2026, 12, 25);
		ClinicClosure holiday = new ClinicClosure(date, date, "Christmas Day");
		clinicClosureRepository.save(holiday);

		List<ClinicClosure> closures = clinicClosureRepository.findByDateRange(date, date);
		assertThat(closures).hasSize(1);
		assertThat(closures.get(0).getReason()).isEqualTo("Christmas Day");
		assertThat(closures.get(0).isFullDay()).isTrue();
		assertThat(closures.get(0).coversDate(date)).isTrue();
		assertThat(closures.get(0).coversDate(date.plusDays(1))).isFalse();
	}

}
