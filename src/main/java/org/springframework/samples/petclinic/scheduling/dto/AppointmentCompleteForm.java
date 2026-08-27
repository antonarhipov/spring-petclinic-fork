package org.springframework.samples.petclinic.scheduling.dto;

import jakarta.validation.constraints.NotBlank;

public class AppointmentCompleteForm {

	@NotBlank
	private String completionNotes;

	public String getCompletionNotes() {
		return completionNotes;
	}

	public void setCompletionNotes(String completionNotes) {
		this.completionNotes = completionNotes;
	}

}
