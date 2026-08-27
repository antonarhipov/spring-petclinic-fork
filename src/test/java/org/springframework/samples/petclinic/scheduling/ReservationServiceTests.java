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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.dto.SuggestionDto;
import org.springframework.samples.petclinic.scheduling.model.Appointment;
import org.springframework.samples.petclinic.scheduling.model.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.model.BookingSource;
import org.springframework.samples.petclinic.scheduling.model.CareType;
import org.springframework.samples.petclinic.scheduling.model.ClinicSettings;
import org.springframework.samples.petclinic.scheduling.model.OperationStatus;
import org.springframework.samples.petclinic.scheduling.model.OperationToken;
import org.springframework.samples.petclinic.scheduling.model.OperationType;
import org.springframework.samples.petclinic.scheduling.model.Rejection;
import org.springframework.samples.petclinic.scheduling.model.RequestStatus;
import org.springframework.samples.petclinic.scheduling.model.Reservation;
import org.springframework.samples.petclinic.scheduling.model.ReservationStatus;
import org.springframework.samples.petclinic.scheduling.model.ReservationType;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.model.StaffClaim;
import org.springframework.samples.petclinic.scheduling.repository.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.repository.ClinicSettingsRepository;
import org.springframework.samples.petclinic.scheduling.repository.OperationTokenRepository;
import org.springframework.samples.petclinic.scheduling.repository.RejectionRepository;
import org.springframework.samples.petclinic.scheduling.repository.ReservationRepository;
import org.springframework.samples.petclinic.scheduling.repository.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.repository.StaffClaimRepository;
import org.springframework.samples.petclinic.scheduling.service.AuditService;
import org.springframework.samples.petclinic.scheduling.service.LiveFeasibilityService;
import org.springframework.samples.petclinic.scheduling.service.LockCoordinator;
import org.springframework.samples.petclinic.scheduling.service.NotificationService;
import org.springframework.samples.petclinic.scheduling.service.ReservationService;
import org.springframework.samples.petclinic.scheduling.service.SchedulingTimeService;
import org.springframework.samples.petclinic.vet.Specialty;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTests {

	@Mock
	private ReservationRepository reservationRepository;

	@Mock
	private SchedulingRequestRepository schedulingRequestRepository;

	@Mock
	private AppointmentRepository appointmentRepository;

	@Mock
	private RejectionRepository rejectionRepository;

	@Mock
	private StaffClaimRepository staffClaimRepository;

	@Mock
	private VetRepository vetRepository;

	@Mock
	private ClinicSettingsRepository clinicSettingsRepository;

	@Mock
	private OperationTokenRepository operationTokenRepository;

	@Mock
	private AuditService auditService;

	@Mock
	private NotificationService notificationService;

	@Mock
	private LockCoordinator lockCoordinator;

	@Mock
	private LiveFeasibilityService liveFeasibilityService;

	private SchedulingTimeService timeService;

	private ReservationService reservationService;

	private final Instant fixedNow = Instant.parse("2026-08-28T09:00:00Z");

	private Owner owner;

	private Pet pet;

	private Vet vet;

	private SchedulingRequest request;

	private ClinicSettings settings;

	@BeforeEach
	void setUp() {
		Clock fixedClock = Clock.fixed(fixedNow, ZoneId.of("UTC"));
		timeService = new SchedulingTimeService(fixedClock, ZoneId.of("UTC"));

		reservationService = new ReservationService(reservationRepository, schedulingRequestRepository,
				appointmentRepository, rejectionRepository, staffClaimRepository, vetRepository,
				clinicSettingsRepository, operationTokenRepository, auditService, notificationService, lockCoordinator,
				liveFeasibilityService, timeService);

		owner = new Owner();
		owner.setId(1);
		owner.setFirstName("George");
		owner.setLastName("Franklin");

		pet = new Pet();
		pet.setId(10);
		pet.setName("Leo");

		vet = new Vet();
		vet.setId(1);
		vet.setFirstName("James");
		vet.setLastName("Carter");
		vet.setActive(true);

		Specialty radiology = new Specialty();
		radiology.setId(1);
		radiology.setName("radiology");
		radiology.setActive(true);
		vet.addSpecialty(radiology);

		request = new SchedulingRequest();
		request.setId(100);
		request.setOwner(owner);
		request.setPet(pet);
		request.setStatus(RequestStatus.MATCHING);
		request.setCareType(CareType.GENERAL);
		request.setDurationMinutes(30);

		settings = new ClinicSettings();
		settings.setId(1);
		settings.setGuidedHoldDurationMinutes(5);
	}

	@Test
	void testCommitGuidedHoldSuccess() {
		String tokenStr = "token-123";
		OperationToken token = new OperationToken();
		token.setToken(tokenStr);
		token.setStatus(OperationStatus.DISPATCHED);
		token.setDeadline(fixedNow.plusSeconds(2));

		Instant slotStart = fixedNow.plusSeconds(3600);
		Instant slotEnd = slotStart.plusSeconds(1800);

		when(operationTokenRepository.findByToken(tokenStr)).thenReturn(Optional.of(token));
		when(schedulingRequestRepository.findById(100)).thenReturn(Optional.of(request));
		when(vetRepository.findById(1)).thenReturn(Optional.of(vet));
		when(clinicSettingsRepository.findById(1)).thenReturn(Optional.of(settings));
		when(liveFeasibilityService.checkFeasibility(eq(1), eq(1), eq(10), eq(slotStart), eq(slotEnd), any(), any(),
				any(), eq(100)))
			.thenReturn(true);
		when(reservationRepository.findByRequestId(100)).thenReturn(Collections.emptyList());

		reservationService.commitGuidedHold(tokenStr, 100, 1, slotStart, slotEnd, "owner");

		assertThat(token.getStatus()).isEqualTo(OperationStatus.COMPLETED);
		assertThat(request.getStatus()).isEqualTo(RequestStatus.SLOT_HELD);

		ArgumentCaptor<Reservation> holdCaptor = ArgumentCaptor.forClass(Reservation.class);
		verify(reservationRepository).save(holdCaptor.capture());

		Reservation hold = holdCaptor.getValue();
		assertThat(hold.getReservationType()).isEqualTo(ReservationType.GUIDED_HOLD);
		assertThat(hold.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
		assertThat(hold.getStartTime()).isEqualTo(slotStart);
		assertThat(hold.getEndTime()).isEqualTo(slotEnd);
		assertThat(hold.getExpiresAt()).isEqualTo(fixedNow.plus(Duration.ofMinutes(5)));
	}

	@Test
	void testCommitGuidedHoldFailsWhenTokenExpired() {
		String tokenStr = "token-expired";
		OperationToken token = new OperationToken();
		token.setToken(tokenStr);
		token.setStatus(OperationStatus.DISPATCHED);
		token.setDeadline(fixedNow.minusSeconds(1)); // expired

		when(operationTokenRepository.findByToken(tokenStr)).thenReturn(Optional.of(token));

		reservationService.commitGuidedHold(tokenStr, 100, 1, fixedNow.plusSeconds(3600), fixedNow.plusSeconds(5400),
				"owner");

		assertThat(token.getStatus()).isEqualTo(OperationStatus.TIMED_OUT);
		verify(reservationRepository, never()).save(any());
	}

	@Test
	void testCommitGuidedHoldConcurrencyLoserDoesNotPlaceHold() {
		String tokenStr = "token-loser";
		OperationToken token = new OperationToken();
		token.setToken(tokenStr);
		token.setStatus(OperationStatus.DISPATCHED);
		token.setDeadline(fixedNow.plusSeconds(2));

		Instant slotStart = fixedNow.plusSeconds(3600);
		Instant slotEnd = slotStart.plusSeconds(1800);

		when(operationTokenRepository.findByToken(tokenStr)).thenReturn(Optional.of(token));
		when(schedulingRequestRepository.findById(100)).thenReturn(Optional.of(request));
		when(vetRepository.findById(1)).thenReturn(Optional.of(vet));
		when(clinicSettingsRepository.findById(1)).thenReturn(Optional.of(settings));
		when(liveFeasibilityService.checkFeasibility(eq(1), eq(1), eq(10), eq(slotStart), eq(slotEnd), any(), any(),
				any(), eq(100)))
			.thenReturn(false); // slot taken

		reservationService.commitGuidedHold(tokenStr, 100, 1, slotStart, slotEnd, "owner");

		assertThat(token.getStatus()).isEqualTo(OperationStatus.COMPLETED);
		assertThat(request.getStatus()).isEqualTo(RequestStatus.MATCHING);
		verify(reservationRepository, never()).save(any());
	}

	@Test
	void testGetGuidedHoldSuggestionReturnsAllowlistedDto() {
		Reservation hold = new Reservation();
		hold.setId(50);
		hold.setRequest(request);
		hold.setOwner(owner);
		hold.setPet(pet);
		hold.setVet(vet);
		hold.setReservationType(ReservationType.GUIDED_HOLD);
		hold.setStatus(ReservationStatus.ACTIVE);
		hold.setStartTime(fixedNow.plusSeconds(3600));
		hold.setEndTime(fixedNow.plusSeconds(5400));
		hold.setExpiresAt(fixedNow.plusSeconds(300));

		when(schedulingRequestRepository.findByIdAndOwnerId(100, 1)).thenReturn(Optional.of(request));
		when(reservationRepository.findByRequestId(100)).thenReturn(List.of(hold));

		SuggestionDto dto = reservationService.getGuidedHoldSuggestion(100, 1);

		assertThat(dto).isNotNull();
		assertThat(dto.reservationId()).isEqualTo(50);
		assertThat(dto.vetName()).isEqualTo("James Carter");
		assertThat(dto.specialties()).containsExactly("radiology");
		assertThat(dto.durationMinutes()).isEqualTo(30);
	}

	@Test
	void testAcceptGuidedHoldSuccess() {
		Reservation hold = new Reservation();
		hold.setId(50);
		hold.setRequest(request);
		hold.setOwner(owner);
		hold.setPet(pet);
		hold.setVet(vet);
		hold.setReservationType(ReservationType.GUIDED_HOLD);
		hold.setStatus(ReservationStatus.ACTIVE);
		hold.setStartTime(fixedNow.plusSeconds(3600));
		hold.setEndTime(fixedNow.plusSeconds(5400));
		hold.setExpiresAt(fixedNow.plusSeconds(300));

		when(schedulingRequestRepository.findByIdAndOwnerId(100, 1)).thenReturn(Optional.of(request));
		when(reservationRepository.findById(50)).thenReturn(Optional.of(hold));
		when(schedulingRequestRepository.findById(100)).thenReturn(Optional.of(request));
		when(staffClaimRepository.findByRequestId(100)).thenReturn(Optional.empty());

		Appointment appt = reservationService.acceptGuidedHold(100, 1, 50, 0L, "george");

		assertThat(hold.getStatus()).isEqualTo(ReservationStatus.ACCEPTED);
		assertThat(request.getStatus()).isEqualTo(RequestStatus.CONFIRMED);
		assertThat(appt).isNotNull();
		assertThat(appt.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
		assertThat(appt.getBookingSource()).isEqualTo(BookingSource.GUIDED_MATCHING);
		assertThat(appt.getStartTime()).isEqualTo(hold.getStartTime());

		verify(appointmentRepository).save(any(Appointment.class));
		verify(notificationService).sendNotification(eq(owner), eq("notification.appointmentConfirmed.title"),
				eq("notification.appointmentConfirmed.message"), anyString(), anyString());
	}

	@Test
	void testAcceptGuidedHoldExpiredThrowsException() {
		Reservation hold = new Reservation();
		hold.setId(50);
		hold.setRequest(request);
		hold.setOwner(owner);
		hold.setPet(pet);
		hold.setVet(vet);
		hold.setReservationType(ReservationType.GUIDED_HOLD);
		hold.setStatus(ReservationStatus.ACTIVE);
		hold.setStartTime(fixedNow.plusSeconds(3600));
		hold.setEndTime(fixedNow.plusSeconds(5400));
		hold.setExpiresAt(fixedNow.minusSeconds(10)); // expired

		when(schedulingRequestRepository.findByIdAndOwnerId(100, 1)).thenReturn(Optional.of(request));
		when(reservationRepository.findById(50)).thenReturn(Optional.of(hold));
		when(schedulingRequestRepository.findById(100)).thenReturn(Optional.of(request));

		assertThatThrownBy(() -> reservationService.acceptGuidedHold(100, 1, 50, 0L, "george"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("expired");
	}

	@Test
	void testRejectGuidedHoldRecordsUniqueRejectionAndReturnsToMatching() {
		Reservation hold = new Reservation();
		hold.setId(50);
		hold.setRequest(request);
		hold.setOwner(owner);
		hold.setPet(pet);
		hold.setVet(vet);
		hold.setReservationType(ReservationType.GUIDED_HOLD);
		hold.setStatus(ReservationStatus.ACTIVE);
		hold.setStartTime(fixedNow.plusSeconds(3600));
		hold.setEndTime(fixedNow.plusSeconds(5400));

		when(schedulingRequestRepository.findByIdAndOwnerId(100, 1)).thenReturn(Optional.of(request));
		when(reservationRepository.findById(50)).thenReturn(Optional.of(hold));
		when(schedulingRequestRepository.findById(100)).thenReturn(Optional.of(request));
		when(rejectionRepository.findByRequestId(100)).thenReturn(Collections.emptyList());

		reservationService.rejectGuidedHold(100, 1, 50, 0L, "george");

		assertThat(hold.getStatus()).isEqualTo(ReservationStatus.REJECTED);
		assertThat(request.getStatus()).isEqualTo(RequestStatus.MATCHING);

		ArgumentCaptor<Rejection> rejCaptor = ArgumentCaptor.forClass(Rejection.class);
		verify(rejectionRepository).save(rejCaptor.capture());

		Rejection rej = rejCaptor.getValue();
		assertThat(rej.getVet().getId()).isEqualTo(vet.getId());
		assertThat(rej.getStartTime()).isEqualTo(hold.getStartTime());
		assertThat(rej.getReason()).isEqualTo("OWNER_REJECTED");
	}

}
