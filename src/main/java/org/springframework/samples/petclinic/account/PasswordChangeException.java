package org.springframework.samples.petclinic.account;

public class PasswordChangeException extends RuntimeException {

	public PasswordChangeException(String message) {
		super(message);
	}

}
