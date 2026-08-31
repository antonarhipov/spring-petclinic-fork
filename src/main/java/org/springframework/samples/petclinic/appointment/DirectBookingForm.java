package org.springframework.samples.petclinic.appointment;

import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.format.annotation.DateTimeFormat;

public class DirectBookingForm {

	private Integer ownerId;

	private Integer petId;

	private Integer vetId;

	@DateTimeFormat(pattern = "yyyy-MM-dd")
	private LocalDate date = LocalDate.now();

	@DateTimeFormat(pattern = "HH:mm")
	private LocalTime startTime = LocalTime.of(10, 0);

	private Integer durationMinutes = 30;

	private boolean ownerAgreementRecorded = true;

	private String agreementMedium = "PHONE";

	private String internalReason;

	public Integer getOwnerId() {
		return this.ownerId;
	}

	public void setOwnerId(Integer ownerId) {
		this.ownerId = ownerId;
	}

	public Integer getPetId() {
		return this.petId;
	}

	public void setPetId(Integer petId) {
		this.petId = petId;
	}

	public Integer getVetId() {
		return this.vetId;
	}

	public void setVetId(Integer vetId) {
		this.vetId = vetId;
	}

	public LocalDate getDate() {
		return this.date;
	}

	public void setDate(LocalDate date) {
		this.date = date;
	}

	public LocalTime getStartTime() {
		return this.startTime;
	}

	public void setStartTime(LocalTime startTime) {
		this.startTime = startTime;
	}

	public Integer getDurationMinutes() {
		return this.durationMinutes;
	}

	public void setDurationMinutes(Integer durationMinutes) {
		this.durationMinutes = durationMinutes;
	}

	public boolean isOwnerAgreementRecorded() {
		return this.ownerAgreementRecorded;
	}

	public void setOwnerAgreementRecorded(boolean ownerAgreementRecorded) {
		this.ownerAgreementRecorded = ownerAgreementRecorded;
	}

	public String getAgreementMedium() {
		return this.agreementMedium;
	}

	public void setAgreementMedium(String agreementMedium) {
		this.agreementMedium = agreementMedium;
	}

	public String getInternalReason() {
		return this.internalReason;
	}

	public void setInternalReason(String internalReason) {
		this.internalReason = internalReason;
	}

}
