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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.samples.petclinic.clinic.AvailabilityService;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.ai.AppointmentInterpreter;
import org.springframework.samples.petclinic.scheduling.ai.CareType;
import org.springframework.samples.petclinic.scheduling.ai.Interpretation;
import org.springframework.samples.petclinic.scheduling.ai.UrgencyLevel;
import org.springframework.samples.petclinic.scheduling.model.Appointment;
import org.springframework.samples.petclinic.scheduling.model.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.model.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.model.Hold;
import org.springframework.samples.petclinic.scheduling.model.HoldRepository;
import org.springframework.samples.petclinic.scheduling.model.HoldStatus;
import org.springframework.samples.petclinic.scheduling.model.OccupancyType;
import org.springframework.samples.petclinic.scheduling.model.RequestExclusion;
import org.springframework.samples.petclinic.scheduling.model.RequestExclusionRepository;
import org.springframework.samples.petclinic.scheduling.model.RequestState;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.model.SlotOccupancy;
import org.springframework.samples.petclinic.scheduling.model.SlotOccupancyRepository;
import org.springframework.samples.petclinic.scheduling.solver.AppointmentSolverService;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import tools.jackson.databind.ObjectMapper;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({ AsyncConfig.class, HoldService.class, BookingService.class, AvailabilityService.class, ObjectMapper.class })
class SchedulingEndToEndFlowTests {

	@Autowired
	private SchedulingRequestRepository schedulingRequests;

	@Autowired
	private OwnerRepository owners;

	@Autowired
	private VetRepository vets;

	@Autowired
	private HoldRepository holds;

	@Autowired
	private SlotOccupancyRepository slotOccupancies;

	@Autowired
	private AppointmentRepository appointments;

	@Autowired
	private RequestExclusionRepository requestExclusions;

	@Autowired
	private HoldService holdService;

	@Autowired
	private BookingService bookingService;

	@Autowired
	private ObjectMapper objectMapper;

	private AppointmentInterpreter interpreter;

	private AppointmentSolverService solverService;

	private AvailabilityService availabilityService;

	private SchedulingOrchestrator orchestrator;

	private Owner owner;

	private Pet pet;

	private Vet vet;

	@BeforeEach
	void setUp() {
		this.interpreter = mock(AppointmentInterpreter.class);
		this.solverService = mock(AppointmentSolverService.class);
		this.availabilityService = mock(AvailabilityService.class);
		this.orchestrator = new SchedulingOrchestrator(this.schedulingRequests, this.interpreter, this.solverService,
				this.holdService, this.vets, this.availabilityService, this.objectMapper);
		this.owner = this.owners.findById(1).orElseThrow();
		this.pet = this.owner.getPets().get(0);
		this.vet = this.vets.findById(1).orElseThrow();
	}

