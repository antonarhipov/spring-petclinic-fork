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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import org.springframework.samples.petclinic.scheduling.model.RequestStatus;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.repository.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.service.AuditService;
import org.springframework.samples.petclinic.scheduling.service.LockCoordinator;
import org.springframework.samples.petclinic.scheduling.service.RetentionService;
import org.springframework.samples.petclinic.scheduling.service.SchedulingTimeService;
import org.springframework.samples.petclinic.vet.Vet;

@ExtendWith(MockitoExtension.class)
class RetentionServiceTests {

	@Mock
	private SchedulingRequestRepository schedulingRequestRepository;

	@Mock
	private LockCoordinator lockCoordinator;

	@Mock
	private AuditService auditService;

	private SchedulingTimeService timeService;

	private RetentionService retentionService;

	private Instant fixedInstant;

	@BeforeEach
	void setUp() {
		fixedInstant = Instant.parse("2026-08-27T10:00:00Z");
		Clock clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));
		timeService = new SchedulingTimeService(clock, ZoneId.of("UTC"));
		retentionService = new RetentionService(schedulingRequestRepository, lockCoordinator, auditService,
				timeService);
	}

	@Test
	void testPurgeOverdueRequestSuccessfullyClearsSensitiveFields() {
		SchedulingRequest request = new SchedulingRequest();
		request.setId(10);
		request.setStatus(RequestStatus.CONFIRMED);
		request.setOriginalText("My dog has an upset stomach since Monday.");
		request.setAiSummary("Digestive upset");
		request.setFullAiResponse("{\"duration\": 30}");
		request.setCareType(CareType.GENERAL);
		request.setDurationMinutes(30);
		request.setRetentionDeadline(fixedInstant.minus(Duration.ofHours(1))); // overdue

		Owner owner = new Owner();
		owner.setId(1);
		request.setOwner(owner);

		Pet pet = new Pet();
		pet.setId(2);
		request.setPet(pet);

		Vet vet = new Vet();
		vet.setId(3);
		request.setPreferredVet(vet);

		when(schedulingRequestRepository.findById(10)).thenReturn(Optional.of(request));

		int result = retentionService.purgeOverdueRequest(10);

		assertThat(result).isEqualTo(1);
		assertThat(request.getOriginalText()).isNull();
		assertThat(request.getAiSummary()).isNull();
		assertThat(request.getFullAiResponse()).isNull();
		assertThat(request.getPurgedAt()).isEqualTo(fixedInstant);
		assertThat(request.getStatus()).isEqualTo(RequestStatus.CONFIRMED);
		assertThat(request.getCareType()).isEqualTo(CareType.GENERAL);
		assertThat(request.getDurationMinutes()).isEqualTo(30);

		verify(schedulingRequestRepository).save(request);
		verify(auditService).recordEvent(eq(AuditEventType.DATA_PURGED), eq("SYSTEM"), eq("SYSTEM"),
				eq("SCHEDULING_REQUEST"), eq("10"), eq("RETENTION_EXPIRED"), any());
	}

	@Test
	void testPurgeSkipsWhenDeadlineInFuture() {
		SchedulingRequest request = new SchedulingRequest();
		request.setId(10);
		request.setOriginalText("Sensitive description");
		request.setRetentionDeadline(fixedInstant.plus(Duration.ofDays(10))); // future

		when(schedulingRequestRepository.findById(10)).thenReturn(Optional.of(request));

		int result = retentionService.purgeOverdueRequest(10);

		assertThat(result).isEqualTo(0);
		assertThat(request.getOriginalText()).isEqualTo("Sensitive description");
		assertThat(request.getPurgedAt()).isNull();

		verify(schedulingRequestRepository, never()).save(any());
		verify(auditService, never()).recordEvent(any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	void testPurgeSkipsWhenAlreadyPurged() {
		SchedulingRequest request = new SchedulingRequest();
		request.setId(10);
		request.setPurgedAt(fixedInstant.minus(Duration.ofDays(1)));
		request.setRetentionDeadline(fixedInstant.minus(Duration.ofDays(2)));

		when(schedulingRequestRepository.findById(10)).thenReturn(Optional.of(request));

		int result = retentionService.purgeOverdueRequest(10);

		assertThat(result).isEqualTo(0);
		verify(schedulingRequestRepository, never()).save(any());
	}

}
