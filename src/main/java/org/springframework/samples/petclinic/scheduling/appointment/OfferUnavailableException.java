package org.springframework.samples.petclinic.scheduling.appointment;

public class OfferUnavailableException extends RuntimeException {

	public OfferUnavailableException() {
		super("OFFER_UNAVAILABLE");
	}

}
