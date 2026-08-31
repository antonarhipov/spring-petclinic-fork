package org.springframework.samples.petclinic.scheduling.queue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.account.AccountRepository;
import org.springframework.samples.petclinic.audit.AuditService;
import org.springframework.samples.petclinic.audit.ProtectedPayload;
import org.springframework.samples.petclinic.audit.ProtectedPayloadService;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.scheduling.interpretation.Urgency;
import org.springframework.samples.petclinic.scheduling.offer.OfferRepository;
import org.springframework.samples.petclinic.scheduling.request.OfferExclusionRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.TextRevision;
import org.springframework.samples.petclinic.vet.VetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StaffQueueQueryServiceTests {

	@Test
	void queueDetailRemainsUsableWhenHistoricalPayloadKeyIsUnavailable() {
		Instant now = Instant.parse("2026-08-31T10:00:00Z");
		QueueItemRepository queueItemRepository = mock(QueueItemRepository.class);
		ContactAttemptRepository contactAttemptRepository = mock(ContactAttemptRepository.class);
		OfferRepository offerRepository = mock(OfferRepository.class);
		OfferExclusionRepository exclusionRepository = mock(OfferExclusionRepository.class);
		OwnerRepository ownerRepository = mock(OwnerRepository.class);
		VetRepository vetRepository = mock(VetRepository.class);
		AccountRepository accountRepository = mock(AccountRepository.class);
		ProtectedPayloadService payloadService = mock(ProtectedPayloadService.class);
		AuditService auditService = mock(AuditService.class);

		SchedulingRequest request = new SchedulingRequest(1, 1, RequestState.STAFF_HANDLING, now);
		request.setId(3L);
		ProtectedPayload prose = new ProtectedPayload();
		ProtectedPayload consent = new ProtectedPayload();
		request.setCurrentTextRevision(new TextRevision(request, 1, now, prose, consent));
		QueueItem item = new QueueItem(request, null, QueueState.NEW, "UNUSABLE_OUTPUT", Urgency.ROUTINE, now);
		item.setId(1L);

		when(queueItemRepository.findById(1L)).thenReturn(Optional.of(item));
		when(ownerRepository.findById(1)).thenReturn(Optional.empty());
		when(accountRepository.findAll()).thenReturn(List.of());
		when(contactAttemptRepository.findByQueueItemIdOrderByAttemptedAtAsc(1L)).thenReturn(List.of());
		when(offerRepository.findByRequestIdOrderByStartAtAsc(3L)).thenReturn(List.of());
		when(auditService.findEventsForTarget("QueueItem", "1")).thenReturn(List.of());
		when(payloadService.decryptToStringIfKeyAvailable(any())).thenReturn(Optional.empty());

		StaffQueueQueryService service = new StaffQueueQueryService(queueItemRepository, contactAttemptRepository,
				offerRepository, exclusionRepository, ownerRepository, vetRepository, accountRepository, payloadService,
				auditService, Clock.fixed(now, ZoneOffset.UTC));

		StaffQueueQueryService.QueueItemDetailDto detail = service.getQueueItemDetail(1L).orElseThrow();

		assertThat(detail.originalProse()).contains("historical key is not configured");
		assertThat(detail.consentGranted()).isNull();
	}

}
