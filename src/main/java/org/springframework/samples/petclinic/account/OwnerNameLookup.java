package org.springframework.samples.petclinic.account;

public interface OwnerNameLookup {

	OwnerName findOwnerName(Integer ownerId);

	record OwnerName(String firstName, String lastName) {
	}

}
