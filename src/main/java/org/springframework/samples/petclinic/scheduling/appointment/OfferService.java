package org.springframework.samples.petclinic.scheduling.appointment;

import java.util.Optional;

import org.springframework.samples.petclinic.scheduling.request.OwnerResourceNotFoundException;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfferService {

	private final OfferRepository offers;

	private final HoldRepository holds;

	private final SchedulingRequestRepository requests;

	public OfferService(OfferRepository offers, HoldRepository holds, SchedulingRequestRepository requests) {
		this.offers = offers;
		this.holds = holds;
		this.requests = requests;
	}

	@Transactional(readOnly = true)
	public OfferView loadOwned(Long requestId, Integer ownerId, Long offerId) {
		SchedulingRequest request = this.requests.findByIdAndOwnerId(requestId, ownerId)
			.orElseThrow(OwnerResourceNotFoundException::new);
		Offer offer = this.offers.findById(offerId).orElseThrow(OwnerResourceNotFoundException::new);
		Hold hold = this.holds.findByOfferId(offerId).orElse(null);
		return new OfferView(request, offer, hold);
	}

	@Transactional(readOnly = true)
	public Optional<Offer> activeHeld(Long requestRevisionId) {
		return this.offers.findFirstByRequestRevisionIdAndStatusOrderByCreatedAtDesc(requestRevisionId,
				OfferStatus.HELD);
	}

	public record OfferView(SchedulingRequest request, Offer offer, Hold hold) {
	}

}
