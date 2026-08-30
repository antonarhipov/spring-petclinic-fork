package org.springframework.samples.petclinic.scheduling.availability;

import java.util.List;

public class AvailabilityConflictException extends RuntimeException {

	private final List<String> affectedReservations;

	public AvailabilityConflictException(List<String> affectedReservations) {
		super("CAPACITY_CONFLICT");
		this.affectedReservations = List.copyOf(affectedReservations);
	}

	public List<String> getAffectedReservations() {
		return this.affectedReservations;
	}

}
