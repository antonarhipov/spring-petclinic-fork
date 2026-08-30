package org.springframework.samples.petclinic.scheduling.request;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.scheduling.appointment.Hold;
import org.springframework.samples.petclinic.scheduling.appointment.HoldRepository;
import org.springframework.samples.petclinic.scheduling.appointment.HoldStatus;
import org.springframework.samples.petclinic.scheduling.appointment.Offer;
import org.springframework.samples.petclinic.scheduling.appointment.OfferRepository;
import org.springframework.samples.petclinic.scheduling.appointment.OfferStatus;
import org.springframework.samples.petclinic.scheduling.appointment.ReservationService;
import org.springframework.samples.petclinic.scheduling.queue.StaffQueueRepository;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestRevisionAndWithdrawalTests {

	@Test
	void reviseReleasesActiveHoldAndKeepsHistory() {
		RequestWorkflowService workflow = mock(RequestWorkflowService.class);
		HoldRepository holds = mock(HoldRepository.class);
		OfferRepository offers = mock(OfferRepository.class);
		ReservationService reservations = mock(ReservationService.class);
		RequestRecoveryAuditService audit = mock(RequestRecoveryAuditService.class);
		Hold hold = new Hold();
		hold.setState(HoldStatus.ACTIVE);
		hold.setOfferId(8L);
		when(holds.findByRequestId(1L)).thenReturn(List.of(hold));
		Offer offer = new Offer();
		when(offers.findById(8L)).thenReturn(Optional.of(offer));
		RequestRevisionService service = new RequestRevisionService(workflow, holds, offers, reservations, audit);
		service.reviseSource(1L, 1, 0, "Need a different morning wellness exam");
		verify(reservations).release(hold, offer, "REVISED", OfferStatus.RELEASED);
		verify(workflow).reviseSourceText(1L, 1, 0, "Need a different morning wellness exam");
		verify(audit).offerOutcome(1L, "REQUEST_REVISED", "{\"state\":\"AWAITING_CONSENT\"}");
	}

	@Test
	void withdrawReleasesHoldClosesQueueAndBlocksReopen() {
		SchedulingRequestRepository requests = mock(SchedulingRequestRepository.class);
		ActivePetRequestRepository activePets = mock(ActivePetRequestRepository.class);
		HoldRepository holds = mock(HoldRepository.class);
		OfferRepository offers = mock(OfferRepository.class);
		ReservationService reservations = mock(ReservationService.class);
		StaffQueueRepository queueItems = mock(StaffQueueRepository.class);
		RequestRecoveryAuditService audit = mock(RequestRecoveryAuditService.class);
		SchedulingRequest request = new SchedulingRequest();
		ReflectionTestUtils.setField(request, "id", 1L);
		request.setState(RequestState.OFFER_HELD);
		ReflectionTestUtils.setField(request, "version", 2);
		when(requests.findByIdAndOwnerId(1L, 1)).thenReturn(Optional.of(request));
		Hold hold = new Hold();
		hold.setState(HoldStatus.ACTIVE);
		hold.setOfferId(8L);
		when(holds.findByRequestId(1L)).thenReturn(List.of(hold));
		Offer offer = new Offer();
		when(offers.findById(8L)).thenReturn(Optional.of(offer));
		when(activePets.findByRequestId(1L)).thenReturn(Optional.empty());
		when(queueItems.findByRequestId(1L)).thenReturn(Optional.empty());
		RequestWithdrawalService service = new RequestWithdrawalService(requests, activePets, holds, offers,
				reservations, queueItems, audit, Clock.fixed(Instant.parse("2026-03-16T15:00:00Z"), ZoneOffset.UTC));
		service.withdraw(1L, 1, 9L, 2);
		verify(reservations).release(hold, offer, "WITHDRAWN", OfferStatus.RELEASED);
		assertThat(request.getState()).isEqualTo(RequestState.CLOSED);
		assertThat(request.getClosureOutcome()).isEqualTo("WITHDRAWN");
		verify(audit).withdrawn(1L, 9L);
		assertThatThrownBy(() -> service.withdraw(1L, 1, 9L, 2)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void historyRetainsOffersForActiveRevision() {
		SchedulingRequestRepository requests = mock(SchedulingRequestRepository.class);
		OfferRepository offers = mock(OfferRepository.class);
		SchedulingRequest request = new SchedulingRequest();
		request.setActiveRequestRevisionId(3L);
		when(requests.findByIdAndOwnerId(1L, 1)).thenReturn(Optional.of(request));
		Offer previous = new Offer();
		previous.setStatus(OfferStatus.REJECTED);
		when(offers.findByRequestRevisionIdOrderByCreatedAtDesc(3L)).thenReturn(List.of(previous));
		OwnerRequestHistoryService history = new OwnerRequestHistoryService(requests, offers);
		assertThat(history.load(1L, 1).offers()).containsExactly(previous);
	}

}
