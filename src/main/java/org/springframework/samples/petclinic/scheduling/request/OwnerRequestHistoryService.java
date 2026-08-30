package org.springframework.samples.petclinic.scheduling.request;

import java.util.List;

import org.springframework.samples.petclinic.scheduling.appointment.Offer;
import org.springframework.samples.petclinic.scheduling.appointment.OfferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerRequestHistoryService {

	private final SchedulingRequestRepository requests;

	private final OfferRepository offers;

	public OwnerRequestHistoryService(SchedulingRequestRepository requests, OfferRepository offers) {
		this.requests = requests;
		this.offers = offers;
	}

	@Transactional(readOnly = true)
	public HistoryView load(Long requestId, Integer ownerId) {
		SchedulingRequest request = this.requests.findByIdAndOwnerId(requestId, ownerId)
			.orElseThrow(OwnerResourceNotFoundException::new);
		List<Offer> history = request.getActiveRequestRevisionId() == null ? List.of()
				: this.offers.findByRequestRevisionIdOrderByCreatedAtDesc(request.getActiveRequestRevisionId());
		return new HistoryView(request, history);
	}

	public record HistoryView(SchedulingRequest request, List<Offer> offers) {
	}

}
