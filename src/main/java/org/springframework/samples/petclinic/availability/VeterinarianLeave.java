package org.springframework.samples.petclinic.availability;

import java.time.LocalDate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.samples.petclinic.shared.persistence.SchedulingEntity;

@Entity
@Table(name = "veterinarian_leave")
public class VeterinarianLeave extends SchedulingEntity {

	@Column(name = "vet_id", nullable = false)
	private Integer vetId;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Column(name = "reason_category", nullable = false, length = 64)
	private String reasonCategory;

	@Column(name = "protected_note_payload_id")
	private Long protectedNotePayloadId;

	public VeterinarianLeave() {
	}

	public VeterinarianLeave(Integer vetId, LocalDate startDate, LocalDate endDate, String reasonCategory) {
		this.vetId = vetId;
		this.startDate = startDate;
		this.endDate = endDate;
		this.reasonCategory = reasonCategory;
	}

	public Integer getVetId() {
		return this.vetId;
	}

	public void setVetId(Integer vetId) {
		this.vetId = vetId;
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

	public String getReasonCategory() {
		return this.reasonCategory;
	}

	public void setReasonCategory(String reasonCategory) {
		this.reasonCategory = reasonCategory;
	}

	public Long getProtectedNotePayloadId() {
		return this.protectedNotePayloadId;
	}

	public void setProtectedNotePayloadId(Long protectedNotePayloadId) {
		this.protectedNotePayloadId = protectedNotePayloadId;
	}

}
