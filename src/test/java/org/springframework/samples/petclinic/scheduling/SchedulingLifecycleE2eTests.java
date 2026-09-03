/*
 * Copyright 2012-2026 the original author or authors.
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

package org.springframework.samples.petclinic.scheduling;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.interpretation.CareType;
import org.springframework.samples.petclinic.scheduling.interpretation.Interpretation;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationRepository;
import org.springframework.samples.petclinic.scheduling.interpretation.Provenance;
import org.springframework.samples.petclinic.scheduling.request.RequestLifecycleService;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.SuggestionService;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end lifecycle test for primary use case UC-1 (RULE-44, AC-138). Executes all 8
 * steps of the main success scenario.
 */
@SpringBootTest
@Import(TestClockConfig.class)
@Transactional
class SchedulingLifecycleE2eTests {

	@Autowired
	private RequestLifecycleService requestLifecycleService;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private InterpretationRepository interpretationRepository;

	@Autowired
	private SuggestionService suggestionService;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private VetRepository vetRepository;

	@Autowired
	private Clock clock;

	private Owner owner;

	private Pet pet;

	private Vet vet;

	@BeforeEach
	void setUp() {
		this.owner = this.ownerRepository.findById(1).orElseThrow();
		this.pet = this.owner.getPet(1);
		this.vet = this.vetRepository.findAll().iterator().next();
	}

	@Test
	void ownerGuidedFlowMainScenario() {
		// Step 1: Owner starts request entering reason and availability text
		SchedulingRequest request = this.requestLifecycleService.createRequest(this.owner, this.pet,
				"Annual wellness checkup and vaccinations", "Monday morning preferred", "owner_1");
		assertThat(request.getState()).isEqualTo(RequestState.AWAITING_CONSENT);
		assertThat(request.getActivePetId()).isEqualTo(this.pet.getId());

		// Step 2: Owner grants explicit consent
		SchedulingRequest interpretingReq = this.requestLifecycleService.consent(request, "owner_1");
		assertThat(interpretingReq.getState()).isEqualTo(RequestState.INTERPRETING);

		// Step 3: System interprets
		Interpretation interpretation = new Interpretation();
		interpretation.setRequest(interpretingReq);
		interpretation.setVersion(1);
		interpretation.setProvenance(Provenance.AI);
		interpretation.setReasonSummary("Annual wellness checkup");
		interpretation.setEstimatedMinutes(30);
		interpretation.setCareType(CareType.GENERAL);
		interpretation.setCreatedAt(ZonedDateTime.now(this.clock));
		this.interpretationRepository.saveAndFlush(interpretation);

		SchedulingRequest interpretedReq = this.requestLifecycleService.interpretationUsable(interpretingReq, "system");
		assertThat(interpretedReq.getState()).isEqualTo(RequestState.INTERPRETED);

		// Step 4: System presents persisted interpretation for read-only review
		Interpretation persistedInterp = this.interpretationRepository
			.findTopByRequestIdOrderByVersionDesc(interpretedReq.getId())
			.orElseThrow();
		assertThat(persistedInterp.getReasonSummary()).isEqualTo("Annual wellness checkup");

		// Step 5: Owner confirms
		// Step 6: System holds and offers exactly one ranked slot
		SchedulingRequest offeredReq = this.suggestionService.confirm(interpretedReq, "owner_1");
		assertThat(offeredReq.getState()).isEqualTo(RequestState.SUGGESTION_OFFERED);
		assertThat(offeredReq.hasHold()).isTrue();
		assertThat(offeredReq.getHeldVet()).isNotNull();
		assertThat(offeredReq.getHeldStart()).isNotNull();

		// Step 7: Owner accepts
		// Step 8: System confirms appointment and records it under My appointments
		Appointment appointment = this.suggestionService.accept(offeredReq, "owner_1");
		assertThat(appointment).isNotNull();
		assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
		assertThat(appointment.getPet().getId()).isEqualTo(this.pet.getId());

		List<Appointment> upcoming = this.appointmentRepository.findUpcomingByPetId(this.pet.getId(),
				ZonedDateTime.now(this.clock).minusDays(1));
		assertThat(upcoming).hasSize(1);
		assertThat(upcoming.get(0).getId()).isEqualTo(appointment.getId());

		SchedulingRequest finalRequest = this.requestRepository.findById(request.getId()).orElseThrow();
		assertThat(finalRequest.getState()).isEqualTo(RequestState.ACCEPTED);
		assertThat(finalRequest.getActivePetId()).isNull();
	}

}
