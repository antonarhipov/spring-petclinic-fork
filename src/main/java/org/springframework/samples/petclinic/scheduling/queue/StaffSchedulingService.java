package org.springframework.samples.petclinic.scheduling.queue;

import java.time.Instant;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentService;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentSource;
import org.springframework.samples.petclinic.scheduling.offer.AppointmentOffer;
import org.springframework.samples.petclinic.scheduling.offer.OfferService;

@Service
public class StaffSchedulingService {

	private final StaffQueueRepository queue;

	private final OfferService offers;

	private final AppointmentService appointments;

	public StaffSchedulingService(StaffQueueRepository queue, OfferService offers, AppointmentService appointments) {
		this.queue = queue;
		this.offers = offers;
		this.appointments = appointments;
	}

	@Transactional
	public AppointmentOffer offer(Integer queueItemId, Authentication actor) {
		StaffQueueItem item = claimed(queueItemId, actor);
		return this.offers.createOffer(item.getRequest().getCurrentRevision(), actor)
			.orElseThrow(() -> new IllegalStateException("No conflict-free appointment can be offered"));
	}

	@Transactional
	public Appointment book(Integer queueItemId, Integer vetId, Instant startAt, int duration, String agreement,
			String reason, Authentication actor) {
		StaffQueueItem item = claimed(queueItemId, actor);
		Appointment appointment = this.appointments.directBook(item.getRequest().getPet().getId(), vetId, startAt,
				duration, AppointmentSource.STAFF_ASSISTED, agreement, reason, actor);
		item.resolve("Appointment booked with owner agreement");
		return appointment;
	}

	private StaffQueueItem claimed(Integer itemId, Authentication actor) {
		StaffQueueItem item = this.queue.findById(itemId).orElseThrow();
		if (item.getClaimedBy() == null || !item.getClaimedBy().getUsername().equals(actor.getName())) {
			throw new IllegalStateException("Claim the queue item before working it");
		}
		return item;
	}

}
