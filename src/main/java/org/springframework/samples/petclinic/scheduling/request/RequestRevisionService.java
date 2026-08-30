package org.springframework.samples.petclinic.scheduling.request;

import org.springframework.samples.petclinic.scheduling.appointment.Hold;
import org.springframework.samples.petclinic.scheduling.appointment.HoldRepository;
import org.springframework.samples.petclinic.scheduling.appointment.HoldStatus;
import org.springframework.samples.petclinic.scheduling.appointment.Offer;
import org.springframework.samples.petclinic.scheduling.appointment.OfferRepository;
import org.springframework.samples.petclinic.scheduling.appointment.OfferStatus;
import org.springframework.samples.petclinic.scheduling.appointment.ReservationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestRevisionService {

	private final RequestWorkflowService workflow;

	private final HoldRepository holds;

	private final OfferRepository offers;

	private final ReservationService reservations;

	private final RequestRecoveryAuditService audit;

	public RequestRevisionService(RequestWorkflowService workflow, HoldRepository holds, OfferRepository offers,
			ReservationService reservations, RequestRecoveryAuditService audit) {
		this.workflow = workflow;
		this.holds = holds;
		this.offers = offers;
		this.reservations = reservations;
		this.audit = audit;
	}

	public RequestRevision saveDraft(Long requestId, Integer ownerId, Integer expectedVersion, String visitReason,
			Integer durationMinutes) {
		return this.workflow.saveOwnerEdits(requestId, ownerId, expectedVersion, visitReason, durationMinutes);
	}

	public void confirm(Long requestId, Integer ownerId, Long accountId, Integer expectedVersion) {
		this.workflow.confirm(requestId, ownerId, accountId, expectedVersion);
	}

	@Transactional
	public void reviseSource(Long requestId, Integer ownerId, Integer expectedVersion, String sourceText) {
		for (Hold hold : this.holds.findByRequestId(requestId)) {
			if (hold.getState() == HoldStatus.ACTIVE) {
				Offer offer = this.offers.findById(hold.getOfferId()).orElseThrow();
				this.reservations.release(hold, offer, "REVISED", OfferStatus.RELEASED);
			}
		}
		this.workflow.reviseSourceText(requestId, ownerId, expectedVersion, sourceText);
		this.audit.offerOutcome(requestId, "REQUEST_REVISED", "{\"state\":\"AWAITING_CONSENT\"}");
	}

}
