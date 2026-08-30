package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HoldExpiryServiceTests {

	@Test
	void expireDueHoldsDelegatesWithoutGetMutation() {
		HoldRepository holds = mock(HoldRepository.class);
		OfferRepository offers = mock(OfferRepository.class);
		SchedulingRequestRepository requests = mock(SchedulingRequestRepository.class);
		OfferDecisionService decisions = mock(OfferDecisionService.class);
		Instant now = Instant.parse("2026-03-16T16:00:00Z");
		Hold hold = new Hold();
		hold.setOfferId(8L);
		hold.setRequestId(1L);
		hold.setState(HoldStatus.ACTIVE);
		when(holds.findByStateAndExpiresAtLessThanEqual(HoldStatus.ACTIVE, now)).thenReturn(List.of(hold));
		Offer offer = new Offer();
		when(offers.findById(8L)).thenReturn(Optional.of(offer));
		SchedulingRequest request = new SchedulingRequest();
		when(requests.findById(1L)).thenReturn(Optional.of(request));
		HoldExpiryService service = new HoldExpiryService(holds, offers, requests, decisions,
				Clock.fixed(now, ZoneOffset.UTC));
		service.expireDueHolds();
		verify(decisions).expire(request, offer, hold, now);
	}

}
