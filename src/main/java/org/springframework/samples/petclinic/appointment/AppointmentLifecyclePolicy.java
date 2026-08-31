package org.springframework.samples.petclinic.appointment;

import java.time.Instant;

import org.springframework.stereotype.Component;

@Component
public class AppointmentLifecyclePolicy {

	public boolean canComplete(Appointment appointment, Instant now) {
		if (appointment == null || appointment.getEndAt() == null || now == null) {
			return false;
		}
		return appointment.getBookingState() == BookingState.CONFIRMED
				&& appointment.getOutcomeState() == OutcomeState.PENDING && !appointment.getEndAt().isAfter(now);
	}

	public boolean canRecordNoShow(Appointment appointment, Instant now) {
		if (appointment == null || appointment.getEndAt() == null || now == null) {
			return false;
		}
		return appointment.getBookingState() == BookingState.CONFIRMED
				&& appointment.getOutcomeState() == OutcomeState.PENDING && !appointment.getEndAt().isAfter(now);
	}

	public boolean canCorrectOutcome(Appointment appointment, Instant now) {
		if (appointment == null) {
			return false;
		}
		return appointment.getBookingState() == BookingState.CONFIRMED
				&& appointment.getOutcomeState() != OutcomeState.PENDING;
	}

	public boolean canReschedule(Appointment appointment, Instant now) {
		if (appointment == null || now == null) {
			return false;
		}
		return appointment.getBookingState() == BookingState.CONFIRMED
				&& appointment.getOutcomeState() == OutcomeState.PENDING && appointment.getStartAt().isAfter(now);
	}

	public boolean canStaffCancel(Appointment appointment, Instant now) {
		if (appointment == null || now == null) {
			return false;
		}
		return appointment.getBookingState() == BookingState.CONFIRMED
				&& appointment.getOutcomeState() == OutcomeState.PENDING && appointment.getStartAt().isAfter(now);
	}

	public boolean canOwnerCancel(Appointment appointment, Instant now) {
		if (appointment == null || now == null) {
			return false;
		}
		return appointment.getBookingState() == BookingState.CONFIRMED
				&& appointment.getOutcomeState() == OutcomeState.PENDING && appointment.getStartAt().isAfter(now);
	}

}
