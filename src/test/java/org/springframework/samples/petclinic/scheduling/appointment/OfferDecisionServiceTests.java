package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.scheduling.queue.FallbackRoutingService;
import org.springframework.samples.petclinic.scheduling.request.RequestRevision;
import org.springframework.samples.petclinic.scheduling.request.RequestRevisionRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.RequestWindowRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestAutomationLimitService;
import org.springframework.samples.petclinic.scheduling.request.RequestRecoveryAuditService;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferDecisionServiceTests {

	@Test
	void rejectExcludesExactSlotAndReturnsToReady() {
		Harness harness = Harness.create(0);
		harness.service.reject(1L, 1, 8L, null, "too late");
		verify(harness.reservations).release(harness.hold, harness.offer, "REJECTED", OfferStatus.REJECTED);
		assertThat(harness.offer.getOwnerRejectionReason()).isEqualTo("too late");
		assertThat(harness.revision.getRejectionExpiryCount()).isEqualTo(1);
		assertThat(harness.request.getState()).isEqualTo(RequestState.READY_FOR_SUGGESTION);
		verify(harness.windows).save(any());
		verify(harness.fallback, never()).ensureQueueItem(any(), any(), any(Boolean.class));
	}

	@Test
	void fifthRejectionRoutesToStaff() {
		Harness harness = Harness.create(4);
		harness.service.reject(1L, 1, 8L, null, null);
		assertThat(harness.revision.getRejectionExpiryCount()).isEqualTo(5);
		assertThat(harness.request.getState()).isEqualTo(RequestState.STAFF_HANDLING);
		verify(harness.fallback).ensureQueueItem(1L, "AUTOMATION_LIMIT", false);
	}

	@Test
	void expiryIncrementsLimitWithoutRepeatingSlot() {
		Harness harness = Harness.create(1);
		harness.service.expire(harness.request, harness.offer, harness.hold, Instant.parse("2026-03-16T16:00:00Z"));
		verify(harness.reservations).release(harness.hold, harness.offer, "EXPIRED", OfferStatus.EXPIRED);
		assertThat(harness.revision.getRejectionExpiryCount()).isEqualTo(2);
		assertThat(harness.request.getState()).isEqualTo(RequestState.READY_FOR_SUGGESTION);
	}

	private static final class Harness {

		private final SchedulingRequest request;

		private final RequestRevision revision;

		private final Offer offer;

		private final Hold hold;

		private final ReservationService reservations;

		private final RequestWindowRepository windows;

		private final FallbackRoutingService fallback;

		private final OfferDecisionService service;

		private Harness(SchedulingRequest request, RequestRevision revision, Offer offer, Hold hold,
				ReservationService reservations, RequestWindowRepository windows, FallbackRoutingService fallback,
				OfferDecisionService service) {
			this.request = request;
			this.revision = revision;
			this.offer = offer;
			this.hold = hold;
			this.reservations = reservations;
			this.windows = windows;
			this.fallback = fallback;
			this.service = service;
		}

		static Harness create(int existingCount) {
			SchedulingRequestRepository requests = mock(SchedulingRequestRepository.class);
			OfferRepository offers = mock(OfferRepository.class);
			HoldRepository holds = mock(HoldRepository.class);
			RequestRevisionRepository revisions = mock(RequestRevisionRepository.class);
			RequestWindowRepository windows = mock(RequestWindowRepository.class);
			ReservationService reservations = mock(ReservationService.class);
			FallbackRoutingService fallback = mock(FallbackRoutingService.class);
			RequestRecoveryAuditService audit = mock(RequestRecoveryAuditService.class);
			SchedulingRequest request = new SchedulingRequest();
			ReflectionTestUtils.setField(request, "id", 1L);
			request.setState(RequestState.OFFER_HELD);
			request.setActiveRequestRevisionId(3L);
			when(requests.findByIdAndOwnerId(1L, 1)).thenReturn(Optional.of(request));
			when(requests.findById(1L)).thenReturn(Optional.of(request));
			RequestRevision revision = new RequestRevision();
			revision.setRequestId(1L);
			revision.setRejectionExpiryCount(existingCount);
			when(revisions.findById(3L)).thenReturn(Optional.of(revision));
			Offer offer = new Offer();
			offer.setStatus(OfferStatus.HELD);
			offer.setVeterinarianId(2);
			offer.setStartAt(Instant.parse("2026-03-16T15:00:00Z"));
			offer.setEndAt(Instant.parse("2026-03-16T15:30:00Z"));
			offer.setRequestRevisionId(3L);
			when(offers.findById(8L)).thenReturn(Optional.of(offer));
			when(offers.findByRequestRevisionIdOrderByCreatedAtDesc(3L)).thenReturn(List.of(offer));
			Hold hold = new Hold();
			hold.setState(HoldStatus.ACTIVE);
			hold.setExpiresAt(Instant.parse("2026-03-16T15:50:00Z"));
			when(holds.findByOfferId(8L)).thenReturn(Optional.of(hold));
			OfferDecisionService service = new OfferDecisionService(requests, offers, holds, revisions, windows,
					reservations, fallback, new RequestAutomationLimitService(), audit,
					Clock.fixed(Instant.parse("2026-03-16T15:10:00Z"), ZoneOffset.UTC));
			return new Harness(request, revision, offer, hold, reservations, windows, fallback, service);
		}

	}

}
