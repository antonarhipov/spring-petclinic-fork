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
import org.springframework.samples.petclinic.scheduling.model.Appointment;
import org.springframework.samples.petclinic.scheduling.model.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.model.BookingSource;
import org.springframework.samples.petclinic.scheduling.model.CareType;
import org.springframework.samples.petclinic.scheduling.model.ClinicSettings;
import org.springframework.samples.petclinic.scheduling.model.RequestStatus;
import org.springframework.samples.petclinic.scheduling.model.Reservation;
import org.springframework.samples.petclinic.scheduling.model.ReservationStatus;
import org.springframework.samples.petclinic.scheduling.model.ReservationType;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.model.StaffClaim;
import org.springframework.samples.petclinic.scheduling.repository.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.repository.ClinicSettingsRepository;
import org.springframework.samples.petclinic.scheduling.repository.ReservationRepository;
import org.springframework.samples.petclinic.scheduling.repository.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.repository.StaffClaimRepository;
import org.springframework.samples.petclinic.scheduling.service.AuditService;
import org.springframework.samples.petclinic.scheduling.service.LiveFeasibilityService;
import org.springframework.samples.petclinic.scheduling.service.LockCoordinator;
import org.springframework.samples.petclinic.scheduling.service.NotificationService;
import org.springframework.samples.petclinic.scheduling.service.SchedulingTimeService;
import org.springframework.samples.petclinic.scheduling.service.StaffFallbackService;
import org.springframework.samples.petclinic.vet.Specialty;
import org.springframework.samples.petclinic.vet.SpecialtyRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;

@ExtendWith(MockitoExtension.class)
class StaffFallbackServiceTests {

	@Mock
	private SchedulingRequestRepository schedulingRequestRepository;

	@Mock
	private StaffClaimRepository staffClaimRepository;

	@Mock
	private ReservationRepository reservationRepository;

	@Mock
	private AppointmentRepository appointmentRepository;

	@Mock
	private VetRepository vetRepository;

	@Mock
	private SpecialtyRepository specialtyRepository;

	@Mock
	private ClinicSettingsRepository clinicSettingsRepository;

	@Mock
	private LockCoordinator lockCoordinator;

	@Mock
	private LiveFeasibilityService liveFeasibilityService;

	@Mock
	private AuditService auditService;

	@Mock
	private NotificationService notificationService;

	private SchedulingTimeService timeService;

	private StaffFallbackService staffFallbackService;

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

		staffFallbackService = new StaffFallbackService(schedulingRequestRepository, staffClaimRepository,
				reservationRepository, appointmentRepository, vetRepository, specialtyRepository,
				clinicSettingsRepository, lockCoordinator, liveFeasibilityService, timeService, auditService,
				notificationService);

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

		request = new SchedulingRequest();
		request.setId(100);
		request.setOwner(owner);
		request.setPet(pet);
		request.setStatus(RequestStatus.STAFF_QUEUED);
		request.setCareType(CareType.GENERAL);
		request.setDurationMinutes(30);
		request.setFallbackDeadline(fixedNow.plus(Duration.ofDays(7)));

