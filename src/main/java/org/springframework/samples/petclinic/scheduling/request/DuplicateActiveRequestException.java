package org.springframework.samples.petclinic.scheduling.request;

public class DuplicateActiveRequestException extends IllegalStateException {

	private final int petId;

	public DuplicateActiveRequestException(int petId) {
		super("Pet " + petId + " already has an active scheduling request");
		this.petId = petId;
	}

	public int getPetId() {
		return this.petId;
	}

}
