package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityRepository;
import org.springframework.samples.petclinic.scheduling.matching.CandidateSlot;
import org.springframework.samples.petclinic.scheduling.matching.SlotScorePolicy;
import org.springframework.samples.petclinic.scheduling.matching.SlotSelectionSnapshot;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {

	private final SchedulingRequestRepository requests;

	private final OfferRepository offers;

	private final HoldRepository holds;

	private final ReservationBlockRepository blocks;

	private final AvailabilityRepository policies;

	private final OccupancyQueryService occupancy;

	private final Clock clock;

	public ReservationService(SchedulingRequestRepository requests, OfferRepository offers, HoldRepository holds,
			ReservationBlockRepository blocks, AvailabilityRepository policies, OccupancyQueryService occupancy,
			Clock clock) {
		this.requests = requests;
		this.offers = offers;
		this.holds = holds;
		this.blocks = blocks;
		this.policies = policies;
		this.occupancy = occupancy;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public HoldAcquisitionOutcome acquire(Long requestId, UUID executionId, CandidateSlot slot,
			SlotSelectionSnapshot snapshot, String publicExplanationCode) {
		SchedulingRequest request = this.requests.findById(requestId).orElseThrow();
		if (request.getState() != RequestState.MATCHING) {
			return HoldAcquisitionOutcome.SUPERSEDED;
		}
		if (!SlotScorePolicy.baseEligible(snapshot, slot)) {
			return HoldAcquisitionOutcome.STALE;
		}
		boolean occupied = this.occupancy.activeBlocks()
			.stream()
			.anyMatch(block -> (block.resourceType() == ReservationResourceType.VETERINARIAN
					&& block.resourceId() == slot.veterinarianId() && !block.blockStart().isBefore(slot.startAt())
					&& block.blockStart().isBefore(slot.endAt()))
					|| (block.resourceType() == ReservationResourceType.PET && block.resourceId() == request.getPetId()
							&& !block.blockStart().isBefore(slot.startAt())
							&& block.blockStart().isBefore(slot.endAt())));
		if (occupied) {
			return HoldAcquisitionOutcome.STALE;
		}
		Instant now = Instant.now(this.clock);
		Instant expiresAt = now.plus(Duration.ofMinutes(this.policies.currentPolicy().getHoldDurationMinutes()));
		Offer offer = new Offer();
		offer.setRequestRevisionId(request.getActiveRequestRevisionId());
		offer.setExecutionId(executionId);
		offer.setVeterinarianId(slot.veterinarianId());
		offer.setStartAt(slot.startAt());
		offer.setEndAt(slot.endAt());
		offer.setDurationMinutes(snapshot.durationMinutes());
		offer.setSource("TIMEFOLD");
		offer.setClassification(slot.preferenceClass());
		offer.setPublicExplanationCode(publicExplanationCode);
		offer.setStatus(OfferStatus.HELD);
		offer.setExpiresAt(expiresAt);
		offer.setCreatedAt(now);
		offer = this.offers.save(offer);
		Hold hold = new Hold();
		hold.setOfferId(offer.getId());
		hold.setRequestId(requestId);
		hold.setState(HoldStatus.ACTIVE);
		hold.setExpiresAt(expiresAt);
		hold.setCreatedAt(now);
		hold = this.holds.saveAndFlush(hold);
		try {
			Instant cursor = slot.startAt();
			while (cursor.isBefore(slot.endAt())) {
				saveBlock(ReservationResourceType.VETERINARIAN, slot.veterinarianId(), cursor, hold.getId());
				saveBlock(ReservationResourceType.PET, request.getPetId(), cursor, hold.getId());
				cursor = cursor.plus(Duration.ofMinutes(15));
			}
			this.blocks.flush();
		}
		catch (RuntimeException ex) {
			if (ex instanceof DataIntegrityViolationException
					|| ex.getCause() instanceof DataIntegrityViolationException) {
				throw new StaleAcquisitionException();
			}
			throw ex;
		}
		request.setState(RequestState.OFFER_HELD);
		request.setOwnerStatusCode("OFFER_HELD");
		request.setUpdatedAt(now);
		return HoldAcquisitionOutcome.HELD;
	}

	@Transactional
	public HoldAcquisitionOutcome acquireExact(Long requestId, CandidateSlot slot, String source,
			String publicExplanationCode) {
		SchedulingRequest request = this.requests.findById(requestId).orElseThrow();
		if (request.getState() != RequestState.STAFF_HANDLING && request.getState() != RequestState.MATCHING) {
			return HoldAcquisitionOutcome.SUPERSEDED;
		}
		Instant now = Instant.now(this.clock);
		Instant expiresAt = now.plus(Duration.ofMinutes(this.policies.currentPolicy().getHoldDurationMinutes()));
		Offer offer = new Offer();
		offer.setRequestRevisionId(request.getActiveRequestRevisionId());
		offer.setVeterinarianId(slot.veterinarianId());
		offer.setStartAt(slot.startAt());
		offer.setEndAt(slot.endAt());
		offer.setDurationMinutes((int) Duration.between(slot.startAt(), slot.endAt()).toMinutes());
		offer.setSource(source);
		offer.setClassification(slot.preferenceClass());
		offer.setPublicExplanationCode(publicExplanationCode);
		offer.setStatus(OfferStatus.HELD);
		offer.setExpiresAt(expiresAt);
		offer.setCreatedAt(now);
		offer = this.offers.save(offer);
		Hold hold = new Hold();
		hold.setOfferId(offer.getId());
		hold.setRequestId(requestId);
		hold.setState(HoldStatus.ACTIVE);
		hold.setExpiresAt(expiresAt);
		hold.setCreatedAt(now);
		hold = this.holds.saveAndFlush(hold);
		try {
			Instant cursor = slot.startAt();
			while (cursor.isBefore(slot.endAt())) {
				saveBlock(ReservationResourceType.VETERINARIAN, slot.veterinarianId(), cursor, hold.getId());
				saveBlock(ReservationResourceType.PET, request.getPetId(), cursor, hold.getId());
				cursor = cursor.plus(Duration.ofMinutes(15));
			}
			this.blocks.flush();
		}
		catch (RuntimeException ex) {
			if (ex instanceof DataIntegrityViolationException
					|| ex.getCause() instanceof DataIntegrityViolationException) {
				throw new StaleAcquisitionException();
			}
			throw ex;
		}
		request.setState(RequestState.OFFER_HELD);
		request.setOwnerStatusCode("OFFER_HELD");
		request.setUpdatedAt(now);
		return HoldAcquisitionOutcome.HELD;
	}

	@Transactional
	public void release(Hold hold, Offer offer, String reason, OfferStatus offerStatus) {
		Instant now = Instant.now(this.clock);
		this.blocks.deleteAll(this.blocks.findByHoldId(hold.getId()));
		hold.setState(reason.equals("EXPIRED") ? HoldStatus.EXPIRED : HoldStatus.RELEASED);
		hold.setReleaseReason(reason);
		hold.setResolvedAt(now);
		offer.setStatus(offerStatus);
		offer.setResolvedAt(now);
	}

	private void saveBlock(ReservationResourceType type, int resourceId, Instant start, Long holdId) {
		ReservationBlock block = new ReservationBlock();
		block.setResourceType(type);
		block.setResourceId(resourceId);
		block.setBlockStart(start);
		block.setHoldId(holdId);
		this.blocks.save(block);
	}

}
