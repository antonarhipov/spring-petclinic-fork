package org.springframework.samples.petclinic.account;

public class DuplicateUsernameException extends RuntimeException {

	public DuplicateUsernameException() {
		super("Username is already in use");
	}

}
