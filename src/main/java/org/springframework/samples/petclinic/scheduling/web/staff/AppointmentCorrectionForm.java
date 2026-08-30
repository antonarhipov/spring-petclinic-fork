package org.springframework.samples.petclinic.scheduling.web.staff;

public class AppointmentCorrectionForm {

	private String targetStatus;

	private String note;

	public String getTargetStatus() {
		return this.targetStatus;
	}

	public void setTargetStatus(String targetStatus) {
		this.targetStatus = targetStatus;
	}

	public String getNote() {
		return this.note;
	}

	public void setNote(String note) {
		this.note = note;
	}

}
