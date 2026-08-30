package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Clock;
import java.time.Instant;

import org.springframework.samples.petclinic.scheduling.queue.QueueState;
import org.springframework.samples.petclinic.scheduling.queue.StaffQueueRepository;
import org.springframework.samples.petclinic.scheduling.request.ActivePetRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.OwnerResourceNotFoundException;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.system.StaleStateException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfferAcceptanceService {

	private final SchedulingRequestRepository requests;

	private final OfferRepository offers;

	private final HoldRepository holds;

	private final ReservationBlockRepository blocks;

	private final AppointmentRepository appointments;

	private final ActivePetRequestRepository activePets;

	private final StaffQueueRepository queueItems;

	private final ReservationService reservations;

	private final Clock clock;

	public OfferAcceptanceService(SchedulingRequestRepository requests, OfferRepository offers, HoldRepository holds,
			ReservationBlockRepository blocks, AppointmentRepository appointments,
			ActivePetRequestRepository activePets, StaffQueueRepository queueItems, ReservationService reservations,
			Clock clock) {
		this.requests = requests;
		this.offers = offers;
		this.holds = holds;
		this.blocks = blocks;
		this.appointments = appointments;
		this.activePets = activePets;
		this.queueItems = queueItems;
		this.reservations = reservations;
		this.clock = clock;
	}

	@Transactional
	public Appointment accept(Long requestId, Integer ownerId, Long offerId, Integer expectedVersion) {
		SchedulingRequest request = this.requests.findByIdAndOwnerId(requestId, ownerId)
			.orElseThrow(OwnerResourceNotFoundException::new);
		if (expectedVersion != null && request.getVersion() != null && !expectedVersion.equals(request.getVersion())) {
			throw new StaleStateException("stale", request.getState().name(), null);
		}
		Offer offer = this.offers.findById(offerId).orElseThrow(OwnerResourceNotFoundException::new);
		Hold hold = this.holds.findByOfferId(offerId).orElseThrow(OwnerResourceNotFoundException::new);
		Instant now = Instant.now(this.clock);
		if (hold.getState() != HoldStatus.ACTIVE || !hold.getExpiresAt().isAfter(now)
				|| offer.getStatus() != OfferStatus.HELD) {
			expireIfNeeded(request, offer, hold, now);
			throw new OfferUnavailableException();
		}
		Appointment appointment = new Appointment();
		appointment.setPetId(request.getPetId());
		appointment.setVeterinarianId(offer.getVeterinarianId());
		appointment.setRequestId(requestId);
		appointment.setOfferId(offerId);
		appointment.setStartAt(offer.getStartAt());
		appointment.setEndAt(offer.getEndAt());
		appointment.setStatus(AppointmentStatus.CONFIRMED);
		appointment.setAuthorizationBasis("OWNER_AGREEMENT");
		appointment.setCreatedAt(now);
		appointment.setUpdatedAt(now);
		appointment = this.appointments.saveAndFlush(appointment);
		for (ReservationBlock block : this.blocks.findByHoldId(hold.getId())) {
			block.setHoldId(null);
			block.setAppointmentId(appointment.getId());
		}
		hold.setState(HoldStatus.CONSUMED);
		hold.setResolvedAt(now);
		offer.setStatus(OfferStatus.ACCEPTED);
		offer.setResolvedAt(now);
		request.setState(RequestState.CONFIRMED);
		request.setOwnerStatusCode("CONFIRMED");
		request.setUpdatedAt(now);
		this.activePets.findByRequestId(requestId).ifPresent(this.activePets::delete);
		this.queueItems.findByRequestId(requestId).ifPresent(item -> {
			item.setState(QueueState.CLOSED);
			item.setUpdatedAt(now);
		});
		return appointment;
	}

	@Transactional
	public void reject(Long requestId, Integer ownerId, Long offerId, Integer expectedVersion) {
		SchedulingRequest request = this.requests.findByIdAndOwnerId(requestId, ownerId)
			.orElseThrow(OwnerResourceNotFoundException::new);
		if (expectedVersion != null && request.getVersion() != null && !expectedVersion.equals(request.getVersion())) {
			throw new StaleStateException("stale", request.getState().name(), null);
		}
		Offer offer = this.offers.findById(offerId).orElseThrow(OwnerResourceNotFoundException::new);
		Hold hold = this.holds.findByOfferId(offerId).orElseThrow(OwnerResourceNotFoundException::new);
		this.reservations.release(hold, offer, "REJECTED", OfferStatus.REJECTED);
		request.setState(RequestState.READY_FOR_SUGGESTION);
		request.setOwnerStatusCode("READY_FOR_SUGGESTION");
		request.setUpdatedAt(Instant.now(this.clock));
	}

	@Transactional
	public void expireIfNeeded(SchedulingRequest request, Offer offer, Hold hold, Instant now) {
		if (hold.getState() == HoldStatus.ACTIVE && !hold.getExpiresAt().isAfter(now)) {
			this.reservations.release(hold, offer, "EXPIRED", OfferStatus.EXPIRED);
			if (request.getState() == RequestState.OFFER_HELD) {
				request.setState(RequestState.READY_FOR_SUGGESTION);
				request.setOwnerStatusCode("READY_FOR_SUGGESTION");
				request.setUpdatedAt(now);
			}
		}
	}

}
