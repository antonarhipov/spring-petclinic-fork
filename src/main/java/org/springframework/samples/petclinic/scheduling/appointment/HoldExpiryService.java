package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HoldExpiryService {

	private final HoldRepository holds;

	private final OfferRepository offers;

	private final SchedulingRequestRepository requests;

	private final OfferDecisionService decisions;

	private final Clock clock;

	public HoldExpiryService(HoldRepository holds, OfferRepository offers, SchedulingRequestRepository requests,
			OfferDecisionService decisions, Clock clock) {
		this.holds = holds;
		this.offers = offers;
		this.requests = requests;
		this.decisions = decisions;
		this.clock = clock;
	}

	@Transactional
	public void expireDueHolds() {
		Instant now = Instant.now(this.clock);
		List<Hold> expired = this.holds.findByStateAndExpiresAtLessThanEqual(HoldStatus.ACTIVE, now);
		for (Hold hold : expired) {
			Offer offer = this.offers.findById(hold.getOfferId()).orElseThrow();
			SchedulingRequest request = this.requests.findById(hold.getRequestId()).orElseThrow();
			this.decisions.expire(request, offer, hold, now);
		}
	}

}