	@Test
	void shouldExecuteCompleteSchedulingSpineSuccessfully() {
		// 1. Owner creates free-text request with AI consent
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(this.owner);
		request.setPet(this.pet);
		request.setRawText("My dog needs an ear exam next Monday morning");
		request.setAiConsent(true);
		request.setState(RequestState.DRAFT);
		request = this.schedulingRequests.saveAndFlush(request);

		assertThat(request.getId()).isNotNull();
		assertThat(request.getActivePetKey()).isEqualTo(this.pet.getId());

		// 2. Async interpretation runs
		Interpretation interpretation = new Interpretation("Ear exam", 30, CareType.GENERAL, null, UrgencyLevel.ROUTINE,
				List.of(), List.of(), List.of(), null, 0.95);
		when(this.interpreter.interpret(anyString())).thenReturn(Optional.of(interpretation));

		this.orchestrator.processInterpretationAsync(request.getId());

		SchedulingRequest interpreted = this.schedulingRequests.findById(request.getId()).orElseThrow();
		assertThat(interpreted.getState()).isEqualTo(RequestState.AWAITING_CONFIRMATION);
		assertThat(interpreted.getInterpretationJson()).isNotNull();

		// 3. Owner confirms interpretation -> transitions to SUGGESTING
		interpreted.transitionTo(RequestState.SUGGESTING, null);
		this.schedulingRequests.saveAndFlush(interpreted);

		// 4. Solver determines slot and places hold -> transitions to SLOT_HELD
		LocalDateTime start = LocalDateTime.of(2026, 9, 7, 9, 0);
		LocalDateTime end = LocalDateTime.of(2026, 9, 7, 9, 30);
		Hold hold = this.holdService.placeHold(interpreted, this.vet, start, end, Duration.ofMinutes(10));

		assertThat(hold.getStatus()).isEqualTo(HoldStatus.ACTIVE);
		assertThat(this.slotOccupancies.findByVetIdAndStartTime(this.vet.getId(), start)).isPresent();

		SchedulingRequest heldRequest = this.schedulingRequests.findById(request.getId()).orElseThrow();
		assertThat(heldRequest.getState()).isEqualTo(RequestState.SLOT_HELD);

		// 5. Owner accepts before expiry -> Appointment created (BOOKED) and CONFIRMED
		Appointment appointment = this.bookingService.acceptHold(request.getId());

		assertThat(appointment).isNotNull();
		assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
		assertThat(appointment.getVet().getId()).isEqualTo(this.vet.getId());
		assertThat(appointment.getStartTime()).isEqualTo(start);

		Hold consumedHold = this.holds.findById(hold.getId()).orElseThrow();
		assertThat(consumedHold.getStatus()).isEqualTo(HoldStatus.CONSUMED);

		SlotOccupancy occupancy = this.slotOccupancies.findByVetIdAndStartTime(this.vet.getId(), start).orElseThrow();
		assertThat(occupancy.getOccupancyType()).isEqualTo(OccupancyType.APPOINTMENT);
		assertThat(occupancy.getReferenceId()).isEqualTo(appointment.getId());

		SchedulingRequest confirmed = this.schedulingRequests.findById(request.getId()).orElseThrow();
		assertThat(confirmed.getState()).isEqualTo(RequestState.CONFIRMED);
		assertThat(confirmed.getActivePetKey()).isNull();
	}

