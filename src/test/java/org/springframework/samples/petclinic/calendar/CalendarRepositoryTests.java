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
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class CalendarRepositoryTests {

	@Autowired
	private ClinicSettingsRepository clinicSettingsRepository;

	@Autowired
	private VetWeeklyShiftRepository shiftRepository;

	@Autowired
	private VetAvailabilityExceptionRepository exceptionRepository;

	@Autowired
	private VetLeaveRepository leaveRepository;

	@Autowired
	private ClinicClosureRepository closureRepository;

	@Autowired
	private VetRepository vetRepository;

	@Autowired
	private EffectiveAvailabilityResolver resolver;

	@Autowired
	private GridGenerator gridGenerator;

	@Test
	void clinicSettingsLoadsFromSeed() {
		ClinicSettings settings = this.clinicSettingsRepository.getClinicSettings();
		assertThat(settings).isNotNull();
		assertThat(settings.getGridGranularityMin()).isEqualTo(15);
		assertThat(settings.getHoldDurationMin()).isEqualTo(10);
		assertThat(settings.getBookingHorizonDays()).isEqualTo(60);
		assertThat(settings.getMinVisitMin()).isEqualTo(15);
		assertThat(settings.getMaxVisitMin()).isEqualTo(120);
		assertThat(settings.getDefaultVisitMin()).isEqualTo(30);
		assertThat(settings.getZoneId()).isEqualTo("Europe/Amsterdam");
	}

	@Test
	void persistsAndResolvesAvailabilityThroughRepositories() {
		Vet vet = this.vetRepository.findAll().iterator().next();

		LocalDate monday = LocalDate.of(2026, 6, 1); // Monday (day 1)
		VetWeeklyShift shift = new VetWeeklyShift(vet, 1, LocalTime.of(9, 0), LocalTime.of(17, 0));
		this.shiftRepository.save(shift);

		ClinicSettings settings = this.clinicSettingsRepository.getClinicSettings();
		List<InstantInterval> intervals = this.resolver.resolve(vet.getId(), monday, settings);

		assertThat(intervals).hasSize(1);

		// Now add a closure for that date and verify resolution becomes empty
		ClinicClosure closure = new ClinicClosure(monday, monday);
		this.closureRepository.save(closure);

		List<InstantInterval> afterClosure = this.resolver.resolve(vet.getId(), monday, settings);
		assertThat(afterClosure).isEmpty();
	}

	@Test
	void weekdayAvailabilityLoadsFromSeedAndProducesFridayCandidates() {
		List<Vet> vets = this.vetRepository.findAll();

		assertThat(vets).isNotEmpty().allSatisfy(vet -> {
			List<VetWeeklyShift> shifts = this.shiftRepository.findByVetId(vet.getId());
			assertThat(shifts).hasSize(5)
				.extracting(VetWeeklyShift::getDayOfWeek)
				.containsExactlyInAnyOrder(1, 2, 3, 4, 5);
			assertThat(shifts).allSatisfy(shift -> {
				assertThat(shift.getStartLocal()).isEqualTo(LocalTime.of(9, 0));
				assertThat(shift.getEndLocal()).isEqualTo(LocalTime.of(17, 0));
			});
		});

		Vet vet = vets.getFirst();
		ClinicSettings settings = this.clinicSettingsRepository.getClinicSettings();
		List<Instant> candidates = this.gridGenerator.generateCandidateStarts(vet.getId(), LocalDate.of(2026, 9, 4), 30,
				settings);

		assertThat(candidates).hasSize(31).first().isEqualTo(Instant.parse("2026-09-04T07:00:00Z"));
	}

}
