package org.springframework.samples.petclinic.availability;

import java.time.LocalDate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.samples.petclinic.shared.persistence.SchedulingEntity;

@Entity
@Table(name = "clinic_closures")
public class ClinicClosure extends SchedulingEntity {

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Column(name = "owner_reason", nullable = false, length = 255)
	private String ownerReason;

	@Column(name = "protected_note_payload_id")
	private Long protectedNotePayloadId;

	public ClinicClosure() {
	}

	public ClinicClosure(LocalDate startDate, LocalDate endDate, String ownerReason) {
		this.startDate = startDate;
		this.endDate = endDate;
		this.ownerReason = ownerReason;
	}

	public LocalDate getStartDate() {
		return this.startDate;
	}

	public void setStartDate(LocalDate startDate) {
		this.startDate = startDate;
	}

	public LocalDate getEndDate() {
		return this.endDate;
	}

	public void setEndDate(LocalDate endDate) {
		this.endDate = endDate;
	}

	public String getOwnerReason() {
		return this.ownerReason;
	}

	public void setOwnerReason(String ownerReason) {
		this.ownerReason = ownerReason;
	}

	public Long getProtectedNotePayloadId() {
		return this.protectedNotePayloadId;
	}

	public void setProtectedNotePayloadId(Long protectedNotePayloadId) {
		this.protectedNotePayloadId = protectedNotePayloadId;
	}

}
