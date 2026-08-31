package org.springframework.samples.petclinic.availability;

import java.util.Collections;
import java.util.List;
import org.springframework.samples.petclinic.appointment.Appointment;

public class AvailabilityConflictException extends RuntimeException {

	private final List<Appointment> conflictingAppointments;

	public AvailabilityConflictException(String message) {
		super(message);
		this.conflictingAppointments = Collections.emptyList();
	}

	public AvailabilityConflictException(String message, List<Appointment> conflictingAppointments) {
		super(message);
		this.conflictingAppointments = conflictingAppointments != null ? conflictingAppointments
				: Collections.emptyList();
	}

	public List<Appointment> getConflictingAppointments() {
		return this.conflictingAppointments;
	}

}
