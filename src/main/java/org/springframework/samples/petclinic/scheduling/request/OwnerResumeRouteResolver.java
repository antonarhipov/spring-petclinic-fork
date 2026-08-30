package org.springframework.samples.petclinic.scheduling.request;

import org.springframework.samples.petclinic.scheduling.appointment.Offer;
import org.springframework.samples.petclinic.scheduling.appointment.OfferService;
import org.springframework.stereotype.Component;

@Component
public class OwnerResumeRouteResolver {

	private final OfferService offers;

	public OwnerResumeRouteResolver(OfferService offers) {
		this.offers = offers;
	}

	public String resumeUrl(SchedulingRequest request) {
		if (request == null) {
			return null;
		}
		return switch (request.getState()) {
			case AWAITING_CONSENT -> "/owner/scheduling-requests/" + request.getId() + "/consent";
			case INTERPRETING, MATCHING -> "/owner/scheduling-requests/" + request.getId() + "/status";
			case INTERPRETATION_REVIEW -> "/owner/scheduling-requests/" + request.getId() + "/interpretation";
			case READY_FOR_SUGGESTION -> "/owner/scheduling-requests/" + request.getId() + "/suggestion";
			case OFFER_HELD -> this.offers.activeHeld(request.getActiveRequestRevisionId())
				.map(Offer::getId)
				.map(id -> "/owner/scheduling-requests/" + request.getId() + "/offers/" + id)
				.orElse("/owner/scheduling-requests/" + request.getId() + "/status");
			case AWAITING_FALLBACK_CHOICE -> "/owner/scheduling-requests/" + request.getId() + "/fallback-choice";
			case STAFF_HANDLING -> "/owner/scheduling-requests/" + request.getId() + "/status";
			default -> "/owner/scheduling-requests/" + request.getId() + "/status";
		};
	}

}
