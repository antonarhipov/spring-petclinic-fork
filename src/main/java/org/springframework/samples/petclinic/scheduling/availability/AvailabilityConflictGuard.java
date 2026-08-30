package org.springframework.samples.petclinic.scheduling.availability;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.appointment.Hold;
import org.springframework.samples.petclinic.scheduling.appointment.HoldRepository;
import org.springframework.samples.petclinic.scheduling.appointment.HoldStatus;
import org.springframework.samples.petclinic.scheduling.appointment.Offer;
import org.springframework.samples.petclinic.scheduling.appointment.OfferRepository;
import org.springframework.stereotype.Service;

@Service
public class AvailabilityConflictGuard {

	private final AppointmentRepository appointments;

	private final HoldRepository holds;

	private final OfferRepository offers;

	private final AvailabilityRepository policies;

	public AvailabilityConflictGuard(AppointmentRepository appointments, HoldRepository holds, OfferRepository offers,
			AvailabilityRepository policies) {
		this.appointments = appointments;
		this.holds = holds;
		this.offers = offers;
		this.policies = policies;
	}

	public List<String> findConflicts(Integer veterinarianId, LocalDate startDate, LocalDate endDate) {
		ZoneId zone = ZoneId.of(this.policies.currentPolicy().getZoneId());
		Instant rangeStart = startDate.atStartOfDay(zone).toInstant();
		Instant rangeEnd = endDate.plusDays(1).atStartOfDay(zone).toInstant();
		List<String> affected = new ArrayList<>();
		for (Appointment appointment : this.appointments.findByStatus(AppointmentStatus.CONFIRMED)) {
			if (veterinarianId != null && !veterinarianId.equals(appointment.getVeterinarianId())) {
				continue;
			}
			if (overlaps(appointment.getStartAt(), appointment.getEndAt(), rangeStart, rangeEnd)) {
				affected.add("APPOINTMENT:" + appointment.getId());
			}
		}
		for (Hold hold : this.holds.findByState(HoldStatus.ACTIVE)) {
			Offer offer = this.offers.findById(hold.getOfferId()).orElse(null);
			if (offer == null) {
				continue;
			}
			if (veterinarianId != null && offer.getVeterinarianId() != veterinarianId) {
				continue;
			}
			if (overlaps(offer.getStartAt(), offer.getEndAt(), rangeStart, rangeEnd)) {
				affected.add("HOLD:" + hold.getId());
			}
		}
		return affected;
	}

	public void assertNoConflicts(Integer veterinarianId, LocalDate startDate, LocalDate endDate) {
		List<String> affected = findConflicts(veterinarianId, startDate, endDate);
		if (!affected.isEmpty()) {
			throw new AvailabilityConflictException(affected);
		}
	}

	private boolean overlaps(Instant start, Instant end, Instant rangeStart, Instant rangeEnd) {
		return start.isBefore(rangeEnd) && end.isAfter(rangeStart);
	}

}
