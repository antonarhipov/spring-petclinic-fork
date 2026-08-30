package org.springframework.samples.petclinic.scheduling.request;

import java.time.Instant;
import java.util.List;

import org.springframework.samples.petclinic.scheduling.appointment.Offer;
import org.springframework.samples.petclinic.scheduling.appointment.OfferRepository;
import org.springframework.samples.petclinic.scheduling.appointment.OfferStatus;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerRequestHistoryService {

	private final SchedulingRequestRepository requests;

	private final OfferRepository offers;

	private final VetRepository vets;

	public OwnerRequestHistoryService(SchedulingRequestRepository requests, OfferRepository offers,
			VetRepository vets) {
		this.requests = requests;
		this.offers = offers;
		this.vets = vets;
	}

	@Transactional(readOnly = true)
	public HistoryView load(Long requestId, Integer ownerId) {
		SchedulingRequest request = this.requests.findByIdAndOwnerId(requestId, ownerId)
			.orElseThrow(OwnerResourceNotFoundException::new);
		List<Offer> offerHistory = request.getActiveRequestRevisionId() == null ? List.of()
				: this.offers.findByRequestRevisionIdOrderByCreatedAtDesc(request.getActiveRequestRevisionId());
		List<OwnerOfferHistoryView> history = offerHistory.stream().map(this::view).toList();
		return new HistoryView(request, history);
	}

	private OwnerOfferHistoryView view(Offer offer) {
		String vetName = this.vets.findById(offer.getVeterinarianId())
			.map(vet -> vet.getFirstName() + " " + vet.getLastName())
			.orElse("");
		return new OwnerOfferHistoryView(offer.getId(), offer.getStatus(), offer.getStartAt(), offer.getEndAt(),
				vetName, offer.getPublicExplanationCode());
	}

	public record OwnerOfferHistoryView(Long id, OfferStatus status, Instant startAt, Instant endAt,
			String veterinarianName, String explanationCode) {
	}

	public record HistoryView(SchedulingRequest request, List<OwnerOfferHistoryView> offers) {
	}

}
