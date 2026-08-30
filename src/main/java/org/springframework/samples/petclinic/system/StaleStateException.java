package org.springframework.samples.petclinic.system;

public class StaleStateException extends RuntimeException {

	private final Object currentState;

	private final Object submittedValues;

	public StaleStateException(String message, Object currentState, Object submittedValues) {
		super(message);
		this.currentState = currentState;
		this.submittedValues = submittedValues;
	}

	public Object getCurrentState() {
		return this.currentState;
	}

	public Object getSubmittedValues() {
		return this.submittedValues;
	}

}
