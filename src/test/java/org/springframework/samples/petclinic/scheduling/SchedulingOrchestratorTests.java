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

package org.springframework.samples.petclinic.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.clinic.AvailabilityService;
import org.springframework.samples.petclinic.clinic.ClinicSettings;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.ai.AppointmentInterpreter;
import org.springframework.samples.petclinic.scheduling.ai.CareType;
import org.springframework.samples.petclinic.scheduling.ai.Interpretation;
import org.springframework.samples.petclinic.scheduling.ai.UrgencyLevel;
import org.springframework.samples.petclinic.scheduling.model.QueueReason;
import org.springframework.samples.petclinic.scheduling.model.RequestState;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.solver.AppointmentSolverService;
import org.springframework.samples.petclinic.scheduling.solver.CandidateSlot;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import tools.jackson.databind.ObjectMapper;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({ AsyncConfig.class, ObjectMapper.class })
class SchedulingOrchestratorTests {

	@Autowired
	private SchedulingRequestRepository schedulingRequests;

	@Autowired
	private OwnerRepository owners;

	@Autowired
	private VetRepository vets;

	@Autowired
	private ObjectMapper objectMapper;

	private AppointmentInterpreter interpreter;

	private AppointmentSolverService solverService;

	private HoldService holdService;

	private AvailabilityService availabilityService;

	private SchedulingOrchestrator orchestrator;

	private Owner owner;

	private Pet pet;

	@BeforeEach
	void setUp() {
		this.interpreter = mock(AppointmentInterpreter.class);
		this.solverService = mock(AppointmentSolverService.class);
		this.holdService = mock(HoldService.class);
		this.availabilityService = mock(AvailabilityService.class);
		this.orchestrator = new SchedulingOrchestrator(this.schedulingRequests, this.interpreter, this.solverService,
				this.holdService, this.vets, this.availabilityService, this.objectMapper);
		this.owner = this.owners.findById(1).orElseThrow();
		this.pet = this.owner.getPets().get(0);
	}

	@Test
	void shouldTransitionToAwaitingConfirmationOnSuccessfulInterpretation() {
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(this.owner);
		request.setPet(this.pet);
		request.setAiConsent(true);
		request.setRawText("Annual checkup next week");
		request.setState(RequestState.DRAFT);
		request = this.schedulingRequests.saveAndFlush(request);

		Interpretation interpretation = new Interpretation("Annual checkup", 30, CareType.GENERAL, null,
				UrgencyLevel.ROUTINE, List.of(), List.of(), List.of(), null, 0.95);
		when(this.interpreter.interpret("Annual checkup next week")).thenReturn(Optional.of(interpretation));

		this.orchestrator.processInterpretationAsync(request.getId());

		SchedulingRequest updated = this.schedulingRequests.findById(request.getId()).orElseThrow();
		assertThat(updated.getState()).isEqualTo(RequestState.AWAITING_CONFIRMATION);
		assertThat(updated.getInterpretationJson()).isNotNull();

		Optional<Interpretation> retrieved = this.orchestrator.getInterpretation(updated);
		assertThat(retrieved).isPresent();
		assertThat(retrieved.get().summary()).isEqualTo("Annual checkup");
		assertThat(retrieved.get().visitDurationMinutes()).isEqualTo(30);
	}

	@Test
	void shouldQueueWhenConsentDeclinedWithoutCallingInterpreter() {
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(this.owner);
		request.setPet(this.pet);
		request.setAiConsent(false);
		request.setRawText("Annual checkup without AI");
		request.setState(RequestState.DRAFT);
		request = this.schedulingRequests.saveAndFlush(request);

		this.orchestrator.processInterpretationAsync(request.getId());

		SchedulingRequest updated = this.schedulingRequests.findById(request.getId()).orElseThrow();
		assertThat(updated.getState()).isEqualTo(RequestState.STAFF_QUEUED);
		assertThat(updated.getQueueReason()).isEqualTo(QueueReason.CONSENT_DECLINED);
		assertThat(updated.getQueuedAt()).isNotNull();
	}

	@Test
	void shouldQueueWhenAiUnavailable() {
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(this.owner);
		request.setPet(this.pet);
		request.setAiConsent(true);
		request.setRawText("Vaccination next Tuesday");
		request.setState(RequestState.DRAFT);
		request = this.schedulingRequests.saveAndFlush(request);

		when(this.interpreter.interpret(anyString())).thenReturn(Optional.empty());

		this.orchestrator.processInterpretationAsync(request.getId());

		SchedulingRequest updated = this.schedulingRequests.findById(request.getId()).orElseThrow();
		assertThat(updated.getState()).isEqualTo(RequestState.STAFF_QUEUED);
		assertThat(updated.getQueueReason()).isEqualTo(QueueReason.AI_UNAVAILABLE);
	}

