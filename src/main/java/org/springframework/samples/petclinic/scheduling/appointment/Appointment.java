package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "appointments")
public class Appointment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "pet_id", nullable = false)
	private Integer petId;

	@Column(name = "veterinarian_id", nullable = false)
	private Integer veterinarianId;

	@Column(name = "request_id")
	private Long requestId;

	@Column(name = "offer_id")
	private Long offerId;

	@Column(name = "start_at", nullable = false)
	private Instant startAt;

	@Column(name = "end_at", nullable = false)
	private Instant endAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AppointmentStatus status;

	@Column(name = "authorization_basis", nullable = false)
	private String authorizationBasis;

	@Column(name = "agreement_recorded_by")
	private Long agreementRecordedBy;

	@Column(name = "agreement_at")
	private Instant agreementAt;

	@Column(name = "agreement_method")
	private String agreementMethod;

	@Column(name = "supporting_visit_id")
	private Integer supportingVisitId;

	@Column(name = "staff_reason_category")
	private String staffReasonCategory;

	@Column(name = "staff_reason_note")
	private String staffReasonNote;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private Integer version;

	public Long getId() {
		return this.id;
	}

	public Integer getPetId() {
		return this.petId;
	}

	public void setPetId(Integer petId) {
		this.petId = petId;
	}

	public Integer getVeterinarianId() {
		return this.veterinarianId;
	}

	public void setVeterinarianId(Integer veterinarianId) {
		this.veterinarianId = veterinarianId;
	}

	public Long getRequestId() {
		return this.requestId;
	}

	public void setRequestId(Long requestId) {
		this.requestId = requestId;
	}

	public Long getOfferId() {
		return this.offerId;
	}

	public void setOfferId(Long offerId) {
		this.offerId = offerId;
	}

	public Instant getStartAt() {
		return this.startAt;
	}

	public void setStartAt(Instant startAt) {
		this.startAt = startAt;
	}

	public Instant getEndAt() {
		return this.endAt;
	}

	public void setEndAt(Instant endAt) {
		this.endAt = endAt;
	}

	public AppointmentStatus getStatus() {
		return this.status;
	}

	public void setStatus(AppointmentStatus status) {
		this.status = status;
	}

	public String getAuthorizationBasis() {
		return this.authorizationBasis;
	}

	public void setAuthorizationBasis(String authorizationBasis) {
		this.authorizationBasis = authorizationBasis;
	}

	public void setAgreementRecordedBy(Long agreementRecordedBy) {
		this.agreementRecordedBy = agreementRecordedBy;
	}

	public void setAgreementAt(Instant agreementAt) {
		this.agreementAt = agreementAt;
	}

	public void setAgreementMethod(String agreementMethod) {
		this.agreementMethod = agreementMethod;
	}

	public void setSupportingVisitId(Integer supportingVisitId) {
		this.supportingVisitId = supportingVisitId;
	}

	public void setStaffReasonCategory(String staffReasonCategory) {
		this.staffReasonCategory = staffReasonCategory;
	}

	public String getStaffReasonCategory() {
		return this.staffReasonCategory;
	}

	public String getStaffReasonNote() {
		return this.staffReasonNote;
	}

	public Integer getSupportingVisitId() {
		return this.supportingVisitId;
	}

	public void setStaffReasonNote(String staffReasonNote) {
		this.staffReasonNote = staffReasonNote;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	public Integer getVersion() {
		return this.version;
	}

}
