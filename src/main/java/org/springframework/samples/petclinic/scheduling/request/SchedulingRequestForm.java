package org.springframework.samples.petclinic.scheduling.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class SchedulingRequestForm {

	@NotNull(message = "Please select a pet")
	private Integer petId;

	@NotBlank(message = "Please describe your visit reason and preferred times")
	@Size(max = 2000, message = "Request description must not exceed 2000 characters")
	private String prose;

	private boolean aiConsent;

	public Integer getPetId() {
		return this.petId;
	}

	public void setPetId(Integer petId) {
		this.petId = petId;
	}

	public String getProse() {
		return this.prose;
	}

	public void setProse(String prose) {
		this.prose = prose;
	}

	public boolean isAiConsent() {
		return this.aiConsent;
	}

	public void setAiConsent(boolean aiConsent) {
		this.aiConsent = aiConsent;
	}

}
