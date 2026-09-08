package org.springframework.samples.petclinic.scheduling.request;

public class StaffSlotUnavailableException extends IllegalStateException {

	private final String messageKey;

	public StaffSlotUnavailableException(String messageKey) {
		super(messageKey);
		this.messageKey = messageKey;
	}

	public String getMessageKey() {
		return this.messageKey;
	}

}
