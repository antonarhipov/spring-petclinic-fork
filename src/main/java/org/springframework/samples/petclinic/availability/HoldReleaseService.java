package org.springframework.samples.petclinic.availability;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.samples.petclinic.audit.AuditService;
import org.springframework.samples.petclinic.audit.OwnerHistoryService;
import org.springframework.samples.petclinic.audit.ProtectedPayload;
import org.springframework.samples.petclinic.audit.ProtectedPayloadService;
import org.springframework.samples.petclinic.scheduling.offer.Offer;
import org.springframework.samples.petclinic.scheduling.offer.OfferOrigin;
import org.springframework.samples.petclinic.scheduling.offer.OfferRepository;
import org.springframework.samples.petclinic.scheduling.offer.OfferState;
import org.springframework.samples.petclinic.scheduling.queue.AssistedOfferService;
import org.springframework.samples.petclinic.scheduling.request.OfferExclusion;
import org.springframework.samples.petclinic.scheduling.request.OfferExclusionRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class HoldReleaseService {

	private final CalendarMutationCoordinator calendarMutationCoordinator;

	private final OfferRepository offerRepository;

	private final OfferExclusionRepository exclusionRepository;

	private final SchedulingRequestRepository requestRepository;

	private final AssistedOfferService assistedOfferService;

	private final ProtectedPayloadService protectedPayloadService;

	private final AuditService auditService;

	private final OwnerHistoryService ownerHistoryService;

	private final Clock clock;

	public HoldReleaseService(CalendarMutationCoordinator calendarMutationCoordinator, OfferRepository offerRepository,
			OfferExclusionRepository exclusionRepository, SchedulingRequestRepository requestRepository,
			AssistedOfferService assistedOfferService, ProtectedPayloadService protectedPayloadService,
			AuditService auditService, OwnerHistoryService ownerHistoryService, Clock clock) {
		this.calendarMutationCoordinator = calendarMutationCoordinator;
		this.offerRepository = offerRepository;
		this.exclusionRepository = exclusionRepository;
		this.requestRepository = requestRepository;
		this.assistedOfferService = assistedOfferService;
		this.protectedPayloadService = protectedPayloadService;
		this.auditService = auditService;
		this.ownerHistoryService = ownerHistoryService;
		this.clock = clock;
	}

	public Offer releaseForAvailabilityChange(Long offerId, Long actorAccountId, boolean confirmed, String reason) {
		if (!confirmed) {
			throw new IllegalArgumentException(
					"Explicit confirmation is required before releasing another owner's hold");
		}
		if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
			throw new IllegalArgumentException("A release reason of 1 to 500 characters is required");
		}
		return this.calendarMutationCoordinator.executeWithLock(() -> {
			Offer offer = this.offerRepository.findById(offerId)
				.orElseThrow(() -> new IllegalArgumentException("Held offer not found: " + offerId));
			Instant now = this.clock.instant();
			if (offer.getState() != OfferState.HELD || !offer.getExpiresAt().isAfter(now)) {
				throw new IllegalStateException("The hold is no longer active");
			}

			ProtectedPayload reasonPayload = this.protectedPayloadService.store(UUID.randomUUID(),
					"HOLD_RELEASE_REASON", 1, "text/plain", reason.trim());
			offer.setState(OfferState.RELEASED);
			this.offerRepository.save(offer);
			if (offer.getWorkflowRevision() != null && !this.exclusionRepository.existsByOfferId(offerId)) {
				this.exclusionRepository.save(new OfferExclusion(offer.getWorkflowRevision(), offer.getVetId(),
						offer.getStartAt(), offer.getEndAt(), offerId, now));
			}

			SchedulingRequest request = offer.getRequest();
			if (offer.getOrigin() == OfferOrigin.STAFF_ASSISTED) {
				this.assistedOfferService.handleAssistedOfferRejectionOrExpiry(offerId);
			}
			else if (request.getState() == RequestState.OFFERED) {
				request.setState(RequestState.READY_TO_MATCH);
				request.setUpdatedAt(now);
				this.requestRepository.save(request);
			}

			this.auditService.recordEvent(actorAccountId, "ACTIVE_HOLD_RELEASED", "Offer", offerId.toString(),
					"SUCCESS", UUID.randomUUID(), null, reasonPayload.getId());
			this.ownerHistoryService.recordOwnerHistory(offer.getOwnerId(), offer.getPetId(), request.getId(),
					"OFFER_RELEASED", "Clinic staff released the held time; no appointment was booked", null);
			return offer;
		});
	}

}