	@Test
	void shouldEnforceSlotExclusivityAtDatabaseLevel() {
		SchedulingRequest request1 = new SchedulingRequest();
		request1.setOwner(this.owner);
		request1.setPet(this.pet);
		request1.setState(RequestState.SUGGESTING);
		request1 = this.schedulingRequests.saveAndFlush(request1);

		Owner owner2 = this.owners.findById(2).orElseThrow();
		Pet pet2 = owner2.getPets().get(0);
		SchedulingRequest request2 = new SchedulingRequest();
		request2.setOwner(owner2);
		request2.setPet(pet2);
		request2.setState(RequestState.SUGGESTING);
		request2 = this.schedulingRequests.saveAndFlush(request2);

		LocalDateTime start = LocalDateTime.of(2026, 9, 8, 14, 0);
		LocalDateTime end = LocalDateTime.of(2026, 9, 8, 14, 30);

		// Hold 1 succeeds
		this.holdService.placeHold(request1, this.vet, start, end, Duration.ofMinutes(10));

		// Concurrent hold 2 fails at DB constraint level
		final SchedulingRequest r2 = request2;
		assertThatThrownBy(() -> this.holdService.placeHold(r2, this.vet, start, end, Duration.ofMinutes(10)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void shouldRefuseAcceptWhenHoldHasExpired() {
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(this.owner);
		request.setPet(this.pet);
		request.setState(RequestState.SUGGESTING);
		request = this.schedulingRequests.saveAndFlush(request);

		LocalDateTime start = LocalDateTime.of(2026, 9, 8, 15, 0);
		LocalDateTime end = LocalDateTime.of(2026, 9, 8, 15, 30);

		Hold hold = this.holdService.placeHold(request, this.vet, start, end, Duration.ofMinutes(10));
		hold.setExpiresAt(Instant.now().minusSeconds(10));
		this.holds.saveAndFlush(hold);

		final Integer reqId = request.getId();
		assertThatThrownBy(() -> this.bookingService.acceptHold(reqId)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("expired");

		assertThat(this.slotOccupancies.findByVetIdAndStartTime(this.vet.getId(), start)).isEmpty();
		SchedulingRequest reloaded = this.schedulingRequests.findById(reqId).orElseThrow();
		assertThat(reloaded.getState()).isEqualTo(RequestState.SUGGESTING);
	}

	@Test
	void shouldRejectAndExcludeSlotThenTransitionToSuggesting() {
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(this.owner);
		request.setPet(this.pet);
		request.setState(RequestState.SUGGESTING);
		request = this.schedulingRequests.saveAndFlush(request);

		LocalDateTime start = LocalDateTime.of(2026, 9, 8, 10, 0);
		LocalDateTime end = LocalDateTime.of(2026, 9, 8, 10, 30);

		Hold hold = this.holdService.placeHold(request, this.vet, start, end, Duration.ofMinutes(10));
		assertThat(request.getState()).isEqualTo(RequestState.SLOT_HELD);

		// Owner rejects suggested slot (AC-42)
		this.holdService.recordExclusion(request, hold.getVet(), hold.getStartTime());
		this.holdService.releaseHold(hold);
		request.transitionTo(RequestState.SUGGESTING, null);
		this.schedulingRequests.saveAndFlush(request);

		// Verify hold released and exclusion recorded
		assertThat(this.slotOccupancies.findByVetIdAndStartTime(this.vet.getId(), start)).isEmpty();
		Hold releasedHold = this.holds.findById(hold.getId()).orElseThrow();
		assertThat(releasedHold.getStatus()).isEqualTo(HoldStatus.RELEASED);

		List<RequestExclusion> exclusions = this.requestExclusions.findByRequestId(request.getId());
		assertThat(exclusions).hasSize(1);
		assertThat(exclusions.get(0).getVet().getId()).isEqualTo(this.vet.getId());
		assertThat(exclusions.get(0).getStartTime()).isEqualTo(start);

		SchedulingRequest reloaded = this.schedulingRequests.findById(request.getId()).orElseThrow();
		assertThat(reloaded.getState()).isEqualTo(RequestState.SUGGESTING);
	}

	@Test
	void shouldCancelActiveRequestAndAllowNewRequestForPet() {
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(this.owner);
		request.setPet(this.pet);
		request.setState(RequestState.SUGGESTING);
		request = this.schedulingRequests.saveAndFlush(request);

		LocalDateTime start = LocalDateTime.of(2026, 9, 8, 11, 0);
		LocalDateTime end = LocalDateTime.of(2026, 9, 8, 11, 30);
		Hold hold = this.holdService.placeHold(request, this.vet, start, end, Duration.ofMinutes(10));

		assertThat(request.getActivePetKey()).isEqualTo(this.pet.getId());

		// Owner cancels request (AC-43, RULE-13)
		this.holdService.releaseHold(hold);
		request.cancel();
		this.schedulingRequests.saveAndFlush(request);

		assertThat(request.getState()).isEqualTo(RequestState.CANCELLED);
		assertThat(request.getActivePetKey()).isNull();
		assertThat(this.slotOccupancies.findByVetIdAndStartTime(this.vet.getId(), start)).isEmpty();

		// Immediate new request for the same pet must succeed (RULE-2)
		SchedulingRequest newRequest = new SchedulingRequest();
		newRequest.setOwner(this.owner);
		newRequest.setPet(this.pet);
		newRequest.setState(RequestState.DRAFT);
		newRequest = this.schedulingRequests.saveAndFlush(newRequest);

		assertThat(newRequest.getId()).isNotNull();
		assertThat(newRequest.getActivePetKey()).isEqualTo(this.pet.getId());
	}

}
