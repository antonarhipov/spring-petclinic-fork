package org.springframework.samples.petclinic.scheduling.config;

public class ConfigurationValidationException extends RuntimeException {

	private final String messageKey;

	public ConfigurationValidationException(String messageKey) {
		super(messageKey);
		this.messageKey = messageKey;
	}

	public String getMessageKey() {
		return this.messageKey;
	}

}
