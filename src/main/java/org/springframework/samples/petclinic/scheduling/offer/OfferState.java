package org.springframework.samples.petclinic.scheduling.offer;

public enum OfferState {

	HELD, ACCEPTED, REJECTED, EXPIRED, RELEASED;

	public boolean isActiveHold() {
		return this == HELD;
	}

}
