package org.springframework.samples.petclinic.scheduling.request;

public enum RequestState {

	AWAITING_INTERPRETATION, AWAITING_REVIEW, READY_TO_MATCH, OFFERED, STAFF_HANDLING, CONFIRMED, WITHDRAWN, CLOSED;

	public boolean isTerminal() {
		return this == CONFIRMED || this == WITHDRAWN || this == CLOSED;
	}

	public boolean isPreConfirmation() {
		return !isTerminal();
	}

}
