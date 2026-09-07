package org.springframework.samples.petclinic.scheduling.appointment;

public class IllegalAppointmentTransitionException extends IllegalStateException {

	private final AppointmentStatus status;

	private final String action;

	public IllegalAppointmentTransitionException(AppointmentStatus status, String action) {
		super("Action " + action + " is not allowed while appointment is " + status);
		this.status = status;
		this.action = action;
	}

	public AppointmentStatus getStatus() {
		return this.status;
	}

	public String getAction() {
		return this.action;
	}

}
