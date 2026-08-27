package org.springframework.samples.petclinic.scheduling.dto;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.springframework.samples.petclinic.scheduling.model.Appointment;
import org.springframework.samples.petclinic.scheduling.model.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.model.BookingSource;
import org.springframework.samples.petclinic.scheduling.model.CareType;

public class AppointmentDto {

	private final Integer id;

	private final Integer ownerId;

	private final String ownerName;

	private final Integer petId;

	private final String petName;

	private final Integer vetId;

	private final String vetName;

	private final Instant startTime;

	private final Instant endTime;

	private final Integer durationMinutes;

	private final CareType careType;

	private final Integer requiredSpecialtyId;

	private final String requiredSpecialtyName;

	private final AppointmentStatus status;

	private final BookingSource bookingSource;

	private final Integer requestId;

	private final String offlineReason;

	private final String cancellationReason;

	private final Instant completedAt;

	private final String completionNotes;

	private final Long version;

	private final Integer visitId;

	public AppointmentDto(Integer id, Integer ownerId, String ownerName, Integer petId, String petName, Integer vetId,
			String vetName, Instant startTime, Instant endTime, Integer durationMinutes, CareType careType,
			Integer requiredSpecialtyId, String requiredSpecialtyName, AppointmentStatus status,
			BookingSource bookingSource, Integer requestId, String offlineReason, String cancellationReason,
			Instant completedAt, String completionNotes, Long version, Integer visitId) {
		this.id = id;
		this.ownerId = ownerId;
		this.ownerName = ownerName;
		this.petId = petId;
		this.petName = petName;
		this.vetId = vetId;
		this.vetName = vetName;
		this.startTime = startTime;
		this.endTime = endTime;
		this.durationMinutes = durationMinutes;
		this.careType = careType;
		this.requiredSpecialtyId = requiredSpecialtyId;
		this.requiredSpecialtyName = requiredSpecialtyName;
		this.status = status;
		this.bookingSource = bookingSource;
		this.requestId = requestId;
		this.offlineReason = offlineReason;
		this.cancellationReason = cancellationReason;
		this.completedAt = completedAt;
		this.completionNotes = completionNotes;
		this.version = version;
		this.visitId = visitId;
	}

	public static AppointmentDto from(Appointment appt) {
		if (appt == null) {
			return null;
		}
		String ownerName = appt.getOwner() != null
				? appt.getOwner().getFirstName() + " " + appt.getOwner().getLastName() : "";
		String petName = appt.getPet() != null ? appt.getPet().getName() : "";
		String vetName = appt.getVet() != null ? appt.getVet().getFirstName() + " " + appt.getVet().getLastName() : "";
		Integer duration = (appt.getStartTime() != null && appt.getEndTime() != null)
				? (int) Duration.between(appt.getStartTime(), appt.getEndTime()).toMinutes() : 0;
		Integer specialtyId = appt.getRequiredSpecialty() != null ? appt.getRequiredSpecialty().getId() : null;
		String specialtyName = appt.getRequiredSpecialty() != null ? appt.getRequiredSpecialty().getName() : null;
		Integer reqId = appt.getRequest() != null ? appt.getRequest().getId() : null;
		Integer visitId = appt.getVisit() != null ? appt.getVisit().getId() : null;

		return new AppointmentDto(appt.getId(), appt.getOwner() != null ? appt.getOwner().getId() : null, ownerName,
				appt.getPet() != null ? appt.getPet().getId() : null, petName,
				appt.getVet() != null ? appt.getVet().getId() : null, vetName, appt.getStartTime(), appt.getEndTime(),
				duration, appt.getCareType(), specialtyId, specialtyName, appt.getStatus(), appt.getBookingSource(),
				reqId, appt.getOfflineReason(), appt.getCancellationReason(), appt.getCompletedAt(),
				appt.getCompletionNotes(), appt.getVersion(), visitId);
	}

	public Integer getId() {
		return id;
	}

	public Integer getOwnerId() {
		return ownerId;
	}

	public String getOwnerName() {
		return ownerName;
	}

	public Integer getPetId() {
		return petId;
	}

	public String getPetName() {
		return petName;
	}

	public Integer getVetId() {
		return vetId;
	}

	public String getVetName() {
		return vetName;
	}

	public Instant getStartTime() {
		return startTime;
	}

	public Instant getEndTime() {
		return endTime;
	}

	public Integer getDurationMinutes() {
		return durationMinutes;
	}

	public CareType getCareType() {
		return careType;
	}

	public Integer getRequiredSpecialtyId() {
		return requiredSpecialtyId;
	}

	public String getRequiredSpecialtyName() {
		return requiredSpecialtyName;
	}

	public AppointmentStatus getStatus() {
		return status;
	}

	public BookingSource getBookingSource() {
		return bookingSource;
	}

	public Integer getRequestId() {
		return requestId;
	}

	public String getOfflineReason() {
		return offlineReason;
	}

	public String getCancellationReason() {
		return cancellationReason;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public String getCompletionNotes() {
		return completionNotes;
	}

	public Long getVersion() {
		return version;
	}

	public Integer getVisitId() {
		return visitId;
	}

}
