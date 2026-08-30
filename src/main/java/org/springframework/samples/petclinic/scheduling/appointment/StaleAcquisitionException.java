package org.springframework.samples.petclinic.scheduling.appointment;

public class StaleAcquisitionException extends RuntimeException {

	public StaleAcquisitionException() {
		super("STALE_ACQUISITION");
	}

}
