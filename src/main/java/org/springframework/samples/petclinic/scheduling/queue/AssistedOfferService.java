package org.springframework.samples.petclinic.scheduling.queue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.samples.petclinic.audit.AuditService;
import org.springframework.samples.petclinic.audit.OwnerHistoryService;
import org.springframework.samples.petclinic.availability.AvailabilityConflictException;
import org.springframework.samples.petclinic.availability.CalendarMutationCoordinator;
import org.springframework.samples.petclinic.availability.CalendarState;
import org.springframework.samples.petclinic.availability.CalendarStateRepository;
import org.springframework.samples.petclinic.availability.CapacityConflictService;
import org.springframework.samples.petclinic.availability.EffectiveAvailabilityService;
import org.springframework.samples.petclinic.scheduling.offer.Offer;
import org.springframework.samples.petclinic.scheduling.offer.OfferOrigin;
import org.springframework.samples.petclinic.scheduling.offer.OfferRepository;
import org.springframework.samples.petclinic.scheduling.offer.OfferState;
import org.springframework.samples.petclinic.scheduling.request.OfferExclusion;
import org.springframework.samples.petclinic.scheduling.request.OfferExclusionRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevision;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AssistedOfferService {

	private static final Logger log = LoggerFactory.getLogger(AssistedOfferService.class);

	private static final Duration HOLD_DURATION = Duration.ofMinutes(10);

	private final QueueItemRepository queueItemRepository;

	private final SchedulingRequestRepository requestRepository;

	private final OfferRepository offerRepository;

	private final OfferExclusionRepository offerExclusionRepository;

	private final CalendarMutationCoordinator calendarCoordinator;

	private final CalendarStateRepository calendarStateRepository;

	private final CapacityConflictService capacityConflictService;

	private final EffectiveAvailabilityService effectiveAvailabilityService;

	private final VetRepository vetRepository;

	private final AuditService auditService;

	private final OwnerHistoryService ownerHistoryService;

	private final Clock clock;

	public AssistedOfferService(QueueItemRepository queueItemRepository, SchedulingRequestRepository requestRepository,
			OfferRepository offerRepository, OfferExclusionRepository offerExclusionRepository,
			CalendarMutationCoordinator calendarCoordinator, CalendarStateRepository calendarStateRepository,
			CapacityConflictService capacityConflictService, EffectiveAvailabilityService effectiveAvailabilityService,
			VetRepository vetRepository, AuditService auditService, OwnerHistoryService ownerHistoryService,
			Clock clock) {
		this.queueItemRepository = queueItemRepository;
		this.requestRepository = requestRepository;
		this.offerRepository = offerRepository;
		this.offerExclusionRepository = offerExclusionRepository;
		this.calendarCoordinator = calendarCoordinator;
		this.calendarStateRepository = calendarStateRepository;
		this.capacityConflictService = capacityConflictService;
		this.effectiveAvailabilityService = effectiveAvailabilityService;
		this.vetRepository = vetRepository;
		this.auditService = auditService;
		this.ownerHistoryService = ownerHistoryService;
		this.clock = clock;
	}

	public record AssistedOfferCommand(Long queueItemId, Long actorAccountId, Integer vetId, Instant startAt,
			Instant endAt, String explanation) {
	}

	public Offer createAssistedOffer(AssistedOfferCommand cmd) {
		Objects.requireNonNull(cmd, "cmd must not be null");
		Objects.requireNonNull(cmd.queueItemId(), "queueItemId must not be null");
		Objects.requireNonNull(cmd.actorAccountId(), "actorAccountId must not be null");
		Objects.requireNonNull(cmd.vetId(), "vetId must not be null");
		Objects.requireNonNull(cmd.startAt(), "startAt must not be null");
		Objects.requireNonNull(cmd.endAt(), "endAt must not be null");

		QueueItem queueItem = this.queueItemRepository.findById(cmd.queueItemId())
			.orElseThrow(() -> new IllegalArgumentException("Queue item not found: " + cmd.queueItemId()));

		if (queueItem.getState() == QueueState.RESOLVED || queueItem.getState() == QueueState.CLOSED) {
			throw new IllegalStateException(
					"Cannot create offer for inactive queue item in state: " + queueItem.getState());
		}

		Vet vet = this.vetRepository.findById(cmd.vetId())
			.orElseThrow(() -> new IllegalArgumentException("Veterinarian not found: " + cmd.vetId()));

		SchedulingRequest request = queueItem.getRequest();
		WorkflowRevision workflowRevision = request.getCurrentWorkflowRevision();
		if (workflowRevision == null) {
			throw new IllegalStateException("No workflow revision found for request " + request.getId());
		}

		return this.calendarCoordinator.executeWithLock(() -> {
			Instant now = this.clock.instant();
			if (this.capacityConflictService.hasOverlappingBlocker(cmd.vetId(), request.getPetId(),
					request.getOwnerId(), cmd.startAt(), cmd.endAt())) {
				throw new AvailabilityConflictException("Selected appointment slot has a capacity conflict");
			}

			CalendarState calendarState = this.calendarStateRepository.findSingletonForUpdate().orElse(null);
			long currentCalRev = calendarState != null ? calendarState.getRevision() : 1L;

			String zoneId = this.effectiveAvailabilityService.getClinicPolicy().getZoneId();
			Instant expiresAt = now.plus(HOLD_DURATION);

			Offer offer = new Offer(request, workflowRevision, request.getOwnerId(), request.getPetId(), cmd.vetId(),
					OfferOrigin.STAFF_ASSISTED, cmd.startAt(), cmd.endAt(), zoneId, expiresAt, OfferState.HELD, null,
					currentCalRev, cmd.explanation() != null ? cmd.explanation().trim() : "Staff assisted offer");

			Offer savedOffer = this.offerRepository.save(offer);

			request.setState(RequestState.OFFERED);
			request.setUpdatedAt(now);
			this.requestRepository.save(request);

			queueItem.setState(QueueState.AWAITING_OWNER);
			queueItem.setAwaitingReason(AwaitingReason.PORTAL_OFFER);
			queueItem.setUpdatedAt(now);
			this.queueItemRepository.save(queueItem);

			this.auditService.recordEvent(cmd.actorAccountId(), "STAFF_ASSISTED_OFFER_CREATED", "QueueItem",
					queueItem.getId().toString(), "SUCCESS", UUID.randomUUID(), null, null);

			this.ownerHistoryService.recordOwnerHistory(request.getOwnerId(), request.getPetId(), request.getId(),
					"OFFER_CREATED",
					"Staff-assisted appointment offer held with Dr. " + vet.getLastName() + " until " + expiresAt,
					null);

			log.info("Created staff assisted held offer {} for queue item {} (expires at {})", savedOffer.getId(),
					queueItem.getId(), expiresAt);
			return savedOffer;
		});
	}

	public void handleAssistedOfferRejectionOrExpiry(Long offerId) {
		Offer offer = this.offerRepository.findById(offerId)
			.orElseThrow(() -> new IllegalArgumentException("Offer not found: " + offerId));

		if (offer.getOrigin() != OfferOrigin.STAFF_ASSISTED) {
			return;
		}

		Instant now = this.clock.instant();
		SchedulingRequest request = offer.getRequest();

		if (offer.getWorkflowRevision() != null) {
			OfferExclusion exclusion = new OfferExclusion(offer.getWorkflowRevision(), offer.getVetId(),
					offer.getStartAt(), offer.getEndAt(), offer.getId(), now);
			this.offerExclusionRepository.save(exclusion);
		}

		this.queueItemRepository.findByRequestId(request.getId()).ifPresent(queueItem -> {
			if (queueItem.getState() == QueueState.AWAITING_OWNER
					&& queueItem.getAwaitingReason() == AwaitingReason.PORTAL_OFFER) {
				queueItem.setState(QueueState.IN_REVIEW);
				queueItem.setAwaitingReason(null);
				queueItem.setUpdatedAt(now);
				this.queueItemRepository.save(queueItem);
			}
		});

		if (request.getState() == RequestState.OFFERED) {
			request.setState(RequestState.STAFF_HANDLING);
			request.setUpdatedAt(now);
			this.requestRepository.save(request);
		}
	}

}
