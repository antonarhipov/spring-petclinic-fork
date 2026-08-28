package org.springframework.samples.petclinic.scheduling.availability;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.offer.AppointmentOffer;
import org.springframework.samples.petclinic.scheduling.offer.AppointmentOfferRepository;
import org.springframework.samples.petclinic.scheduling.offer.OfferService;
import org.springframework.samples.petclinic.scheduling.offer.OfferState;

@Service
public class AvailabilityConflictService {

	private final AppointmentRepository appointments;

	private final AppointmentOfferRepository offers;

	private final OfferService offerService;

	public AvailabilityConflictService(AppointmentRepository appointments, AppointmentOfferRepository offers,
			OfferService offerService) {
		this.appointments = appointments;
		this.offers = offers;
		this.offerService = offerService;
	}

	@Transactional(readOnly = true)
	public void assertChangeAllowed(Integer vetId, LocalDate start, LocalDate end) {
		boolean appointmentConflict = this.appointments.findAll()
			.stream()
			.filter(appointment -> appointment.getStatus() == AppointmentStatus.CONFIRMED
					&& appointment.getVet().getId().equals(vetId))
			.anyMatch(appointment -> within(appointment.getStartAt(), start, end));
		if (appointmentConflict) {
			throw new IllegalStateException("Resolve confirmed appointments before changing availability");
		}
		boolean heldOfferConflict = this.offers.findByState(OfferState.HELD)
			.stream()
			.anyMatch(offer -> offer.getVet().getId().equals(vetId) && within(offer.getStartAt(), start, end));
		if (heldOfferConflict) {
			throw new IllegalStateException("Release affected offers before changing availability");
		}
	}

	@Transactional(readOnly = true)
	public void assertClinicChangeAllowed(LocalDate start, LocalDate end) {
		boolean appointmentConflict = this.appointments.findAll()
			.stream()
			.filter(appointment -> appointment.getStatus() == AppointmentStatus.CONFIRMED)
			.anyMatch(appointment -> within(appointment.getStartAt(), start, end));
		boolean heldOfferConflict = this.offers.findByState(OfferState.HELD)
			.stream()
			.anyMatch(offer -> within(offer.getStartAt(), start, end));
		if (appointmentConflict || heldOfferConflict) {
			throw new IllegalStateException("Resolve affected appointments and offers before closing the clinic");
		}
	}

	@Transactional
	public void releaseHeldOffer(Integer offerId, String reason, Authentication actor) {
		AppointmentOffer offer = this.offers.findById(offerId).orElseThrow();
		this.offerService.releaseCurrent(offer.getRevision(), actor, reason);
	}

	private boolean within(Instant instant, LocalDate start, LocalDate end) {
		LocalDate date = instant.atZone(ZoneOffset.UTC).toLocalDate();
		return !date.isBefore(start) && !date.isAfter(end);
	}

}
