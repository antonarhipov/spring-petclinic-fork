package org.springframework.samples.petclinic.scheduling.request;

import java.time.Clock;
import java.time.Instant;

import org.springframework.samples.petclinic.scheduling.appointment.Hold;
import org.springframework.samples.petclinic.scheduling.appointment.HoldRepository;
import org.springframework.samples.petclinic.scheduling.appointment.HoldStatus;
import org.springframework.samples.petclinic.scheduling.appointment.Offer;
import org.springframework.samples.petclinic.scheduling.appointment.OfferRepository;
import org.springframework.samples.petclinic.scheduling.appointment.OfferStatus;
import org.springframework.samples.petclinic.scheduling.appointment.ReservationService;
import org.springframework.samples.petclinic.scheduling.queue.QueueState;
import org.springframework.samples.petclinic.scheduling.queue.StaffQueueRepository;
import org.springframework.samples.petclinic.system.StaleStateException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestWithdrawalService {

	private final SchedulingRequestRepository requests;

	private final ActivePetRequestRepository activePets;

	private final HoldRepository holds;

	private final OfferRepository offers;

	private final ReservationService reservations;

	private final StaffQueueRepository queueItems;

	private final RequestRecoveryAuditService audit;

	private final Clock clock;

	public RequestWithdrawalService(SchedulingRequestRepository requests, ActivePetRequestRepository activePets,
			HoldRepository holds, OfferRepository offers, ReservationService reservations,
			StaffQueueRepository queueItems, RequestRecoveryAuditService audit, Clock clock) {
		this.requests = requests;
		this.activePets = activePets;
		this.holds = holds;
		this.offers = offers;
		this.reservations = reservations;
		this.queueItems = queueItems;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional
	public void withdraw(Long requestId, Integer ownerId, Long accountId, Integer expectedVersion) {
		SchedulingRequest request = this.requests.findByIdAndOwnerId(requestId, ownerId)
			.orElseThrow(OwnerResourceNotFoundException::new);
		if (expectedVersion != null && request.getVersion() != null && !expectedVersion.equals(request.getVersion())) {
			throw new StaleStateException("stale", request.getState().name(), null);
		}
		if (request.getState() == RequestState.CONFIRMED || request.getState() == RequestState.CLOSED) {
			throw new IllegalStateException("INVALID_STATE");
		}
		Instant now = Instant.now(this.clock);
		for (Hold hold : this.holds.findByRequestId(requestId)) {
			if (hold.getState() == HoldStatus.ACTIVE) {
				Offer offer = this.offers.findById(hold.getOfferId()).orElseThrow();
				this.reservations.release(hold, offer, "WITHDRAWN", OfferStatus.RELEASED);
			}
		}
		this.activePets.findByRequestId(requestId).ifPresent(this.activePets::delete);
		this.queueItems.findByRequestId(requestId).ifPresent(item -> {
			item.setState(QueueState.CLOSED);
			item.setResolutionCode("WITHDRAWN");
			item.setUpdatedAt(now);
		});
		request.setState(RequestState.CLOSED);
		request.setOwnerStatusCode("CLOSED");
		request.setClosureOutcome("WITHDRAWN");
		request.setClosedAt(now);
		request.setUpdatedAt(now);
		this.audit.withdrawn(requestId, accountId);
	}

}
