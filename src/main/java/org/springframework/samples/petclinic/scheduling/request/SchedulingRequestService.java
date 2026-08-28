package org.springframework.samples.petclinic.scheduling.request;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.ai.InterpretationPort;
import org.springframework.samples.petclinic.scheduling.ai.InterpretationResponse;
import org.springframework.samples.petclinic.scheduling.audit.AuditAction;
import org.springframework.samples.petclinic.scheduling.audit.SchedulingAuditService;
import org.springframework.samples.petclinic.scheduling.offer.OfferService;
import org.springframework.samples.petclinic.scheduling.queue.FallbackQueueService;
import org.springframework.samples.petclinic.security.AuthenticatedOwner;
import org.springframework.samples.petclinic.security.OwnerAccessService;

@Service
public class SchedulingRequestService {

	private final SchedulingRequestRepository requests;

	private final RequestRevisionRepository revisions;

	private final OwnerAccessService owners;

	private final InterpretationPort interpretation;

	private final EmergencyScreeningService emergencyScreening;

	private final FallbackQueueService fallback;

	private final OfferService offers;

	private final SchedulingAuditService audit;

	private final Clock clock;

	public SchedulingRequestService(SchedulingRequestRepository requests, RequestRevisionRepository revisions,
			OwnerAccessService owners, InterpretationPort interpretation, EmergencyScreeningService emergencyScreening,
			FallbackQueueService fallback, OfferService offers, SchedulingAuditService audit, Clock clock) {
		this.requests = requests;
		this.revisions = revisions;
		this.owners = owners;
		this.interpretation = interpretation;
		this.emergencyScreening = emergencyScreening;
		this.fallback = fallback;
		this.offers = offers;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional
	public SchedulingRequest submit(Integer petId, String sourceText, boolean consent, Authentication actor) {
		validateSourceText(sourceText);
		AuthenticatedOwner authenticatedOwner = this.owners.currentOwner(actor);
		Pet pet = authenticatedOwner.owner().getPet(petId);
		if (pet == null) {
			throw new org.springframework.security.access.AccessDeniedException("Access denied");
		}
		if (this.requests.existsByPetIdAndStateIn(petId,
				List.of(SchedulingRequestState.INTERPRETATION_REVIEW, SchedulingRequestState.READY_FOR_SUGGESTION,
						SchedulingRequestState.OFFER_HELD, SchedulingRequestState.STAFF_HANDLING))) {
			throw new IllegalStateException("This pet already has an active scheduling request");
		}
		SchedulingRequest request = this.requests
			.save(new SchedulingRequest(pet, this.emergencyScreening.indicatesEmergency(sourceText)));
		String correlationId = UUID.randomUUID().toString();
		RequestRevision revision = this.revisions.save(new RequestRevision(request, 1, sourceText, consent,
				consent ? this.clock.instant() : null, correlationId));
		request.setCurrentRevision(revision);
		this.audit.record(actor, correlationId, AuditAction.REQUEST_SUBMITTED, "request", request.getId(), null,
				"INTERPRETATION_REVIEW", null);
		this.audit.record(actor, correlationId, AuditAction.CONSENT_RECORDED, "revision", revision.getId(), null,
				Boolean.toString(consent), null);
		if (request.isEmergencyPriority() || !consent) {
			revision.requireStaffReview();
			this.fallback.route(request);
			return request;
		}
		InterpretationPort.InterpretationResult result = this.interpretation.interpret(sourceText, correlationId);
		if (!result.isSuccessful()) {
			revision.requireStaffReview();
			this.fallback.route(request);
			this.audit.record(actor, correlationId, AuditAction.INTERPRETATION_FALLBACK, "request", request.getId(),
					null, "STAFF_HANDLING", result.failure().name());
			return request;
		}
		applyInterpretation(revision, result.response());
		return request;
	}

	@Transactional
	public SchedulingRequest confirm(Integer requestId, String visitReason, Integer duration, Authentication actor) {
		SchedulingRequest request = owned(requestId, actor);
		RequestRevision revision = request.getCurrentRevision();
		if (revision.getStatus() == RevisionStatus.NEEDS_STAFF_REVIEW) {
			throw new IllegalStateException("This request requires staff handling");
		}
		if (visitReason != null && !visitReason.isBlank()) {
			revision.applyInterpretation(null, null, visitReason,
					duration == null ? revision.getDurationMinutes() : duration, "GENERAL",
					revision.getRequiredSpecialty(), revision.getPreferredVet(), "STANDARD");
		}
		if (revision.getDurationMinutes() == null) {
			throw new IllegalStateException("A valid interpretation is required");
		}
		revision.confirm(this.clock.instant());
		request.moveTo(SchedulingRequestState.READY_FOR_SUGGESTION);
		this.audit.record(actor, revision.getCorrelationId(), AuditAction.REQUEST_CONFIRMED, "request", requestId,
				"INTERPRETATION_REVIEW", "READY_FOR_SUGGESTION", null);
		if (this.offers.createOffer(revision, actor).isEmpty()) {
			this.fallback.route(request);
		}
		return request;
	}

	@Transactional
	public SchedulingRequest revise(Integer requestId, String sourceText, boolean consent, Authentication actor) {
		SchedulingRequest request = owned(requestId, actor);
		RequestRevision prior = request.getCurrentRevision();
		if (request.getState() == SchedulingRequestState.CONFIRMED
				|| request.getState() == SchedulingRequestState.CLOSED) {
			throw new IllegalStateException("This request cannot be revised");
		}
		validateSourceText(sourceText);
		this.offers.releaseCurrent(prior, actor, "Owner revision");
		prior.supersede();
		RequestRevision next = this.revisions.save(new RequestRevision(request, prior.getRevisionNumber() + 1,
				sourceText, consent, consent ? this.clock.instant() : null, UUID.randomUUID().toString()));
		request.setCurrentRevision(next);
		request.moveTo(SchedulingRequestState.INTERPRETATION_REVIEW);
		if (!consent) {
			next.requireStaffReview();
			this.fallback.route(request);
			return request;
		}
		InterpretationPort.InterpretationResult result = this.interpretation.interpret(sourceText,
				next.getCorrelationId());
		if (result.isSuccessful()) {
			applyInterpretation(next, result.response());
		}
		else {
			next.requireStaffReview();
			this.fallback.route(request);
		}
		return request;
	}

	@Transactional
	public void withdraw(Integer requestId, Authentication actor) {
		SchedulingRequest request = owned(requestId, actor);
		if (request.getState() == SchedulingRequestState.CONFIRMED) {
			throw new IllegalStateException("A confirmed request cannot be withdrawn");
		}
		this.offers.releaseCurrent(request.getCurrentRevision(), actor, "Owner withdrawal");
		request.getCurrentRevision().withdraw();
		request.moveTo(SchedulingRequestState.CLOSED);
		this.audit.record(actor, request.getCurrentRevision().getCorrelationId(), AuditAction.REQUEST_WITHDRAWN,
				"request", requestId, null, "CLOSED", null);
	}

	private SchedulingRequest owned(Integer requestId, Authentication actor) {
		return this.requests.findOwnedById(requestId, this.owners.currentOwner(actor).owner().getId())
			.orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Access denied"));
	}

	private void applyInterpretation(RequestRevision revision, InterpretationResponse response) {
		revision.applyInterpretation(response.toString(), "ollama", response.visitReason(), response.durationMinutes(),
				response.careType(), response.requiredSpecialty(), null, response.urgency());
	}

	private void validateSourceText(String sourceText) {
		if (sourceText == null || sourceText.length() < 10 || sourceText.length() > 2000) {
			throw new IllegalArgumentException("Describe your request using 10 to 2,000 characters");
		}
	}

}
