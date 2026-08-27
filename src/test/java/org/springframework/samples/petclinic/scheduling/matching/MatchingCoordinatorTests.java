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

package org.springframework.samples.petclinic.scheduling.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import ai.timefold.solver.core.api.score.BendableScore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.model.AuditEventType;
import org.springframework.samples.petclinic.scheduling.model.CareType;
import org.springframework.samples.petclinic.scheduling.model.ClinicSettings;
import org.springframework.samples.petclinic.scheduling.model.OperationStatus;
import org.springframework.samples.petclinic.scheduling.model.OperationToken;
import org.springframework.samples.petclinic.scheduling.model.OperationType;
import org.springframework.samples.petclinic.scheduling.model.RequestStatus;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.repository.ClinicSettingsRepository;
import org.springframework.samples.petclinic.scheduling.repository.OperationTokenRepository;
import org.springframework.samples.petclinic.scheduling.repository.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.service.AuditService;
import org.springframework.samples.petclinic.scheduling.service.LockCoordinator;
import org.springframework.samples.petclinic.scheduling.service.ReservationService;
import org.springframework.samples.petclinic.scheduling.service.SchedulingRequestService;
import org.springframework.samples.petclinic.scheduling.service.SchedulingTimeService;
import org.springframework.samples.petclinic.vet.Specialty;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class MatchingCoordinatorTests {

	@Mock
	private SchedulingProblemFactory problemFactory;

	@Mock
	private ReservationService reservationService;

	@Mock
	private SchedulingRequestService schedulingRequestService;

	@Mock
	private SchedulingRequestRepository schedulingRequestRepository;

	@Mock
	private OperationTokenRepository operationTokenRepository;

	@Mock
	private VetRepository vetRepository;

	@Mock
	private ClinicSettingsRepository clinicSettingsRepository;

	@Mock
	private LockCoordinator lockCoordinator;

	@Mock
	private AuditService auditService;

	private SchedulingTimeService timeService;

	private MatchingCoordinator matchingCoordinator;

	private SchedulingRequest request;

	private Owner owner;

	private Pet pet;

	private ClinicSettings settings;

	private final Instant fixedNow = Instant.parse("2026-08-28T09:00:00Z");

	@BeforeEach
	void setUp() {
		Clock fixedClock = Clock.fixed(fixedNow, ZoneId.of("UTC"));
		timeService = new SchedulingTimeService(fixedClock, ZoneId.of("UTC"));

		matchingCoordinator = new MatchingCoordinator(problemFactory, reservationService, schedulingRequestService,
				schedulingRequestRepository, operationTokenRepository, vetRepository, clinicSettingsRepository,
				lockCoordinator, timeService, auditService);

		owner = new Owner();
		owner.setId(1);
		owner.setFirstName("George");
		owner.setLastName("Franklin");

		pet = new Pet();
		pet.setId(10);
		pet.setName("Leo");

		request = new SchedulingRequest();
		request.setId(100);
		request.setOwner(owner);
		request.setPet(pet);
		request.setStatus(RequestStatus.MATCHING);
		request.setCareType(CareType.GENERAL);
		request.setDurationMinutes(30);

		settings = new ClinicSettings();
		settings.setId(1);
	}

	@AfterEach
	void tearDown() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	void testDispatchMatchingFastTracksNoSpecialtyVetFallback() {
		Specialty radiology = new Specialty();
		radiology.setId(1);
		radiology.setName("radiology");
		radiology.setActive(true);

		request.setCareType(CareType.SPECIALTY);
		request.setRequiredSpecialty(radiology);

		when(lockCoordinator.lockRequest(100)).thenReturn(request);
		when(clinicSettingsRepository.findById(1)).thenReturn(Optional.of(settings));
		when(vetRepository.findAll()).thenReturn(Collections.emptyList());

		matchingCoordinator.dispatchMatching(100, "owner");

		verify(schedulingRequestService).fallbackToStaff(eq(request), eq("NO_SPECIALTY_VET"), eq(fixedNow),
				eq(settings));
		verify(operationTokenRepository, never()).save(any());
	}

	@Test
	void testDispatchMatchingCreatesOperationToken() {
		when(lockCoordinator.lockRequest(100)).thenReturn(request);
		when(clinicSettingsRepository.findById(1)).thenReturn(Optional.of(settings));

		matchingCoordinator.dispatchMatching(100, "owner");

		ArgumentCaptor<OperationToken> tokenCaptor = ArgumentCaptor.forClass(OperationToken.class);
		verify(operationTokenRepository).save(tokenCaptor.capture());

		OperationToken token = tokenCaptor.getValue();
		assertThat(token.getOperationType()).isEqualTo(OperationType.SOLVER_MATCHING);
		assertThat(token.getStatus()).isEqualTo(OperationStatus.DISPATCHED);
		assertThat(token.getDispatchedAt()).isEqualTo(fixedNow);
		assertThat(token.getDeadline()).isEqualTo(fixedNow.plusSeconds(2));
	}

	@Test
	void testNonMatchingRequestDoesNotDispatch() {
		request.setStatus(RequestStatus.CANCELLED);
		when(lockCoordinator.lockRequest(100)).thenReturn(request);

		matchingCoordinator.dispatchMatching(100, "owner");

		verify(operationTokenRepository, never()).save(any());
	}

	@Test
	void testDispatchMatchingDefersSolverSubmissionUntilAfterCommit() throws Exception {
		when(lockCoordinator.lockRequest(100)).thenReturn(request);
		when(clinicSettingsRepository.findById(1)).thenReturn(Optional.of(settings));
		when(operationTokenRepository.findByToken(anyString())).thenReturn(Optional.empty());

		TransactionSynchronizationManager.initSynchronization();

		matchingCoordinator.dispatchMatching(100, "owner");

		// While the transaction has not committed, the async solver task must not have
		// been submitted yet (Dec-3: Post-Commit Operation Token).
		Thread.sleep(200);
		verify(operationTokenRepository, never()).findByToken(anyString());

		TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());

		verify(operationTokenRepository, timeout(2000)).findByToken(anyString());
	}

	@Test
	void testRunSolverTaskDeadlineExceededEscalatesToSolverUnavailable() {
		OperationToken token = new OperationToken();
		token.setToken("tok-deadline");
		token.setStatus(OperationStatus.DISPATCHED);
		token.setDeadline(fixedNow.minusSeconds(1));

		when(operationTokenRepository.findByToken("tok-deadline")).thenReturn(Optional.of(token));
		when(clinicSettingsRepository.findById(1)).thenReturn(Optional.of(settings));
		when(schedulingRequestService.fallbackToStaffIfMatching(eq(100), eq("SOLVER_UNAVAILABLE"), eq(fixedNow),
				eq(settings)))
			.thenReturn(true);

		matchingCoordinator.runSolverTask("tok-deadline", 100, "owner");

		assertThat(token.getStatus()).isEqualTo(OperationStatus.TIMED_OUT);
		verify(operationTokenRepository).save(token);
		verify(auditService).recordEvent(eq(AuditEventType.OPERATION_FAILED), eq("solver"), eq("SYSTEM"),
				eq("SCHEDULING_REQUEST"), eq("100"), eq("SOLVER_UNAVAILABLE"), any());
	}

	@Test
	void testRunSolverTaskSolverExceptionEscalatesToSolverUnavailable() {
		OperationToken token = new OperationToken();
		token.setToken("tok-exception");
		token.setStatus(OperationStatus.DISPATCHED);
		token.setDeadline(fixedNow.plusSeconds(2));

		when(operationTokenRepository.findByToken("tok-exception")).thenReturn(Optional.of(token));
		when(schedulingRequestRepository.findWithDetailsById(100)).thenReturn(Optional.of(request));
		when(clinicSettingsRepository.findById(1)).thenReturn(Optional.of(settings));
		when(problemFactory.createProblem(eq(request), eq(settings), eq(fixedNow)))
			.thenThrow(new IllegalStateException("boom"));
		when(schedulingRequestService.fallbackToStaffIfMatching(eq(100), eq("SOLVER_UNAVAILABLE"), eq(fixedNow),
				eq(settings)))
			.thenReturn(true);

		matchingCoordinator.runSolverTask("tok-exception", 100, "owner");

		verify(auditService).recordEvent(eq(AuditEventType.OPERATION_FAILED), eq("solver"), eq("SYSTEM"),
				eq("SCHEDULING_REQUEST"), eq("100"), eq("SOLVER_UNAVAILABLE"), eq(Map.of("details", "boom")));
	}

	@Test
	void testRunSolverTaskNoFeasibleCandidateEscalatesToSuggestionsExhausted() {
		OperationToken token = new OperationToken();
		token.setToken("tok-exhausted");
		token.setStatus(OperationStatus.DISPATCHED);
		token.setDeadline(fixedNow.plusSeconds(2));

		when(operationTokenRepository.findByToken("tok-exhausted")).thenReturn(Optional.of(token));
		when(schedulingRequestRepository.findWithDetailsById(100)).thenReturn(Optional.of(request));
		when(clinicSettingsRepository.findById(1)).thenReturn(Optional.of(settings));

		// Only an infeasible candidate (invalid DST offset) is available, so no
		// candidate can satisfy the hard constraints.
		CandidateSlot infeasible = new CandidateSlot(1, 1, 1, fixedNow, fixedNow.plusSeconds(1800), false, true, true,
				true, false, true, true, false, false, false, false, 0);
		ProposedAssignment assignment = new ProposedAssignment("assignment-1");
		SchedulingRequestFact requestFact = new SchedulingRequestFact(100, 1, 1, CareType.GENERAL, null, null, 30,
				fixedNow, fixedNow.plusSeconds(86400), null, null);
		SchedulingProblem problem = new SchedulingProblem(assignment, List.of(infeasible), requestFact, List.of(),
				List.of());

		when(problemFactory.createProblem(eq(request), eq(settings), eq(fixedNow))).thenReturn(problem);
		when(schedulingRequestService.fallbackToStaffIfMatching(eq(100), eq("SUGGESTIONS_EXHAUSTED"), eq(fixedNow),
				eq(settings)))
			.thenReturn(true);

		matchingCoordinator.runSolverTask("tok-exhausted", 100, "owner");

		assertThat(token.getStatus()).isEqualTo(OperationStatus.COMPLETED);
		verify(auditService).recordEvent(eq(AuditEventType.REQUEST_EXHAUSTED), eq("solver"), eq("SYSTEM"),
				eq("SCHEDULING_REQUEST"), eq("100"), eq("SUGGESTIONS_EXHAUSTED"), any());
	}

	@Test
	void testRunSolverTaskSuccessfulCandidateCommitsGuidedHold() {
		OperationToken token = new OperationToken();
		token.setToken("tok-success");
		token.setStatus(OperationStatus.DISPATCHED);
		token.setDeadline(fixedNow.plusSeconds(2));

		when(operationTokenRepository.findByToken("tok-success")).thenReturn(Optional.of(token));
		when(schedulingRequestRepository.findWithDetailsById(100)).thenReturn(Optional.of(request));
		when(clinicSettingsRepository.findById(1)).thenReturn(Optional.of(settings));

		Instant slotStart = fixedNow.plusSeconds(3600);
		Instant slotEnd = fixedNow.plusSeconds(5400);
		CandidateSlot feasible = new CandidateSlot(2, 1, 1, slotStart, slotEnd, true, true, true, true, false, true,
				true, false, false, false, false, 0);
		ProposedAssignment assignment = new ProposedAssignment("assignment-1");
		SchedulingRequestFact requestFact = new SchedulingRequestFact(100, 1, 1, CareType.GENERAL, null, null, 30,
				fixedNow, fixedNow.plusSeconds(86400), null, null);
		SchedulingProblem problem = new SchedulingProblem(assignment, List.of(feasible), requestFact, List.of(),
				List.of());

		when(problemFactory.createProblem(eq(request), eq(settings), eq(fixedNow))).thenReturn(problem);

		matchingCoordinator.runSolverTask("tok-success", 100, "owner");

		verify(reservationService).commitGuidedHold(eq("tok-success"), eq(100), eq(2), eq(slotStart), eq(slotEnd),
				eq("owner"));
	}

}
