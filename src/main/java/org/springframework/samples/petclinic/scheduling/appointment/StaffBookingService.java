package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Clock;
import java.time.Instant;

import org.springframework.samples.petclinic.scheduling.matching.CandidateSlot;
import org.springframework.samples.petclinic.scheduling.queue.QueueState;
import org.springframework.samples.petclinic.scheduling.queue.StaffQueueRepository;
import org.springframework.samples.petclinic.scheduling.request.ActivePetRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffBookingService {

	private final SchedulingRequestRepository requests;

	private final AppointmentRepository appointments;

	private final OfferRepository offers;

	private final HoldRepository holds;

	private final ReservationBlockRepository blocks;

	private final ReservationService reservations;

	private final ActivePetRequestRepository activePets;

	private final StaffQueueRepository queueItems;

	private final BookingAuthorizationPolicy policy;

	private final Clock clock;

	public StaffBookingService(SchedulingRequestRepository requests, AppointmentRepository appointments,
			OfferRepository offers, HoldRepository holds, ReservationBlockRepository blocks,
			ReservationService reservations, ActivePetRequestRepository activePets, StaffQueueRepository queueItems,
			BookingAuthorizationPolicy policy, Clock clock) {
		this.requests = requests;
		this.appointments = appointments;
		this.offers = offers;
		this.holds = holds;
		this.blocks = blocks;
		this.reservations = reservations;
		this.activePets = activePets;
		this.queueItems = queueItems;
		this.policy = policy;
		this.clock = clock;
	}

	@Transactional
	public Appointment bookDirect(Long requestId, CandidateSlot slot, BookingAuthorization authorization,
			String staffReason) {
		this.policy.validate(authorization);
		SchedulingRequest request = this.requests.findById(requestId).orElseThrow();
		this.reservations.acquireExact(requestId, slot, "STAFF", "STAFF_DIRECT");
		Offer offer = this.offers
			.findFirstByRequestRevisionIdAndStatusOrderByCreatedAtDesc(request.getActiveRequestRevisionId(),
					OfferStatus.HELD)
			.orElseThrow();
		Hold hold = this.holds.findByOfferId(offer.getId()).orElseThrow();
		Instant now = Instant.now(this.clock);
		Appointment appointment = new Appointment();
		appointment.setPetId(request.getPetId());
		appointment.setVeterinarianId(slot.veterinarianId());
		appointment.setRequestId(requestId);
		appointment.setOfferId(offer.getId());
		appointment.setStartAt(slot.startAt());
		appointment.setEndAt(slot.endAt());
		appointment.setStatus(AppointmentStatus.CONFIRMED);
		appointment.setAuthorizationBasis(authorization.basis());
		appointment.setAgreementRecordedBy(authorization.agreementRecordedBy());
		appointment.setAgreementAt(authorization.agreementAt());
		appointment.setAgreementMethod(authorization.agreementMethod());
		appointment.setSupportingVisitId(authorization.supportingVisitId());
		appointment.setStaffReasonCategory(staffReason);
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
			item.setState(QueueState.RESOLVED);
			item.setResolutionCode("BOOKED");
			item.setUpdatedAt(now);
		});
		return appointment;
	}

}
