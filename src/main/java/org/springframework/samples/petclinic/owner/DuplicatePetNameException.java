package org.springframework.samples.petclinic.owner;

class DuplicatePetNameException extends RuntimeException {

	DuplicatePetNameException() {
		super("duplicate pet name");
	}

}
