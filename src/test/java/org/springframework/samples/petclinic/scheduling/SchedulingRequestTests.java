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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.PetRepository;
import org.springframework.samples.petclinic.scheduling.dto.AIInterpretationResponseDto;
import org.springframework.samples.petclinic.scheduling.exception.RateLimitExceededException;
import org.springframework.samples.petclinic.scheduling.model.CareType;
import org.springframework.samples.petclinic.scheduling.model.AvailabilityWindowKind;
import org.springframework.samples.petclinic.scheduling.model.CalendarExpressionType;
import org.springframework.samples.petclinic.scheduling.model.ClinicSettings;
import org.springframework.samples.petclinic.scheduling.model.OperationStatus;
import org.springframework.samples.petclinic.scheduling.model.OperationToken;
import org.springframework.samples.petclinic.scheduling.model.OperationType;
import org.springframework.samples.petclinic.scheduling.model.RequestStatus;
import org.springframework.samples.petclinic.scheduling.model.RequestAvailabilityWindow;
import org.springframework.samples.petclinic.scheduling.model.Reservation;
import org.springframework.samples.petclinic.scheduling.model.ReservationStatus;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.model.StaffClaim;
import org.springframework.samples.petclinic.scheduling.repository.ClinicSettingsRepository;
import org.springframework.samples.petclinic.scheduling.repository.OperationTokenRepository;
import org.springframework.samples.petclinic.scheduling.repository.RejectionRepository;
import org.springframework.samples.petclinic.scheduling.repository.RequestAvailabilityIntervalRepository;
import org.springframework.samples.petclinic.scheduling.repository.RequestAvailabilityWindowRepository;
import org.springframework.samples.petclinic.scheduling.repository.ReservationRepository;
import org.springframework.samples.petclinic.scheduling.repository.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.repository.StaffClaimRepository;
import org.springframework.samples.petclinic.scheduling.service.AuditService;
import org.springframework.samples.petclinic.scheduling.service.AvailabilityWindowResolver;
import org.springframework.samples.petclinic.scheduling.service.LockCoordinator;
import org.springframework.samples.petclinic.scheduling.service.SchedulingRequestService;
import org.springframework.samples.petclinic.scheduling.service.SchedulingTimeService;
import org.springframework.samples.petclinic.vet.Specialty;
import org.springframework.samples.petclinic.vet.SpecialtyRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchedulingRequestTests {

	@Mock
	private SchedulingRequestRepository schedulingRequestRepository;

	@Mock
	private OperationTokenRepository operationTokenRepository;

	@Mock
	private ReservationRepository reservationRepository;

	@Mock
	private RejectionRepository rejectionRepository;

	@Mock
	private StaffClaimRepository staffClaimRepository;

	@Mock
	private RequestAvailabilityWindowRepository availabilityWindowRepository;

	@Mock
	private RequestAvailabilityIntervalRepository availabilityIntervalRepository;

	@Mock
	private PetRepository petRepository;

	@Mock
	private OwnerRepository ownerRepository;

	@Mock
	private VetRepository vetRepository;

	@Mock
	private SpecialtyRepository specialtyRepository;

	@Mock
	private ClinicSettingsRepository clinicSettingsRepository;

	@Mock
	private AuditService auditService;

	@Mock
	private LockCoordinator lockCoordinator;

	@Mock
	private AvailabilityWindowResolver availabilityWindowResolver;

	private SchedulingTimeService timeService;

	private SchedulingRequestService requestService;

	private final ZoneId zoneId = ZoneId.of("UTC");

	private final Clock fixedClock = Clock.fixed(Instant.parse("2026-08-27T10:00:00Z"), zoneId);

	@BeforeEach
	void setUp() {
		timeService = new SchedulingTimeService(fixedClock, zoneId);
		requestService = new SchedulingRequestService(schedulingRequestRepository, operationTokenRepository,
				reservationRepository, rejectionRepository, staffClaimRepository, availabilityWindowRepository,
				availabilityIntervalRepository, petRepository, ownerRepository, vetRepository, specialtyRepository,
				clinicSettingsRepository, auditService, lockCoordinator, timeService, availabilityWindowResolver);
	}

	@Test
	void testCreateEnglishRequestSuccess() {
		Owner owner = new Owner();
		owner.setId(1);
		Pet pet = new Pet();
		pet.setId(10);

		when(petRepository.findByIdAndOwnerId(10, 1)).thenReturn(Optional.of(pet));
		when(ownerRepository.findById(1)).thenReturn(Optional.of(owner));
		when(schedulingRequestRepository.findByPetIdAndStatusNotIn(10,
				List.of(RequestStatus.CANCELLED, RequestStatus.CONFIRMED, RequestStatus.EXPIRED)))
			.thenReturn(Collections.emptyList());
		when(clinicSettingsRepository.findById(1)).thenReturn(Optional.of(new ClinicSettings()));
		when(schedulingRequestRepository.save(any(SchedulingRequest.class))).thenAnswer(i -> {
			SchedulingRequest r = i.getArgument(0);
			r.setId(100);
			return r;
		});

		SchedulingRequest req = requestService.createRequest(1, 10, "  Needs checkup \r\n next week  ", "en", "owner");

		assertThat(req.getStatus()).isEqualTo(RequestStatus.AWAITING_CONSENT);
		assertThat(req.getOriginalText()).isEqualTo("Needs checkup \n next week");
		assertThat(req.getLanguage()).isEqualTo("en");
	}

	@Test
	void testCreateUnsupportedLanguageRoutesToStaffQueue() {
		Owner owner = new Owner();
		owner.setId(1);
		Pet pet = new Pet();
		pet.setId(10);

		when(petRepository.findByIdAndOwnerId(10, 1)).thenReturn(Optional.of(pet));
		when(ownerRepository.findById(1)).thenReturn(Optional.of(owner));
		when(schedulingRequestRepository.findByPetIdAndStatusNotIn(10,
				List.of(RequestStatus.CANCELLED, RequestStatus.CONFIRMED, RequestStatus.EXPIRED)))
			.thenReturn(Collections.emptyList());
		when(clinicSettingsRepository.findById(1)).thenReturn(Optional.of(new ClinicSettings()));
		when(schedulingRequestRepository.save(any(SchedulingRequest.class))).thenAnswer(i -> i.getArgument(0));

		SchedulingRequest req = requestService.createRequest(1, 10, "Necesito una cita", "es", "owner");

		assertThat(req.getStatus()).isEqualTo(RequestStatus.STAFF_QUEUED);
		assertThat(req.getLastClarificationReason()).isEqualTo("UNSUPPORTED_LANGUAGE");
		assertThat(req.getFirstQueuedAt()).isNotNull();
	}

	@Test
	void testRecordConsentFalseRoutesToStaffQueue() {
		Owner owner = new Owner();
		owner.setId(1);
		SchedulingRequest req = new SchedulingRequest();
		req.setId(100);
		req.setOwner(owner);
		req.setStatus(RequestStatus.AWAITING_CONSENT);

		when(lockCoordinator.lockRequest(100)).thenReturn(req);
		when(clinicSettingsRepository.findById(1)).thenReturn(Optional.of(new ClinicSettings()));

		String token = requestService.recordConsentAndDispatchAI(100, 1, false, "owner");

		assertThat(token).isNull();
		assertThat(req.getStatus()).isEqualTo(RequestStatus.STAFF_QUEUED);
		assertThat(req.getLastClarificationReason()).isEqualTo("CONSENT_DECLINED");
	}

	@Test
	void testRecordConsentTrueDispatchesToken() {
		Owner owner = new Owner();
		owner.setId(1);
		SchedulingRequest req = new SchedulingRequest();
		req.setId(100);
		req.setOwner(owner);
		req.setStatus(RequestStatus.AWAITING_CONSENT);

		when(lockCoordinator.lockRequest(100)).thenReturn(req);
		when(clinicSettingsRepository.findById(1)).thenReturn(Optional.of(new ClinicSettings()));
		when(operationTokenRepository.countByRequestOwnerIdAndOperationTypeAndDispatchedAtAfter(any(), any(), any()))
			.thenReturn(0L);

		String token = requestService.recordConsentAndDispatchAI(100, 1, true, "owner");

		assertThat(token).isNotNull();
		assertThat(req.getStatus()).isEqualTo(RequestStatus.INTERPRETING);
		assertThat(req.getConsentGivenAt()).isNotNull();
		verify(operationTokenRepository).save(any(OperationToken.class));
	}

	@Test
	void testRecordConsentRateLimitExceeded() {
		Owner owner = new Owner();
		owner.setId(1);
		SchedulingRequest req = new SchedulingRequest();
		req.setId(100);
		req.setOwner(owner);
		req.setStatus(RequestStatus.AWAITING_CONSENT);

		when(lockCoordinator.lockRequest(100)).thenReturn(req);
		when(clinicSettingsRepository.findById(1)).thenReturn(Optional.of(new ClinicSettings()));
		when(operationTokenRepository.countByRequestOwnerIdAndOperationTypeAndDispatchedAtAfter(any(), any(), any()))
			.thenReturn(10L);

		OperationToken oldToken = new OperationToken();
		oldToken.setDispatchedAt(Instant.parse("2026-08-27T09:15:00Z"));
		when(operationTokenRepository
			.findByRequestOwnerIdAndOperationTypeAndDispatchedAtAfterOrderByDispatchedAtAsc(any(), any(), any()))
			.thenReturn(List.of(oldToken));

		assertThatThrownBy(() -> requestService.recordConsentAndDispatchAI(100, 1, true, "owner"))
			.isInstanceOf(RateLimitExceededException.class);

		// Status remains AWAITING_CONSENT
		assertThat(req.getStatus()).isEqualTo(RequestStatus.AWAITING_CONSENT);
	}

	@Test
	void testDeterministicAISuccessValidOutput() {
		OperationToken token = new OperationToken();
		token.setToken("tok-123");
		token.setStatus(OperationStatus.DISPATCHED);

		SchedulingRequest req = new SchedulingRequest();
		req.setId(100);
		req.setStatus(RequestStatus.INTERPRETING);

		when(operationTokenRepository.findByToken("tok-123")).thenReturn(Optional.of(token));
		when(lockCoordinator.lockRequest(100)).thenReturn(req);
		when(clinicSettingsRepository.findById(1)).thenReturn(Optional.of(new ClinicSettings()));

		AIInterpretationResponseDto dto = new AIInterpretationResponseDto("Routine checkup", "GENERAL", null, null, 25,
				false, Collections.emptyList(), Collections.emptyList());

		requestService.processAIInterpretationSuccess("tok-123", 100, dto);

		assertThat(req.getStatus()).isEqualTo(RequestStatus.AWAITING_CONFIRMATION);
		assertThat(req.getDurationMinutes()).isEqualTo(30);
		assertThat(token.getStatus()).isEqualTo(OperationStatus.COMPLETED);
	}

	@Test
	void aiSuccessPersistsNextWeekThursdayAfternoonAsHardAvailability() {
		OperationToken token = new OperationToken();
		token.setToken("tok-relative");
		token.setStatus(OperationStatus.DISPATCHED);
		SchedulingRequest request = new SchedulingRequest();
		request.setId(100);
		request.setStatus(RequestStatus.INTERPRETING);
		when(operationTokenRepository.findByToken("tok-relative")).thenReturn(Optional.of(token));
		when(lockCoordinator.lockRequest(100)).thenReturn(request);
		when(clinicSettingsRepository.findById(1)).thenReturn(Optional.of(new ClinicSettings()));

		AIInterpretationResponseDto.TimeWindowDto window = new AIInterpretationResponseDto.TimeWindowDto("ALLOWED",
				"RELATIVE_WEEK", null, "THURSDAY", 1, null, null, null, null, null, "AFTERNOON");
		AIInterpretationResponseDto dto = new AIInterpretationResponseDto("Running nose", "GENERAL", null, null, 30,
				false, List.of(window), List.of());

		requestService.processAIInterpretationSuccess("tok-relative", 100, dto);

		verify(availabilityWindowRepository).saveAll(argThat(saved -> {
			RequestAvailabilityWindow first = saved.iterator().next();
			return first.getKind() == AvailabilityWindowKind.ALLOWED
					&& first.getExpressionType() == CalendarExpressionType.RELATIVE_WEEK
					&& Integer.valueOf(1).equals(first.getWeekOffset())
					&& Integer.valueOf(4).equals(first.getDayOfWeek())
					&& first.getStartTime().equals(new ClinicSettings().getAfternoonStartTime())
					&& first.getEndTime().equals(new ClinicSettings().getAfternoonEndTime());
		}));
		assertThat(request.getStatus()).isEqualTo(RequestStatus.AWAITING_CONFIRMATION);
	}

	@Test
	void testDeterministicAISuccessUrgentFlagsEmergencyStaffQueue() {
		OperationToken token = new OperationToken();
		token.setToken("tok-123");
		token.setStatus(OperationStatus.DISPATCHED);

		SchedulingRequest req = new SchedulingRequest();
		req.setId(100);
		req.setStatus(RequestStatus.INTERPRETING);

		when(operationTokenRepository.findByToken("tok-123")).thenReturn(Optional.of(token));
		when(lockCoordinator.lockRequest(100)).thenReturn(req);
		when(clinicSettingsRepository.findById(1)).thenReturn(Optional.of(new ClinicSettings()));

		AIInterpretationResponseDto dto = new AIInterpretationResponseDto("Urgent bleeding", "GENERAL", null, null, 30,
				true, // urgent!
				Collections.emptyList(), Collections.emptyList());

		requestService.processAIInterpretationSuccess("tok-123", 100, dto);

		assertThat(req.getStatus()).isEqualTo(RequestStatus.STAFF_QUEUED);
		assertThat(req.isUrgent()).isTrue();
		assertThat(req.getLastClarificationReason()).isEqualTo("URGENCY");
	}

	@Test
	void testLateAIResultIgnoredIfRequestTerminal() {
		OperationToken token = new OperationToken();
		token.setToken("tok-123");
		token.setStatus(OperationStatus.CANCELLED);

		when(operationTokenRepository.findByToken("tok-123")).thenReturn(Optional.of(token));

		AIInterpretationResponseDto dto = new AIInterpretationResponseDto("Late result", "GENERAL", null, null, 30,
				false, Collections.emptyList(), Collections.emptyList());

		requestService.processAIInterpretationSuccess("tok-123", 100, dto);

		// No request was locked or modified
		verify(lockCoordinator, never()).lockRequest(any());
	}

	@Test
	void testReplaceOriginalTextInvalidatesWorkAndResetsConsent() {
		Owner owner = new Owner();
		owner.setId(1);
		SchedulingRequest req = new SchedulingRequest();
		req.setId(100);
		req.setOwner(owner);
		req.setStatus(RequestStatus.AWAITING_CONFIRMATION);
		req.setOriginalText("Old text");

		OperationToken token = new OperationToken();
		token.setStatus(OperationStatus.DISPATCHED);

		Reservation hold = new Reservation();
		hold.setStatus(ReservationStatus.ACTIVE);

		when(lockCoordinator.lockRequest(100)).thenReturn(req);
		when(operationTokenRepository.findByRequestId(100)).thenReturn(List.of(token));
		when(reservationRepository.findByRequestId(100)).thenReturn(List.of(hold));
		when(rejectionRepository.findByRequestId(100)).thenReturn(Collections.emptyList());

		requestService.replaceOriginalText(100, 1, "New replacement text", "owner");

		assertThat(req.getStatus()).isEqualTo(RequestStatus.AWAITING_CONSENT);
		assertThat(req.getOriginalText()).isEqualTo("New replacement text");
		assertThat(token.getStatus()).isEqualTo(OperationStatus.CANCELLED);
		assertThat(hold.getStatus()).isEqualTo(ReservationStatus.CLEARED);
	}

	@Test
	void testCancelRequestReleasesResourcesAndDeletesClaim() {
		Owner owner = new Owner();
		owner.setId(1);
		SchedulingRequest req = new SchedulingRequest();
		req.setId(100);
		req.setOwner(owner);
		req.setStatus(RequestStatus.STAFF_QUEUED);

		Reservation hold = new Reservation();
		hold.setStatus(ReservationStatus.ACTIVE);

		StaffClaim claim = new StaffClaim();
		claim.setRequest(req);

		when(lockCoordinator.lockRequest(100)).thenReturn(req);
		when(reservationRepository.findByRequestId(100)).thenReturn(List.of(hold));
		when(operationTokenRepository.findByRequestId(100)).thenReturn(Collections.emptyList());
		when(staffClaimRepository.findByRequestId(100)).thenReturn(Optional.of(claim));

		requestService.cancelRequest(100, 1, "owner");

		assertThat(req.getStatus()).isEqualTo(RequestStatus.CANCELLED);
		assertThat(hold.getStatus()).isEqualTo(ReservationStatus.CLEARED);
		verify(staffClaimRepository).delete(claim);
	}

	@Test
	void testConfirmInterpretationTransitionsToMatching() {
		Owner owner = new Owner();
		owner.setId(1);
		SchedulingRequest req = new SchedulingRequest();
		req.setId(100);
		req.setOwner(owner);
		req.setStatus(RequestStatus.AWAITING_CONFIRMATION);
		req.setCareType(CareType.GENERAL);
		req.setDurationMinutes(30);

		when(lockCoordinator.lockRequest(100)).thenReturn(req);
		when(clinicSettingsRepository.findById(1)).thenReturn(Optional.of(new ClinicSettings()));
		when(availabilityWindowResolver.materialize(any(SchedulingRequest.class), any(Instant.class),
				any(Instant.class)))
			.thenReturn(AvailabilityWindowResolver.MaterializationResult.SUCCESS);

		requestService.confirmInterpretation(100, 1, false, "owner");

		assertThat(req.getStatus()).isEqualTo(RequestStatus.MATCHING);
		assertThat(req.getOwnerHorizonEnd()).isNotNull();
	}

	@Test
	void confirmationRequiresClarificationWhenHardWindowIsOutsideHorizon() {
		Owner owner = new Owner();
		owner.setId(1);
		SchedulingRequest request = new SchedulingRequest();
		request.setId(100);
		request.setOwner(owner);
		request.setStatus(RequestStatus.AWAITING_CONFIRMATION);
		when(lockCoordinator.lockRequest(100)).thenReturn(request);
		when(clinicSettingsRepository.findById(1)).thenReturn(Optional.of(new ClinicSettings()));
		when(availabilityWindowResolver.materialize(any(SchedulingRequest.class), any(Instant.class),
				any(Instant.class)))
			.thenReturn(AvailabilityWindowResolver.MaterializationResult.OUTSIDE_HORIZON);

		requestService.confirmInterpretation(100, 1, false, "owner");

		assertThat(request.getStatus()).isEqualTo(RequestStatus.CLARIFICATION_REQUIRED);
		assertThat(request.getLastClarificationReason()).isEqualTo("OUTSIDE_BOOKING_HORIZON");
	}

	@Test
	void testDisputeClinicalFactsRoutesToStaffQueue() {
		Owner owner = new Owner();
		owner.setId(1);
		SchedulingRequest req = new SchedulingRequest();
		req.setId(100);
		req.setOwner(owner);
		req.setStatus(RequestStatus.AWAITING_CONFIRMATION);

		when(lockCoordinator.lockRequest(100)).thenReturn(req);
		when(clinicSettingsRepository.findById(1)).thenReturn(Optional.of(new ClinicSettings()));

		requestService.disputeClinicalFacts(100, 1, "Incorrect care type assigned", "owner");

		assertThat(req.getStatus()).isEqualTo(RequestStatus.STAFF_QUEUED);
		assertThat(req.getLastClarificationReason()).isEqualTo("CLINICAL_DISPUTE");
	}

	@Test
	void testEditConfirmedFactsResetsToAwaitingConfirmation() {
		Owner owner = new Owner();
		owner.setId(1);
		SchedulingRequest req = new SchedulingRequest();
		req.setId(100);
		req.setOwner(owner);
		req.setStatus(RequestStatus.MATCHING);

		Reservation hold = new Reservation();
		hold.setStatus(ReservationStatus.ACTIVE);

		when(lockCoordinator.lockRequest(100)).thenReturn(req);
		when(reservationRepository.findByRequestId(100)).thenReturn(List.of(hold));
		when(rejectionRepository.findByRequestId(100)).thenReturn(Collections.emptyList());

		requestService.editConfirmedFacts(100, 1, null, 45, null, null, "owner");

		assertThat(req.getStatus()).isEqualTo(RequestStatus.AWAITING_CONFIRMATION);
		assertThat(req.getDurationMinutes()).isEqualTo(45);
		assertThat(hold.getStatus()).isEqualTo(ReservationStatus.CLEARED);
	}

	@Test
	void testExpiredDeadlineResultsInStaffFallback() {
		OperationToken token = new OperationToken();
		token.setToken("tok-expired");
		token.setStatus(OperationStatus.DISPATCHED);
		token.setDeadline(fixedClock.instant().minusSeconds(10));

		SchedulingRequest req = new SchedulingRequest();
		req.setId(100);
		req.setStatus(RequestStatus.INTERPRETING);

		when(operationTokenRepository.findByToken("tok-expired")).thenReturn(Optional.of(token));
		when(lockCoordinator.lockRequest(100)).thenReturn(req);
		when(clinicSettingsRepository.findById(1)).thenReturn(Optional.of(new ClinicSettings()));

		AIInterpretationResponseDto dto = new AIInterpretationResponseDto("Late result", "GENERAL", null, null, 30,
				false, Collections.emptyList(), Collections.emptyList());

		requestService.processAIInterpretationSuccess("tok-expired", 100, dto);

		assertThat(req.getStatus()).isEqualTo(RequestStatus.STAFF_QUEUED);
		assertThat(req.getLastClarificationReason()).isEqualTo("AI_UNAVAILABLE");
		assertThat(token.getStatus()).isEqualTo(OperationStatus.TIMED_OUT);
	}

}