		settings = new ClinicSettings();
		settings.setId(1);
		settings.setMinDurationMinutes(15);
		settings.setMaxDurationMinutes(120);
	}

	@Test
	void testGetFallbackQueueReturnsPrioritizedList() {
		when(schedulingRequestRepository
			.findByStatusInOrderByUrgentDescFirstQueuedAtAsc(List.of(RequestStatus.STAFF_QUEUED)))
			.thenReturn(List.of(request));

		List<SchedulingRequest> queue = staffFallbackService.getFallbackQueue();

		assertThat(queue).containsExactly(request);
	}

	@Test
	void testClaimRequestUnclaimedSuccess() {
		when(lockCoordinator.lockRequest(100)).thenReturn(request);
		when(staffClaimRepository.findByRequestId(100)).thenReturn(Optional.empty());

		StaffClaim claim = staffFallbackService.claimRequest(100, "staffUser");

		assertThat(claim.getStaffUsername()).isEqualTo("staffUser");
		assertThat(claim.getClaimedAt()).isEqualTo(fixedNow);
		assertThat(claim.getLastActivityAt()).isEqualTo(fixedNow);
		assertThat(claim.getReclaimableAfter()).isEqualTo(fixedNow.plus(Duration.ofMinutes(30)));

		verify(staffClaimRepository).save(claim);
	}

	@Test
	void testClaimRequestActiveByOtherStaffThrows() {
		StaffClaim existingClaim = new StaffClaim();
		existingClaim.setId(5);
		existingClaim.setRequest(request);
		existingClaim.setStaffUsername("otherStaff");
		existingClaim.setClaimedAt(fixedNow.minus(Duration.ofMinutes(10)));
		existingClaim.setLastActivityAt(fixedNow.minus(Duration.ofMinutes(10)));
		existingClaim.setReclaimableAfter(fixedNow.plus(Duration.ofMinutes(20))); // not
																					// yet
																					// reclaimable

		when(lockCoordinator.lockRequest(100)).thenReturn(request);
		when(staffClaimRepository.findByRequestId(100)).thenReturn(Optional.of(existingClaim));

		assertThatThrownBy(() -> staffFallbackService.claimRequest(100, "newStaff"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("actively claimed by otherStaff");
	}

	@Test
	void testReclaimRequestAfter30MinutesSuccess() {
		StaffClaim existingClaim = new StaffClaim();
		existingClaim.setId(5);
		existingClaim.setRequest(request);
		existingClaim.setStaffUsername("otherStaff");
		existingClaim.setClaimedAt(fixedNow.minus(Duration.ofMinutes(40)));
		existingClaim.setLastActivityAt(fixedNow.minus(Duration.ofMinutes(35)));
		existingClaim.setReclaimableAfter(fixedNow.minus(Duration.ofMinutes(5))); // past
																					// reclaim
																					// deadline

		when(lockCoordinator.lockRequest(100)).thenReturn(request);
		when(staffClaimRepository.findByRequestId(100)).thenReturn(Optional.of(existingClaim));

		StaffClaim reclaimed = staffFallbackService.claimRequest(100, "newStaff");

		assertThat(reclaimed.getStaffUsername()).isEqualTo("newStaff");
		assertThat(reclaimed.getClaimedAt()).isEqualTo(fixedNow);
		assertThat(reclaimed.getLastActivityAt()).isEqualTo(fixedNow);
		assertThat(reclaimed.getReclaimableAfter()).isEqualTo(fixedNow.plus(Duration.ofMinutes(30)));
		verify(staffClaimRepository).save(existingClaim);
	}

	@Test
	void testReleaseClaimSuccess() {
		StaffClaim claim = new StaffClaim();
		claim.setId(5);
		claim.setRequest(request);
		claim.setStaffUsername("staffUser");

		when(lockCoordinator.lockRequest(100)).thenReturn(request);
		when(staffClaimRepository.findByRequestId(100)).thenReturn(Optional.of(claim));

		staffFallbackService.releaseClaim(100, "staffUser");

		verify(staffClaimRepository).delete(claim);
	}

	@Test
	void testUpdateStructuredInterpretationSuccess() {
		StaffClaim claim = new StaffClaim();
		claim.setId(5);
		claim.setRequest(request);
		claim.setStaffUsername("staffUser");

		when(lockCoordinator.lockRequest(100)).thenReturn(request);
		when(staffClaimRepository.findByRequestId(100)).thenReturn(Optional.of(claim));
		when(clinicSettingsRepository.findById(1)).thenReturn(Optional.of(settings));

		staffFallbackService.updateStructuredInterpretation(100, 45, CareType.GENERAL, null, null, null, null,
				"staffUser");

		assertThat(request.getDurationMinutes()).isEqualTo(45);
		verify(schedulingRequestRepository).save(request);
	}

	@Test
	void testUpdateStructuredInterpretationRejectsOutOfBoundsDurationAndPreservesPrior() {
		StaffClaim claim = new StaffClaim();
		claim.setId(5);
		claim.setRequest(request);
		claim.setStaffUsername("staffUser");

		when(lockCoordinator.lockRequest(100)).thenReturn(request);
		when(staffClaimRepository.findByRequestId(100)).thenReturn(Optional.of(claim));
		when(clinicSettingsRepository.findById(1)).thenReturn(Optional.of(settings));

		// 40 is not a multiple of 15 and would previously have been silently clamped;
		// per UC5-AC13 it must instead be rejected and the prior interpretation kept.
		assertThatThrownBy(() -> staffFallbackService.updateStructuredInterpretation(100, 40, CareType.GENERAL, null,
				null, null, null, "staffUser"))
			.isInstanceOf(IllegalArgumentException.class);

		assertThat(request.getDurationMinutes()).isEqualTo(30);
		verify(schedulingRequestRepository, never()).save(any());
	}

	@Test
	void testUpdateStructuredInterpretationRejectsDurationAboveMaximum() {
		StaffClaim claim = new StaffClaim();
		claim.setId(5);
		claim.setRequest(request);
		claim.setStaffUsername("staffUser");

		when(lockCoordinator.lockRequest(100)).thenReturn(request);
		when(staffClaimRepository.findByRequestId(100)).thenReturn(Optional.of(claim));
		when(clinicSettingsRepository.findById(1)).thenReturn(Optional.of(settings));

		assertThatThrownBy(() -> staffFallbackService.updateStructuredInterpretation(100, 135, CareType.GENERAL, null,
				null, null, null, "staffUser"))
			.isInstanceOf(IllegalArgumentException.class);

		assertThat(request.getDurationMinutes()).isEqualTo(30);
		verify(schedulingRequestRepository, never()).save(any());
	}

	@Test
	void testUpdateStructuredInterpretationRejectsInvalidWindowAndPreservesPrior() {
		StaffClaim claim = new StaffClaim();
		claim.setId(5);
		claim.setRequest(request);
		claim.setStaffUsername("staffUser");
		request.setPreferredStartWindow(null);
		request.setPreferredEndWindow(null);

		when(lockCoordinator.lockRequest(100)).thenReturn(request);
		when(staffClaimRepository.findByRequestId(100)).thenReturn(Optional.of(claim));

		Instant start = fixedNow.plus(Duration.ofHours(4));
		Instant end = fixedNow.plus(Duration.ofHours(2));

		assertThatThrownBy(() -> staffFallbackService.updateStructuredInterpretation(100, null, null, null, null, start,
				end, "staffUser"))
			.isInstanceOf(IllegalArgumentException.class);

		assertThat(request.getPreferredStartWindow()).isNull();
		assertThat(request.getPreferredEndWindow()).isNull();
		verify(schedulingRequestRepository, never()).save(any());
	}

	@Test
	void testCreateStaffOfferSuccess() {
		StaffClaim claim = new StaffClaim();
		claim.setId(5);
		claim.setRequest(request);
		claim.setStaffUsername("staffUser");

		Instant slotStart = fixedNow.plus(Duration.ofHours(2));
		Instant slotEnd = slotStart.plusSeconds(1800);

		when(lockCoordinator.lockRequest(100)).thenReturn(request);
		when(staffClaimRepository.findByRequestId(100)).thenReturn(Optional.of(claim));
		when(vetRepository.findById(1)).thenReturn(Optional.of(vet));
		when(liveFeasibilityService.checkFeasibility(eq(1), eq(1), eq(10), eq(slotStart), eq(slotEnd), any(), any(),
				any(), eq(100)))
			.thenReturn(true);
		when(reservationRepository.findByRequestId(100)).thenReturn(Collections.emptyList());

		Reservation offer = staffFallbackService.createStaffOffer(100, 1, slotStart, "staffUser");

		assertThat(offer).isNotNull();
		assertThat(offer.getReservationType()).isEqualTo(ReservationType.STAFF_OFFER);
		assertThat(offer.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
		assertThat(offer.getStartTime()).isEqualTo(slotStart);
		assertThat(offer.getExpiresAt()).isEqualTo(fixedNow.plus(Duration.ofHours(24)));

		verify(reservationRepository).save(offer);
		verify(notificationService).sendNotification(eq(owner), eq("notification.staffOffer.title"),
				eq("notification.staffOffer.message"), anyString(), anyString());
	}

	@Test
	void testCreateStaffOfferCappedByFallbackDeadline() {
		StaffClaim claim = new StaffClaim();
		claim.setId(5);
		claim.setRequest(request);
		claim.setStaffUsername("staffUser");

		// Fallback deadline is only 10 hours from now
		request.setFallbackDeadline(fixedNow.plus(Duration.ofHours(10)));

		Instant slotStart = fixedNow.plus(Duration.ofHours(2));
		Instant slotEnd = slotStart.plusSeconds(1800);

		when(lockCoordinator.lockRequest(100)).thenReturn(request);
		when(staffClaimRepository.findByRequestId(100)).thenReturn(Optional.of(claim));
		when(vetRepository.findById(1)).thenReturn(Optional.of(vet));
		when(liveFeasibilityService.checkFeasibility(eq(1), eq(1), eq(10), eq(slotStart), eq(slotEnd), any(), any(),
				any(), eq(100)))
			.thenReturn(true);
		when(reservationRepository.findByRequestId(100)).thenReturn(Collections.emptyList());

		Reservation offer = staffFallbackService.createStaffOffer(100, 1, slotStart, "staffUser");

		assertThat(offer.getExpiresAt()).isEqualTo(fixedNow.plus(Duration.ofHours(10)));
	}

	@Test
	void testRevokeStaffOfferSuccess() {
		StaffClaim claim = new StaffClaim();
		claim.setId(5);
		claim.setRequest(request);
		claim.setStaffUsername("staffUser");

		Reservation offer = new Reservation();
		offer.setId(20);
		offer.setRequest(request);
		offer.setReservationType(ReservationType.STAFF_OFFER);
		offer.setStatus(ReservationStatus.ACTIVE);

		when(staffClaimRepository.findByRequestId(100)).thenReturn(Optional.of(claim));
		when(reservationRepository.findById(20)).thenReturn(Optional.of(offer));

		staffFallbackService.revokeStaffOffer(100, 20, "staffUser");

		assertThat(offer.getStatus()).isEqualTo(ReservationStatus.REVOKED);
		verify(reservationRepository).save(offer);
	}

	@Test
	void testDirectBookSuccess() {
		StaffClaim claim = new StaffClaim();
		claim.setId(5);
		claim.setRequest(request);
		claim.setStaffUsername("staffUser");

		Instant slotStart = fixedNow.plus(Duration.ofHours(2));
		Instant slotEnd = slotStart.plusSeconds(1800);

		when(lockCoordinator.lockRequest(100)).thenReturn(request);
		when(staffClaimRepository.findByRequestId(100)).thenReturn(Optional.of(claim));
		when(vetRepository.findById(1)).thenReturn(Optional.of(vet));
		when(liveFeasibilityService.checkFeasibility(eq(1), eq(1), eq(10), eq(slotStart), eq(slotEnd), any(), any(),
				any(), eq(100)))
			.thenReturn(true);
		when(reservationRepository.findByRequestId(100)).thenReturn(Collections.emptyList());

		Appointment appt = staffFallbackService.directBook(100, 1, slotStart, "Phone booking with owner", "staffUser");

		assertThat(appt).isNotNull();
		assertThat(appt.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
		assertThat(appt.getBookingSource()).isEqualTo(BookingSource.STAFF_DIRECT);
		assertThat(appt.getOfflineReason()).isEqualTo("Phone booking with owner");
		assertThat(request.getStatus()).isEqualTo(RequestStatus.CONFIRMED);

		verify(appointmentRepository).save(appt);
		verify(staffClaimRepository).delete(claim);
		verify(notificationService).sendNotification(eq(owner), eq("notification.appointmentConfirmed.title"),
				eq("notification.appointmentConfirmed.message"), anyString(), anyString());
	}

	@Test
	void testDirectBookBlankReasonThrows() {
		assertThatThrownBy(
				() -> staffFallbackService.directBook(100, 1, fixedNow.plusSeconds(3600), "   ", "staffUser"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("reason is required");
	}

	@Test
	void testCancelRequestByStaffSuccess() {
		when(lockCoordinator.lockRequest(100)).thenReturn(request);
		when(reservationRepository.findByRequestId(100)).thenReturn(Collections.emptyList());
		when(staffClaimRepository.findByRequestId(100)).thenReturn(Optional.empty());

		staffFallbackService.cancelRequestByStaff(100, "Owner requested cancellation over phone", "staffUser");

		assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
		verify(schedulingRequestRepository).save(request);
		verify(notificationService).sendNotification(eq(owner), anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void testExpireFallbackPastDeadline() {
		request.setFallbackDeadline(fixedNow.minusSeconds(1)); // past deadline

		when(lockCoordinator.lockRequest(100)).thenReturn(request);
		when(reservationRepository.findByRequestId(100)).thenReturn(Collections.emptyList());
		when(staffClaimRepository.findByRequestId(100)).thenReturn(Optional.empty());

		staffFallbackService.expireFallback(100);

		assertThat(request.getStatus()).isEqualTo(RequestStatus.EXPIRED);
		verify(schedulingRequestRepository).save(request);
		verify(notificationService).sendNotification(eq(owner), anyString(), anyString(), anyString(), anyString());
	}

}
