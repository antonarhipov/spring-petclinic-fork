package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Clock;
import java.time.Instant;

import org.springframework.samples.petclinic.scheduling.queue.FallbackRoutingService;
import org.springframework.samples.petclinic.scheduling.request.OwnerResourceNotFoundException;
import org.springframework.samples.petclinic.scheduling.request.RequestAutomationLimitService;
import org.springframework.samples.petclinic.scheduling.request.RequestRecoveryAuditService;
import org.springframework.samples.petclinic.scheduling.request.RequestRevision;
import org.springframework.samples.petclinic.scheduling.request.RequestRevisionRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.RequestWindow;
import org.springframework.samples.petclinic.scheduling.request.RequestWindowRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.system.StaleStateException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfferDecisionService {

	private final SchedulingRequestRepository requests;

	private final OfferRepository offers;

	private final HoldRepository holds;

	private final RequestRevisionRepository revisions;

	private final RequestWindowRepository windows;

	private final ReservationService reservations;

	private final FallbackRoutingService fallback;

	private final RequestAutomationLimitService limits;

	private final RequestRecoveryAuditService recoveryAudit;

	private final Clock clock;

	public OfferDecisionService(SchedulingRequestRepository requests, OfferRepository offers, HoldRepository holds,
			RequestRevisionRepository revisions, RequestWindowRepository windows, ReservationService reservations,
			FallbackRoutingService fallback, RequestAutomationLimitService limits,
			RequestRecoveryAuditService recoveryAudit, Clock clock) {
		this.requests = requests;
		this.offers = offers;
		this.holds = holds;
		this.revisions = revisions;
		this.windows = windows;
		this.reservations = reservations;
		this.fallback = fallback;
		this.limits = limits;
		this.recoveryAudit = recoveryAudit;
		this.clock = clock;
	}

	@Transactional
	public void reject(Long requestId, Integer ownerId, Long offerId, Integer expectedVersion, String reason) {
		SchedulingRequest request = this.requests.findByIdAndOwnerId(requestId, ownerId)
			.orElseThrow(OwnerResourceNotFoundException::new);
		if (expectedVersion != null && request.getVersion() != null && !expectedVersion.equals(request.getVersion())) {
			throw new StaleStateException("stale", request.getState().name(), null);
		}
		Offer offer = this.offers.findById(offerId).orElseThrow(OwnerResourceNotFoundException::new);
		Hold hold = this.holds.findByOfferId(offerId).orElseThrow(OwnerResourceNotFoundException::new);
		if (reason != null && !reason.isBlank()) {
			offer.setOwnerRejectionReason(reason.trim());
		}
		this.reservations.release(hold, offer, "REJECTED", OfferStatus.REJECTED);
		excludeSlot(offer);
		applyLimit(request, Instant.now(this.clock), "OFFER_REJECTED");
	}

	@Transactional
	public void expire(SchedulingRequest request, Offer offer, Hold hold, Instant now) {
		if (hold.getState() != HoldStatus.ACTIVE) {
			return;
		}
		this.reservations.release(hold, offer, "EXPIRED", OfferStatus.EXPIRED);
		excludeSlot(offer);
		applyLimit(request, now, "OFFER_EXPIRED");
	}

	private void excludeSlot(Offer offer) {
		RequestWindow window = new RequestWindow();
		window.setRequestRevisionId(offer.getRequestRevisionId());
		window.setKind("EXCLUDED");
		window.setStartAt(offer.getStartAt());
		window.setEndAt(offer.getEndAt());
		window.setSourcePhrase("vet:" + offer.getVeterinarianId());
		window.setFallbackAllowed(false);
		this.windows.save(window);
	}

	private void applyLimit(SchedulingRequest request, Instant now, String action) {
		RequestRevision revision = this.revisions.findById(request.getActiveRequestRevisionId()).orElseThrow();
		revision.setRejectionExpiryCount(revision.getRejectionExpiryCount() + 1);
		if (this.limits.limitReached(revision.getRejectionExpiryCount())) {
			request.setState(RequestState.STAFF_HANDLING);
			request.setOwnerStatusCode("STAFF_HANDLING");
			this.fallback.ensureQueueItem(request.getId(), "AUTOMATION_LIMIT", request.isSuspectedEmergency());
		}
		else {
			request.setState(RequestState.READY_FOR_SUGGESTION);
			request.setOwnerStatusCode("READY_FOR_SUGGESTION");
		}
		request.setUpdatedAt(now);
		this.recoveryAudit.offerOutcome(request.getId(), action,
				"{\"count\":" + revision.getRejectionExpiryCount() + "}");
	}

}
