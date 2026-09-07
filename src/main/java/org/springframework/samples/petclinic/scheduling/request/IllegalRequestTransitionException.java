package org.springframework.samples.petclinic.scheduling.request;

public class IllegalRequestTransitionException extends IllegalStateException {

	private final RequestState state;

	private final String action;

	public IllegalRequestTransitionException(RequestState state, String action) {
		super("Action " + action + " is not allowed while request is " + state);
		this.state = state;
		this.action = action;
	}

	public RequestState getState() {
		return this.state;
	}

	public String getAction() {
		return this.action;
	}

}
