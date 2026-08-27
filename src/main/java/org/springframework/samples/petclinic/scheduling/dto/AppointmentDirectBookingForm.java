package org.springframework.samples.petclinic.scheduling.dto;

import org.springframework.samples.petclinic.scheduling.model.CareType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class AppointmentDirectBookingForm {

	@NotNull
	private Integer ownerId;

	@NotNull
	private Integer petId;

	@NotNull
	private Integer vetId;

	@NotBlank
	private String startDateTime;

	@NotNull
	@Positive
	private Integer durationMinutes;

	@NotNull
	private CareType careType = CareType.GENERAL;

	private Integer requiredSpecialtyId;

	@NotBlank
	private String offlineReason;

	public Integer getOwnerId() {
		return ownerId;
	}

	public void setOwnerId(Integer ownerId) {
		this.ownerId = ownerId;
	}

	public Integer getPetId() {
		return petId;
	}

	public void setPetId(Integer petId) {
		this.petId = petId;
	}

	public Integer getVetId() {
		return vetId;
	}

	public void setVetId(Integer vetId) {
		this.vetId = vetId;
	}

	public String getStartDateTime() {
		return startDateTime;
	}

	public void setStartDateTime(String startDateTime) {
		this.startDateTime = startDateTime;
	}

	public Integer getDurationMinutes() {
		return durationMinutes;
	}

	public void setDurationMinutes(Integer durationMinutes) {
		this.durationMinutes = durationMinutes;
	}

	public CareType getCareType() {
		return careType;
	}

	public void setCareType(CareType careType) {
		this.careType = careType;
	}

	public Integer getRequiredSpecialtyId() {
		return requiredSpecialtyId;
	}

	public void setRequiredSpecialtyId(Integer requiredSpecialtyId) {
		this.requiredSpecialtyId = requiredSpecialtyId;
	}

	public String getOfflineReason() {
		return offlineReason;
	}

	public void setOfflineReason(String offlineReason) {
		this.offlineReason = offlineReason;
	}

}
