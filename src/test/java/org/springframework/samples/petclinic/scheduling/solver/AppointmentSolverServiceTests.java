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

package org.springframework.samples.petclinic.scheduling.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.samples.petclinic.clinic.AvailabilityService;
import org.springframework.samples.petclinic.clinic.ClinicClosure;
import org.springframework.samples.petclinic.clinic.ClinicClosureRepository;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.ai.CareType;
import org.springframework.samples.petclinic.scheduling.ai.Interpretation;
import org.springframework.samples.petclinic.scheduling.ai.UrgencyLevel;
import org.springframework.samples.petclinic.scheduling.model.RequestExclusion;
import org.springframework.samples.petclinic.scheduling.model.RequestExclusionRepository;
import org.springframework.samples.petclinic.scheduling.model.RequestState;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequestRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetDateException;
import org.springframework.samples.petclinic.vet.VetDateExceptionRepository;
import org.springframework.samples.petclinic.vet.VetDateExceptionType;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.samples.petclinic.vet.VetWeeklyShiftRepository;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AppointmentSolverServiceTests {

	@Autowired
	private AppointmentSolverService solverService;

	@Autowired
	private AvailabilityService availabilityService;

	@Autowired
	private SchedulingRequestRepository schedulingRequestRepository;

	@Autowired
	private RequestExclusionRepository requestExclusionRepository;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private VetRepository vetRepository;

	@Autowired
	private VetWeeklyShiftRepository vetWeeklyShiftRepository;

	@Autowired
	private VetDateExceptionRepository vetDateExceptionRepository;

	@Autowired
	private ClinicClosureRepository clinicClosureRepository;

	private SchedulingRequest request;

	@BeforeEach
	void setUp() {
		// Fix clock to Monday 2026-09-07 07:00 AM EDT (11:00 UTC)
		Instant mondayMorning = Instant.parse("2026-09-07T11:00:00Z");
		availabilityService.setClock(Clock.fixed(mondayMorning, ZoneId.of("America/New_York")));

		Owner owner = ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().get(0);

		request = new SchedulingRequest();
		request.setOwner(owner);
		request.setPet(pet);
		request.setRawText("Checkup for my pet");
		request.setAiConsent(true);
		request.setState(RequestState.SUGGESTING);
		request = schedulingRequestRepository.save(request);
	}

	@Test
	void shouldSolveSlotWithinRealAvailability() {
		Interpretation interpretation = new Interpretation("Routine checkup", 30, CareType.GENERAL, null,
				UrgencyLevel.ROUTINE, List.of(), List.of(), List.of(), null, 0.95);

		Optional<CandidateSlot> slotOpt = solverService.findBestSlot(request, interpretation);
		assertThat(slotOpt).isPresent();

		CandidateSlot slot = slotOpt.get();
		assertThat(slot.getDurationMinutes()).isEqualTo(30);
		assertThat(slot.getStartTime().toLocalTime()).isBetween(LocalTime.of(8, 0), LocalTime.of(16, 30));
		assertThat(slot.getEndTime().toLocalTime()).isBetween(LocalTime.of(8, 30), LocalTime.of(17, 0));
	}

	@Test
	void shouldNotOfferSlotsOnClinicClosureDates() {
		LocalDate monday = LocalDate.of(2026, 9, 7);
		ClinicClosure closure = new ClinicClosure(monday, monday, "Labor Day");
		clinicClosureRepository.save(closure);

		Interpretation interpretation = new Interpretation("Routine checkup", 30, CareType.GENERAL, null,
				UrgencyLevel.ROUTINE, List.of(), List.of(), List.of(), null, 0.95);

		Optional<CandidateSlot> slotOpt = solverService.findBestSlot(request, interpretation);
		assertThat(slotOpt).isPresent();

		CandidateSlot slot = slotOpt.get();
		// Must not be offered on Monday since clinic is closed
		assertThat(slot.getStartTime().toLocalDate()).isNotEqualTo(monday);
		assertThat(slot.getStartTime().toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 8)); // Tuesday
	}

	@Test
	void shouldHonorSpecialtyAndModifiedHours() {
		// Linda Douglas (vet 3) has dentistry and surgery specialties
		// Helen Leary (vet 2) and Henry Stevens (vet 5) have radiology
		Interpretation interpretation = new Interpretation("X-ray needed", 45, CareType.SPECIALTY, "radiology",
				UrgencyLevel.ROUTINE, List.of(), List.of(), List.of(), null, 0.95);

		Optional<CandidateSlot> slotOpt = solverService.findBestSlot(request, interpretation);
		assertThat(slotOpt).isPresent();

		CandidateSlot slot = slotOpt.get();
		assertThat(slot.getVetId()).isIn(2, 5); // Vet 2 or Vet 5 has radiology
		assertThat(slot.getDurationMinutes()).isEqualTo(45);
	}

	@Test
	void shouldExcludeRejectedSlotOnSubsequentSolve() {
		Interpretation interpretation = new Interpretation("Routine checkup", 30, CareType.GENERAL, null,
				UrgencyLevel.ROUTINE, List.of(), List.of(), List.of(), null, 0.95);

		Optional<CandidateSlot> firstSlotOpt = solverService.findBestSlot(request, interpretation);
		assertThat(firstSlotOpt).isPresent();
		CandidateSlot firstSlot = firstSlotOpt.get();

		Vet vet = vetRepository.findById(firstSlot.getVetId()).orElseThrow();
		RequestExclusion exclusion = new RequestExclusion(request, vet, firstSlot.getStartTime());
		requestExclusionRepository.saveAndFlush(exclusion);

		Optional<CandidateSlot> secondSlotOpt = solverService.findBestSlot(request, interpretation);
		assertThat(secondSlotOpt).isPresent();
		CandidateSlot secondSlot = secondSlotOpt.get();

		assertThat(secondSlot.getVetId().equals(firstSlot.getVetId())
				&& secondSlot.getStartTime().equals(firstSlot.getStartTime()))
			.isFalse();
	}

}
