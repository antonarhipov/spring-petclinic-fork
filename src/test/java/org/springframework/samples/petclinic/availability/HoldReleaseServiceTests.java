package org.springframework.samples.petclinic.availability;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.samples.petclinic.audit.AuditService;
import org.springframework.samples.petclinic.audit.OwnerHistoryService;
import org.springframework.samples.petclinic.audit.ProtectedPayload;
import org.springframework.samples.petclinic.audit.ProtectedPayloadService;
import org.springframework.samples.petclinic.scheduling.interpretation.Urgency;
import org.springframework.samples.petclinic.scheduling.offer.Offer;
import org.springframework.samples.petclinic.scheduling.offer.OfferOrigin;
import org.springframework.samples.petclinic.scheduling.offer.OfferRepository;
import org.springframework.samples.petclinic.scheduling.offer.OfferState;
import org.springframework.samples.petclinic.scheduling.queue.AssistedOfferService;
import org.springframework.samples.petclinic.scheduling.request.OfferExclusionRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevision;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevisionState;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldReleaseServiceTests {

	@Mock
	private CalendarMutationCoordinator calendarCoordinator;

	@Mock
	private OfferRepository offerRepository;

	@Mock
	private OfferExclusionRepository exclusionRepository;

	@Mock
	private SchedulingRequestRepository requestRepository;

	@Mock
	private AssistedOfferService assistedOfferService;

	@Mock
	private ProtectedPayloadService payloadService;

	@Mock
	private AuditService auditService;

	@Mock
	private OwnerHistoryService ownerHistoryService;

	private final Instant now = Instant.parse("2026-08-31T10:00:00Z");

	private HoldReleaseService service;

	@BeforeEach
	void setUp() {
		this.service = new HoldReleaseService(this.calendarCoordinator, this.offerRepository, this.exclusionRepository,
				this.requestRepository, this.assistedOfferService, this.payloadService, this.auditService,
				this.ownerHistoryService, Clock.fixed(this.now, ZoneOffset.UTC));
	}

	@Test
	void releaseRequiresExplicitConfirmationAndReason() {
		assertThatThrownBy(() -> this.service.releaseForAvailabilityChange(12L, 3L, false, "Needed for closure"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("confirmation");
		assertThatThrownBy(() -> this.service.releaseForAvailabilityChange(12L, 3L, true, " "))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("reason");
		verify(this.offerRepository, never()).findById(any());
	}

	@Test
	void confirmedAutomaticHoldReleaseIsAuditedAndReturnsRequestToMatching() {
		allowLockExecution();
		Offer offer = heldOffer(OfferOrigin.AUTOMATIC);
		ProtectedPayload reasonPayload = new ProtectedPayload();
		reasonPayload.setId(88L);
		when(this.offerRepository.findById(12L)).thenReturn(Optional.of(offer));
		when(this.payloadService.store(any(), eq("HOLD_RELEASE_REASON"), eq(1), eq("text/plain"),
				eq("Capacity change approved")))
			.thenReturn(reasonPayload);

		Offer released = this.service.releaseForAvailabilityChange(12L, 3L, true, "Capacity change approved");

		assertThat(released.getState()).isEqualTo(OfferState.RELEASED);
		assertThat(released.getRequest().getState()).isEqualTo(RequestState.READY_TO_MATCH);
		verify(this.offerRepository).save(offer);
		verify(this.exclusionRepository).save(any());
		verify(this.auditService).recordEvent(eq(3L), eq("ACTIVE_HOLD_RELEASED"), eq("Offer"), eq("12"), eq("SUCCESS"),
				any(), eq(null), eq(88L));
		verify(this.ownerHistoryService).recordOwnerHistory(eq(1), eq(1), eq(20L), eq("OFFER_RELEASED"), any(),
				eq(null));
	}

	@Test
	void assistedHoldReleaseDelegatesQueueRecovery() {
		allowLockExecution();
		Offer offer = heldOffer(OfferOrigin.STAFF_ASSISTED);
		ProtectedPayload reasonPayload = new ProtectedPayload();
		reasonPayload.setId(89L);
		when(this.offerRepository.findById(12L)).thenReturn(Optional.of(offer));
		when(this.payloadService.store(any(), any(), eq(1), any(), any(String.class))).thenReturn(reasonPayload);

		this.service.releaseForAvailabilityChange(12L, 3L, true, "Owner contacted about clinic closure");

		verify(this.assistedOfferService).handleAssistedOfferRejectionOrExpiry(12L);
	}

	private Offer heldOffer(OfferOrigin origin) {
		SchedulingRequest request = new SchedulingRequest(1, 1, RequestState.OFFERED, this.now);
		request.setId(20L);
		ProtectedPayload workflowReason = new ProtectedPayload();
		workflowReason.setId(40L);
		WorkflowRevision workflowRevision = new WorkflowRevision(request, 1, null, null,
				WorkflowRevisionState.CONFIRMED, workflowReason, 30, 1, null, Urgency.ROUTINE, 0);
		workflowRevision.setId(30L);
		Offer offer = new Offer(request, workflowRevision, 1, 1, 1, origin, this.now.plusSeconds(7200),
				this.now.plusSeconds(9000), "Europe/Tallinn", this.now.plusSeconds(1800), OfferState.HELD,
				origin == OfferOrigin.AUTOMATIC ? 1 : null, 1L, "Held slot");
		offer.setId(12L);
		return offer;
	}

	private void allowLockExecution() {
		when(this.calendarCoordinator.executeWithLock(any(Supplier.class)))
			.thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());
	}

}
