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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.scheduling.model.AuditEventType;
import org.springframework.samples.petclinic.scheduling.model.ClinicSettings;
import org.springframework.samples.petclinic.scheduling.model.RequestStatus;
import org.springframework.samples.petclinic.scheduling.model.Reservation;
import org.springframework.samples.petclinic.scheduling.model.ReservationStatus;
import org.springframework.samples.petclinic.scheduling.model.ReservationType;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.repository.ReservationRepository;
import org.springframework.samples.petclinic.scheduling.repository.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.repository.StaffClaimRepository;
import org.springframework.samples.petclinic.scheduling.service.AuditService;
import org.springframework.samples.petclinic.scheduling.service.ClinicSettingsService;
import org.springframework.samples.petclinic.scheduling.service.LifecycleProcessor;
import org.springframework.samples.petclinic.scheduling.service.LockCoordinator;
import org.springframework.samples.petclinic.scheduling.service.NotificationService;
import org.springframework.samples.petclinic.scheduling.service.RetentionService;
import org.springframework.samples.petclinic.scheduling.service.SchedulingTimeService;
import org.springframework.samples.petclinic.security.Account;
import org.springframework.samples.petclinic.security.AccountRepository;

@ExtendWith(MockitoExtension.class)
class LifecycleProcessorTests {

	@Mock
	private RetentionService retentionService;

	@Mock
	private SchedulingRequestRepository schedulingRequestRepository;

	@Mock
	private ReservationRepository reservationRepository;

	@Mock
	private StaffClaimRepository staffClaimRepository;

	@Mock
	private AccountRepository accountRepository;

	@Mock
	private LockCoordinator lockCoordinator;

	@Mock
	private AuditService auditService;

	@Mock
	private NotificationService notificationService;

	@Mock
	private ClinicSettingsService clinicSettingsService;

	private SchedulingTimeService timeService;

	private LifecycleProcessor lifecycleProcessor;

	private Instant fixedInstant;

	@BeforeEach
	void setUp() {
		fixedInstant = Instant.parse("2026-08-27T10:00:00Z");
		Clock clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));
		timeService = new SchedulingTimeService(clock, ZoneId.of("UTC"));
		lifecycleProcessor = new LifecycleProcessor(retentionService, schedulingRequestRepository,
				reservationRepository, staffClaimRepository, accountRepository, lockCoordinator, auditService,
				notificationService, clinicSettingsService, timeService);
	}

	@Test
	void testProcessOverdueFallbackExpiresRequest() {
		SchedulingRequest request = new SchedulingRequest();
		request.setId(20);
		request.setStatus(RequestStatus.STAFF_QUEUED);
		request.setFallbackDeadline(fixedInstant.minus(Duration.ofMinutes(10)));

		Owner owner = new Owner();
		owner.setId(1);
		request.setOwner(owner);

		when(schedulingRequestRepository.findById(20)).thenReturn(Optional.of(request));
		when(clinicSettingsService.getSettings()).thenReturn(new ClinicSettings());

		lifecycleProcessor.processOverdueFallback(20);

		assertThat(request.getStatus()).isEqualTo(RequestStatus.EXPIRED);
		assertThat(request.getRetentionDeadline()).isNotNull();

		verify(schedulingRequestRepository).save(request);
		verify(auditService).recordEvent(eq(AuditEventType.REQUEST_EXPIRED), eq("SYSTEM"), eq("SYSTEM"),
				eq("SCHEDULING_REQUEST"), eq("20"), eq("FALLBACK_DEADLINE_EXPIRED"), any());
		verify(notificationService).sendNotification(eq(owner), eq("notification.offerExpired.title"),
				eq("notification.offerExpired.message"), eq(""), eq("/scheduling/requests/20"));
	}

	@Test
	void testProcessOverdueGuidedHoldTransitionsToMatching() {
		SchedulingRequest request = new SchedulingRequest();
		request.setId(30);
		request.setStatus(RequestStatus.SLOT_HELD);

		Reservation hold = new Reservation();
		hold.setId(100);
		hold.setReservationType(ReservationType.GUIDED_HOLD);
		hold.setStatus(ReservationStatus.ACTIVE);
		hold.setExpiresAt(fixedInstant.minus(Duration.ofMinutes(1)));
		hold.setRequest(request);

		when(reservationRepository.findById(100)).thenReturn(Optional.of(hold));
		when(schedulingRequestRepository.findById(30)).thenReturn(Optional.of(request));

		lifecycleProcessor.processOverdueGuidedHold(100);

		assertThat(hold.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
		assertThat(request.getStatus()).isEqualTo(RequestStatus.MATCHING);

		verify(reservationRepository).save(hold);
		verify(schedulingRequestRepository).save(request);
		verify(auditService).recordEvent(eq(AuditEventType.HOLD_EXPIRED), eq("SYSTEM"), eq("SYSTEM"), eq("RESERVATION"),
				eq("100"), eq("HOLD_TIMEOUT"), any());
	}

	@Test
	void testProcessOverdueStaffOfferReturnsToStaffQueued() {
		SchedulingRequest request = new SchedulingRequest();
		request.setId(40);
		request.setStatus(RequestStatus.STAFF_OFFERED);
		request.setFallbackDeadline(fixedInstant.plus(Duration.ofDays(2))); // not yet
																			// expired

		Owner owner = new Owner();
		owner.setId(1);
		request.setOwner(owner);

		Reservation offer = new Reservation();
		offer.setId(200);
		offer.setReservationType(ReservationType.STAFF_OFFER);
		offer.setStatus(ReservationStatus.ACTIVE);
		offer.setExpiresAt(fixedInstant.minus(Duration.ofHours(1)));
		offer.setRequest(request);

		when(reservationRepository.findById(200)).thenReturn(Optional.of(offer));
		when(schedulingRequestRepository.findById(40)).thenReturn(Optional.of(request));

		lifecycleProcessor.processOverdueStaffOffer(200);

		assertThat(offer.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
		assertThat(request.getStatus()).isEqualTo(RequestStatus.STAFF_QUEUED);

		verify(reservationRepository).save(offer);
		verify(schedulingRequestRepository).save(request);
		verify(auditService).recordEvent(eq(AuditEventType.OFFER_EXPIRED), eq("SYSTEM"), eq("SYSTEM"),
				eq("RESERVATION"), eq("200"), eq("OFFER_TIMEOUT"), any());
		verify(notificationService).sendNotification(eq(owner), eq("notification.offerExpired.title"),
				eq("notification.offerExpired.message"), eq(""), eq("/scheduling/requests/40"));
	}

	@Test
	void testProcessOverdueAccountLockoutUnlocksAccount() {
		Account account = new Account();
		account.setId(5);
		account.setUsername("lockeduser");
		account.setLockedUntil(fixedInstant.minus(Duration.ofMinutes(5)));
		account.setFailedLoginCount(5);

		when(accountRepository.findByIdForUpdate(5)).thenReturn(Optional.of(account));

		lifecycleProcessor.processOverdueAccountLock(5);

		assertThat(account.getLockedUntil()).isNull();
		assertThat(account.getFailedLoginCount()).isEqualTo(0);

		verify(accountRepository).save(account);
		verify(auditService).recordEvent(eq(AuditEventType.ACCOUNT_UNLOCKED), eq("SYSTEM"), eq("SYSTEM"), eq("ACCOUNT"),
				eq("5"), eq("LOCK_TIMEOUT_EXPIRED"), any());
	}

}
