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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.samples.petclinic.scheduling.model.AuditEventType;
import org.springframework.samples.petclinic.scheduling.model.ClinicSettings;
import org.springframework.samples.petclinic.scheduling.model.OperationStatus;
import org.springframework.samples.petclinic.scheduling.model.OperationToken;
import org.springframework.samples.petclinic.scheduling.model.RequestStatus;
import org.springframework.samples.petclinic.scheduling.model.Reservation;
import org.springframework.samples.petclinic.scheduling.model.ReservationStatus;
import org.springframework.samples.petclinic.scheduling.model.ReservationType;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.repository.OperationTokenRepository;
import org.springframework.samples.petclinic.scheduling.repository.ReservationRepository;
import org.springframework.samples.petclinic.scheduling.repository.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.service.AuditService;
import org.springframework.samples.petclinic.scheduling.service.ClinicSettingsService;
import org.springframework.samples.petclinic.scheduling.service.LifecycleProcessor;
import org.springframework.samples.petclinic.scheduling.service.LockCoordinator;
import org.springframework.samples.petclinic.scheduling.service.SchedulingTimeService;
import org.springframework.samples.petclinic.scheduling.service.StartupRecoveryRunner;

@ExtendWith(MockitoExtension.class)
class StartupRecoveryRunnerTests {

	@Mock
	private LifecycleProcessor lifecycleProcessor;

	@Mock
	private SchedulingRequestRepository schedulingRequestRepository;

	@Mock
	private OperationTokenRepository operationTokenRepository;

	@Mock
	private ReservationRepository reservationRepository;

	@Mock
	private LockCoordinator lockCoordinator;

	@Mock
	private AuditService auditService;

	@Mock
	private ClinicSettingsService clinicSettingsService;

	private SchedulingTimeService timeService;

	private StartupRecoveryRunner recoveryRunner;

	private Instant fixedInstant;

	@BeforeEach
	void setUp() {
		fixedInstant = Instant.parse("2026-08-27T10:00:00Z");
		Clock clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));
		timeService = new SchedulingTimeService(clock, ZoneId.of("UTC"));
		recoveryRunner = new StartupRecoveryRunner(lifecycleProcessor, schedulingRequestRepository,
				operationTokenRepository, reservationRepository, lockCoordinator, auditService, clinicSettingsService,
				timeService);
	}

	@Test
	void testRecoverInterruptedInterpretingRequest() {
		SchedulingRequest request = new SchedulingRequest();
		request.setId(50);
		request.setStatus(RequestStatus.INTERPRETING);

		OperationToken token = new OperationToken();
		token.setId(501);
		token.setRequest(request);
		token.setStatus(OperationStatus.DISPATCHED);

		when(schedulingRequestRepository.findById(50)).thenReturn(Optional.of(request));
		when(operationTokenRepository.findByRequestId(50)).thenReturn(List.of(token));
		when(reservationRepository.findByRequestId(50)).thenReturn(List.of());
		when(clinicSettingsService.getSettings()).thenReturn(new ClinicSettings());

		recoveryRunner.recoverInterruptedRequest(50);

		assertThat(request.getStatus()).isEqualTo(RequestStatus.STAFF_QUEUED);
		assertThat(request.isRecoveryRequired()).isTrue();
		assertThat(request.getLastClarificationReason()).isEqualTo("STARTUP_CRASH_RECOVERY");
		assertThat(token.getStatus()).isEqualTo(OperationStatus.CANCELLED);

		verify(operationTokenRepository).save(token);
		verify(schedulingRequestRepository).save(request);
		verify(auditService).recordEvent(eq(AuditEventType.OPERATION_FAILED), eq("SYSTEM"), eq("SYSTEM"),
				eq("SCHEDULING_REQUEST"), eq("50"), eq("STARTUP_CRASH_RECOVERY"), any());
		verify(auditService).recordEvent(eq(AuditEventType.REQUEST_QUEUED), eq("SYSTEM"), eq("SYSTEM"),
				eq("SCHEDULING_REQUEST"), eq("50"), eq("RECOVERY_REQUIRED"), any());
		verify(auditService).recordEvent(eq(AuditEventType.RECOVERY_EXECUTED), eq("SYSTEM"), eq("SYSTEM"),
				eq("SCHEDULING_REQUEST"), eq("50"), eq("STARTUP_RECOVERY"), any());
	}

	@Test
	void testRecoverInterruptedMatchingRequestWithActiveHold() {
		SchedulingRequest request = new SchedulingRequest();
		request.setId(60);
		request.setStatus(RequestStatus.MATCHING);

		Reservation hold = new Reservation();
		hold.setId(601);
		hold.setRequest(request);
		hold.setReservationType(ReservationType.GUIDED_HOLD);
		hold.setStatus(ReservationStatus.ACTIVE);

		when(schedulingRequestRepository.findById(60)).thenReturn(Optional.of(request));
		when(operationTokenRepository.findByRequestId(60)).thenReturn(List.of());
		when(reservationRepository.findByRequestId(60)).thenReturn(List.of(hold));
		when(clinicSettingsService.getSettings()).thenReturn(new ClinicSettings());

		recoveryRunner.recoverInterruptedRequest(60);

		assertThat(request.getStatus()).isEqualTo(RequestStatus.STAFF_QUEUED);
		assertThat(request.isRecoveryRequired()).isTrue();
		assertThat(hold.getStatus()).isEqualTo(ReservationStatus.CLEARED);

		verify(reservationRepository).save(hold);
		verify(schedulingRequestRepository).save(request);
	}

	@Test
	void testRunExecutesLifecycleAndInterruptedRecovery() {
		when(schedulingRequestRepository.findInterruptedRequestIds()).thenReturn(List.of(50));
		SchedulingRequest request = new SchedulingRequest();
		request.setId(50);
		request.setStatus(RequestStatus.INTERPRETING);

		when(schedulingRequestRepository.findById(50)).thenReturn(Optional.of(request));
		when(operationTokenRepository.findByRequestId(50)).thenReturn(List.of());
		when(reservationRepository.findByRequestId(50)).thenReturn(List.of());
		when(clinicSettingsService.getSettings()).thenReturn(new ClinicSettings());

		recoveryRunner.run(new DefaultApplicationArguments());

		verify(lifecycleProcessor).processOverdueDeadlines();
		verify(schedulingRequestRepository).save(request);
	}

}
