package org.springframework.samples.petclinic.scheduling.offer;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;

@Service
public class ReservationService {

	private final ReservationBlockRepository blocks;

	public ReservationService(ReservationBlockRepository blocks) {
		this.blocks = blocks;
	}

	@Transactional
	public void hold(AppointmentOffer offer) {
		Integer petId = offer.getRevision().getRequest().getPet().getId();
		reserve("VETERINARIAN", offer.getVet().getId(), "OFFER", offer.getId(), offer.getStartAt(),
				offer.getDurationMinutes(), ReservationState.HELD, offer.getExpiresAt());
		reserve("PET", petId, "OFFER", offer.getId(), offer.getStartAt(), offer.getDurationMinutes(),
				ReservationState.HELD, offer.getExpiresAt());
		this.blocks.flush();
	}

	@Transactional
	public void promote(AppointmentOffer offer, Appointment appointment) {
		this.blocks.findByOwnerTypeAndOwnerId("OFFER", offer.getId())
			.forEach(block -> block.confirmForAppointment(appointment.getId()));
	}

	@Transactional
	public void releaseOffer(Integer offerId) {
		this.blocks.deleteByOwnerTypeAndOwnerId("OFFER", offerId);
	}

	@Transactional
	public void reserveConfirmed(Appointment appointment) {
		reserve("VETERINARIAN", appointment.getVet().getId(), "APPOINTMENT", appointment.getId(),
				appointment.getStartAt(), appointment.getDurationMinutes(), ReservationState.CONFIRMED, null);
		reserve("PET", appointment.getPet().getId(), "APPOINTMENT", appointment.getId(), appointment.getStartAt(),
				appointment.getDurationMinutes(), ReservationState.CONFIRMED, null);
		this.blocks.flush();
	}

	@Transactional
	public void releaseAppointment(Integer appointmentId) {
		this.blocks.deleteByOwnerTypeAndOwnerId("APPOINTMENT", appointmentId);
	}

	private void reserve(String resourceType, Integer resourceId, String ownerType, Integer ownerId, Instant start,
			int duration, ReservationState state, Instant expiresAt) {
		for (Instant slot = start; slot
			.isBefore(start.plus(Duration.ofMinutes(duration))); slot = slot.plus(Duration.ofMinutes(15))) {
			this.blocks
				.save(new ReservationBlock(resourceType, resourceId, slot, ownerType, ownerId, state, expiresAt));
		}
	}

}
