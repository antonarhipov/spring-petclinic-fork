package org.springframework.samples.petclinic.scheduling.availability;

public class PolicyValidationException extends RuntimeException {

	public PolicyValidationException(String message) {
		super(message);
	}

}
