package org.springframework.samples.petclinic.scheduling.offer;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.samples.petclinic.audit.AuditService;
import org.springframework.samples.petclinic.audit.OwnerHistoryService;
import org.springframework.samples.petclinic.availability.CalendarMutationCoordinator;
import org.springframework.samples.petclinic.scheduling.interpretation.Urgency;
import org.springframework.samples.petclinic.scheduling.job.BackgroundJob;
import org.springframework.samples.petclinic.scheduling.job.BackgroundJobRepository;
import org.springframework.samples.petclinic.scheduling.job.JobState;
import org.springframework.samples.petclinic.scheduling.job.JobType;
import org.springframework.samples.petclinic.scheduling.queue.AssistedOfferService;
import org.springframework.samples.petclinic.scheduling.queue.StaffFallbackPort;
import org.springframework.samples.petclinic.scheduling.request.OfferExclusion;
import org.springframework.samples.petclinic.scheduling.request.OfferExclusionRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevision;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevisionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OfferLifecycleService {

	private static final Logger log = LoggerFactory.getLogger(OfferLifecycleService.class);

	private static final int MAX_AUTOMATIC_OFFERS = 5;

	private final OfferRepository offerRepository;

	private final SchedulingRequestRepository requestRepository;

	private final WorkflowRevisionRepository workflowRevisionRepository;

	private final OfferExclusionRepository offerExclusionRepository;

	private final BackgroundJobRepository jobRepository;

	private final AssistedOfferService assistedOfferService;

	private final StaffFallbackPort staffFallbackPort;

	private final CalendarMutationCoordinator calendarCoordinator;

	private final OwnerHistoryService ownerHistoryService;

	private final AuditService auditService;

	private final Clock clock;

	public OfferLifecycleService(OfferRepository offerRepository, SchedulingRequestRepository requestRepository,
			WorkflowRevisionRepository workflowRevisionRepository, OfferExclusionRepository offerExclusionRepository,
			BackgroundJobRepository jobRepository, AssistedOfferService assistedOfferService,
			StaffFallbackPort staffFallbackPort, CalendarMutationCoordinator calendarCoordinator,
			OwnerHistoryService ownerHistoryService, AuditService auditService, Clock clock) {
		this.offerRepository = offerRepository;
		this.requestRepository = requestRepository;
		this.workflowRevisionRepository = workflowRevisionRepository;
		this.offerExclusionRepository = offerExclusionRepository;
		this.jobRepository = jobRepository;
		this.assistedOfferService = assistedOfferService;
		this.staffFallbackPort = staffFallbackPort;
		this.calendarCoordinator = calendarCoordinator;
		this.ownerHistoryService = ownerHistoryService;
		this.auditService = auditService;
		this.clock = clock;
	}

	public Offer rejectOffer(Long offerId, Integer ownerId, String reason) {
		Objects.requireNonNull(offerId, "offerId must not be null");

		return this.calendarCoordinator.executeWithLock(() -> {
			Offer offer = (ownerId != null)
					? this.offerRepository.findByIdAndOwnerId(offerId, ownerId)
						.orElseThrow(() -> new IllegalArgumentException("Offer not found for owner"))
					: this.offerRepository.findById(offerId)
						.orElseThrow(() -> new IllegalArgumentException("Offer not found: " + offerId));

			if (offer.getState() != OfferState.HELD) {
				throw new IllegalStateException("Offer is not in HELD state: " + offer.getState());
			}

			Instant now = this.clock.instant();
			OfferState priorState = offer.getState();
			RequestState priorRequestState = offer.getRequest().getState();
			offer.setState(OfferState.REJECTED);
			this.offerRepository.save(offer);

			SchedulingRequest request = offer.getRequest();
			WorkflowRevision workflowRev = offer.getWorkflowRevision();

			if (workflowRev != null && offer.getOrigin() != OfferOrigin.STAFF_ASSISTED) {
				OfferExclusion exclusion = new OfferExclusion(workflowRev, offer.getVetId(), offer.getStartAt(),
						offer.getEndAt(), offer.getId(), now);
				this.offerExclusionRepository.save(exclusion);
				workflowRev.getExclusions().add(exclusion);
			}

			if (offer.getOrigin() == OfferOrigin.AUTOMATIC) {
				int attemptCount = (offer.getAutomaticAttemptNumber() != null) ? offer.getAutomaticAttemptNumber()
						: (workflowRev != null && workflowRev.getAutomaticOfferCount() != null
								? workflowRev.getAutomaticOfferCount() : 1);

				if (attemptCount >= MAX_AUTOMATIC_OFFERS) {
					request.setState(RequestState.STAFF_HANDLING);
					this.staffFallbackPort.sendToFallbackQueue(request.getId(), "MAX_OFFERS_EXCEEDED", Urgency.ROUTINE,
							"Maximum automatic offer attempts (" + MAX_AUTOMATIC_OFFERS + ") reached");
				}
				else {
					request.setState(RequestState.READY_TO_MATCH);
				}
			}
			else if (offer.getOrigin() == OfferOrigin.STAFF_ASSISTED) {
				this.assistedOfferService.handleAssistedOfferRejectionOrExpiry(offer.getId());
			}

			request.setUpdatedAt(now);
			this.requestRepository.save(request);

			this.ownerHistoryService.recordOwnerHistory(offer.getOwnerId(), offer.getPetId(), request.getId(),
					"OFFER_REJECTED", "Offer for " + offer.getStartAt() + " was rejected"
							+ (reason != null && !reason.isBlank() ? ": " + reason.trim() : ""),
					null);
			this.auditService.recordStructuredEvent(null, "OFFER_REJECTED", "Offer", offer.getId().toString(),
					"SUCCESS", null, null,
					Map.of("offerState", priorState.name(), "requestState", priorRequestState.name()),
					Map.of("offerState", offer.getState().name(), "requestState", request.getState().name()));

			log.info("Rejected offer {} for request {} (origin: {})", offer.getId(), request.getId(),
					offer.getOrigin());
			return offer;
		});
	}

	public Offer expireOffer(Long offerId) {
		Objects.requireNonNull(offerId, "offerId must not be null");

		return this.calendarCoordinator.executeWithLock(() -> {
			Offer offer = this.offerRepository.findById(offerId)
				.orElseThrow(() -> new IllegalArgumentException("Offer not found: " + offerId));

			if (offer.getState() != OfferState.HELD) {
				return offer;
			}

			Instant now = this.clock.instant();
			if (offer.getExpiresAt().isAfter(now)) {
				return offer; // Not yet expired
			}
			OfferState priorState = offer.getState();
			RequestState priorRequestState = offer.getRequest().getState();

			offer.setState(OfferState.EXPIRED);
			this.offerRepository.save(offer);

			SchedulingRequest request = offer.getRequest();
			WorkflowRevision workflowRev = offer.getWorkflowRevision();

			if (workflowRev != null && offer.getOrigin() != OfferOrigin.STAFF_ASSISTED) {
				OfferExclusion exclusion = new OfferExclusion(workflowRev, offer.getVetId(), offer.getStartAt(),
						offer.getEndAt(), offer.getId(), now);
				this.offerExclusionRepository.save(exclusion);
				workflowRev.getExclusions().add(exclusion);
			}

			if (offer.getOrigin() == OfferOrigin.AUTOMATIC) {
				int attemptCount = (offer.getAutomaticAttemptNumber() != null) ? offer.getAutomaticAttemptNumber()
						: (workflowRev != null && workflowRev.getAutomaticOfferCount() != null
								? workflowRev.getAutomaticOfferCount() : 1);

				if (attemptCount >= MAX_AUTOMATIC_OFFERS) {
					request.setState(RequestState.STAFF_HANDLING);
					this.staffFallbackPort.sendToFallbackQueue(request.getId(), "MAX_OFFERS_EXCEEDED", Urgency.ROUTINE,
							"Maximum automatic offer attempts (" + MAX_AUTOMATIC_OFFERS + ") reached after expiry");
				}
				else {
					request.setState(RequestState.READY_TO_MATCH);
				}
			}
			else if (offer.getOrigin() == OfferOrigin.STAFF_ASSISTED) {
				this.assistedOfferService.handleAssistedOfferRejectionOrExpiry(offer.getId());
			}

			request.setUpdatedAt(now);
			this.requestRepository.save(request);

			this.ownerHistoryService.recordOwnerHistory(offer.getOwnerId(), offer.getPetId(), request.getId(),
					"OFFER_EXPIRED", "Held offer expired at " + offer.getExpiresAt(), null);
			this.auditService.recordStructuredEvent(null, "OFFER_EXPIRED", "Offer", offer.getId().toString(), "SUCCESS",
					null, null, Map.of("offerState", priorState.name(), "requestState", priorRequestState.name()),
					Map.of("offerState", offer.getState().name(), "requestState", request.getState().name()));

			log.info("Expired offer {} for request {}", offer.getId(), request.getId());
			return offer;
		});
	}

	public SchedulingRequest requestNextOffer(Long requestId, Integer ownerId) {
		Objects.requireNonNull(requestId, "requestId must not be null");
		Objects.requireNonNull(ownerId, "ownerId must not be null");

		SchedulingRequest request = this.requestRepository.findByIdAndOwnerId(requestId, ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));

		if (request.getState() != RequestState.READY_TO_MATCH) {
			throw new IllegalStateException("Request is not ready to match: " + request.getState());
		}

		WorkflowRevision workflowRev = request.getCurrentWorkflowRevision();
		if (workflowRev == null) {
			throw new IllegalStateException("No workflow revision found for request");
		}

		int currentOffers = (workflowRev.getAutomaticOfferCount() != null) ? workflowRev.getAutomaticOfferCount() : 0;
		if (currentOffers >= MAX_AUTOMATIC_OFFERS) {
			throw new IllegalStateException(
					"Maximum automatic offers (" + MAX_AUTOMATIC_OFFERS + ") reached for this revision");
		}

		Instant now = this.clock.instant();
		BackgroundJob job = this.jobRepository.findByWorkflowRevisionId(workflowRev.getId())
			.orElseGet(() -> new BackgroundJob(JobType.MATCHING, null, workflowRev, JobState.PENDING, now));
		job.setState(JobState.PENDING);
		job.setAvailableAt(now);
		job.setRunSequence((job.getRunSequence() != null ? job.getRunSequence() : 1) + 1);
		job.setCompletedAt(null);
		job.setLeaseToken(null);
		job.setLeaseUntil(null);
		this.jobRepository.save(job);

		request.setUpdatedAt(now);
		this.requestRepository.save(request);

		this.ownerHistoryService.recordOwnerHistory(ownerId, request.getPetId(), requestId, "MATCH_REQUESTED",
				"Owner requested next eligible appointment offer", null);
		this.auditService.recordStructuredEvent(null, "MATCHING_REQUESTED", "SchedulingRequest", requestId.toString(),
				"SUCCESS", null, null, Map.of("automaticOfferCount", currentOffers),
				Map.of("jobCommandId", job.getCommandId(), "jobRunSequence", job.getRunSequence()));

		log.info("Enqueued matching job for owner {} request {} (offer count so far: {})", ownerId, requestId,
				currentOffers);
		return request;
	}

}
