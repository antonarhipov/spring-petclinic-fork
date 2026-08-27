package org.springframework.samples.petclinic.scheduling.dto;

import jakarta.validation.constraints.NotBlank;

public class AppointmentCancelForm {

	private String cancellationReason;

	public String getCancellationReason() {
		return cancellationReason;
	}

	public void setCancellationReason(String cancellationReason) {
		this.cancellationReason = cancellationReason;
	}

}