	@Test
	void shouldQueueEmergencyDirectly() {
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(this.owner);
		request.setPet(this.pet);
		request.setAiConsent(true);
		request.setRawText("Pet is bleeding heavily and cannot breathe!");
		request.setState(RequestState.DRAFT);
		request = this.schedulingRequests.saveAndFlush(request);

		Interpretation emergency = new Interpretation("Severe bleeding and breathing difficulty", 60, CareType.GENERAL,
				null, UrgencyLevel.EMERGENCY, List.of(), List.of(), List.of(), null, 0.99);
		when(this.interpreter.interpret(anyString())).thenReturn(Optional.of(emergency));

		this.orchestrator.processInterpretationAsync(request.getId());

		SchedulingRequest updated = this.schedulingRequests.findById(request.getId()).orElseThrow();
		assertThat(updated.getState()).isEqualTo(RequestState.STAFF_QUEUED);
		assertThat(updated.getQueueReason()).isEqualTo(QueueReason.EMERGENCY);
	}

	@Test
	void shouldQueueWhenNoSpecialtyVetExists() {
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(this.owner);
		request.setPet(this.pet);
		request.setAiConsent(true);
		request.setRawText("Need rare neurology specialist");
		request.setState(RequestState.SUGGESTING);
		Interpretation interpretation = new Interpretation("Neurology exam", 45, CareType.SPECIALTY, "neurology",
				UrgencyLevel.ROUTINE, List.of(), List.of(), List.of(), null, 0.95);
		try {
			request.setInterpretationJson(this.objectMapper.writeValueAsString(interpretation));
		}
		catch (Exception ignored) {
		}
		request = this.schedulingRequests.saveAndFlush(request);

		this.orchestrator.processSolveAsync(request.getId());

		SchedulingRequest updated = this.schedulingRequests.findById(request.getId()).orElseThrow();
		assertThat(updated.getState()).isEqualTo(RequestState.STAFF_QUEUED);
		assertThat(updated.getQueueReason()).isEqualTo(QueueReason.NO_SPECIALTY_VET);
	}

	@Test
	void shouldQueueWhenSuggestionsExhausted() {
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(this.owner);
		request.setPet(this.pet);
		request.setAiConsent(true);
		request.setRawText("Checkup");
		request.setState(RequestState.SUGGESTING);
		Interpretation interpretation = new Interpretation("Checkup", 30, CareType.GENERAL, null, UrgencyLevel.ROUTINE,
				List.of(), List.of(), List.of(), null, 0.95);
		try {
			request.setInterpretationJson(this.objectMapper.writeValueAsString(interpretation));
		}
		catch (Exception ignored) {
		}
		request = this.schedulingRequests.saveAndFlush(request);

		when(this.solverService.findBestSlot(any(), any())).thenReturn(Optional.empty());

		this.orchestrator.processSolveAsync(request.getId());

		SchedulingRequest updated = this.schedulingRequests.findById(request.getId()).orElseThrow();
		assertThat(updated.getState()).isEqualTo(RequestState.STAFF_QUEUED);
		assertThat(updated.getQueueReason()).isEqualTo(QueueReason.SUGGESTIONS_EXHAUSTED);
	}

	@Test
	void shouldQueueWhenSolverThrowsException() {
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(this.owner);
		request.setPet(this.pet);
		request.setAiConsent(true);
		request.setRawText("Checkup");
		request.setState(RequestState.SUGGESTING);
		Interpretation interpretation = new Interpretation("Checkup", 30, CareType.GENERAL, null, UrgencyLevel.ROUTINE,
				List.of(), List.of(), List.of(), null, 0.95);
		try {
			request.setInterpretationJson(this.objectMapper.writeValueAsString(interpretation));
		}
		catch (Exception ignored) {
		}
		request = this.schedulingRequests.saveAndFlush(request);

		when(this.solverService.findBestSlot(any(), any())).thenThrow(new IllegalStateException("Solver timeout"));

		this.orchestrator.processSolveAsync(request.getId());

		SchedulingRequest updated = this.schedulingRequests.findById(request.getId()).orElseThrow();
		assertThat(updated.getState()).isEqualTo(RequestState.STAFF_QUEUED);
		assertThat(updated.getQueueReason()).isEqualTo(QueueReason.SOLVER_UNAVAILABLE);
	}

	@Test
	void shouldPlaceHoldWhenSolverFindsSlot() {
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(this.owner);
		request.setPet(this.pet);
		request.setAiConsent(true);
		request.setRawText("Checkup");
		request.setState(RequestState.SUGGESTING);
		Interpretation interpretation = new Interpretation("Checkup", 30, CareType.GENERAL, null, UrgencyLevel.ROUTINE,
				List.of(), List.of(), List.of(), null, 0.95);
		try {
			request.setInterpretationJson(this.objectMapper.writeValueAsString(interpretation));
		}
		catch (Exception ignored) {
		}
		request = this.schedulingRequests.saveAndFlush(request);

		LocalDateTime start = LocalDateTime.of(2026, 9, 8, 10, 0);
		CandidateSlot slot = new CandidateSlot(1, start, 30);
		when(this.solverService.findBestSlot(any(), any())).thenReturn(Optional.of(slot));
		when(this.availabilityService.getClinicSettings()).thenReturn(new ClinicSettings());

		this.orchestrator.processSolveAsync(request.getId());

		verify(this.holdService).placeHold(any(SchedulingRequest.class), any(Vet.class), eq(start),
				eq(start.plusMinutes(30)), any(Duration.class));
	}

}
