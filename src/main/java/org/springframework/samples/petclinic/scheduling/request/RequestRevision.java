package org.springframework.samples.petclinic.scheduling.request;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "request_revisions")
public class RequestRevision {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "request_id", nullable = false)
	private Long requestId;

	@Column(nullable = false)
	private int sequence;

	@Column(name = "interpretation_id", nullable = false)
	private Long interpretationId;

	@Column(nullable = false)
	private String status;

	@Column(name = "visit_reason", nullable = false, length = 500)
	private String visitReason;

	@Column(name = "duration_minutes", nullable = false)
	private int durationMinutes;

	@Column(name = "care_type", nullable = false)
	private String careType;

	@Column(name = "specialty_id")
	private Integer specialtyId;

	@Column(nullable = false)
	private String urgency;

	@Column(name = "preferred_veterinarian_id")
	private Integer preferredVeterinarianId;

	@Column(name = "veterinarian_preference_strength", nullable = false)
	private String veterinarianPreferenceStrength;

	@Column(name = "clinic_policy_version", nullable = false)
	private long clinicPolicyVersion;

	@Column(name = "clinic_zone_id", nullable = false)
	private String clinicZoneId;

	@Column(name = "rejection_expiry_count", nullable = false)
	private int rejectionExpiryCount;

	@Column(name = "confirmed_by_account_id")
	private Long confirmedByAccountId;

	@Column(name = "confirmed_at")
	private Instant confirmedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Version
	private Integer version;

	public Long getId() {
		return this.id;
	}

	public Long getRequestId() {
		return this.requestId;
	}

	public void setRequestId(Long requestId) {
		this.requestId = requestId;
	}

	public int getSequence() {
		return this.sequence;
	}

	public void setSequence(int sequence) {
		this.sequence = sequence;
	}

	public Long getInterpretationId() {
		return this.interpretationId;
	}

	public void setInterpretationId(Long interpretationId) {
		this.interpretationId = interpretationId;
	}

	public String getStatus() {
		return this.status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getVisitReason() {
		return this.visitReason;
	}

	public void setVisitReason(String visitReason) {
		this.visitReason = visitReason;
	}

	public int getDurationMinutes() {
		return this.durationMinutes;
	}

	public void setDurationMinutes(int durationMinutes) {
		this.durationMinutes = durationMinutes;
	}

	public String getCareType() {
		return this.careType;
	}

	public void setCareType(String careType) {
		this.careType = careType;
	}

	public Integer getSpecialtyId() {
		return this.specialtyId;
	}

	public void setSpecialtyId(Integer specialtyId) {
		this.specialtyId = specialtyId;
	}

	public String getUrgency() {
		return this.urgency;
	}

	public void setUrgency(String urgency) {
		this.urgency = urgency;
	}

	public Integer getPreferredVeterinarianId() {
		return this.preferredVeterinarianId;
	}

	public void setPreferredVeterinarianId(Integer preferredVeterinarianId) {
		this.preferredVeterinarianId = preferredVeterinarianId;
	}

	public String getVeterinarianPreferenceStrength() {
		return this.veterinarianPreferenceStrength;
	}

	public void setVeterinarianPreferenceStrength(String veterinarianPreferenceStrength) {
		this.veterinarianPreferenceStrength = veterinarianPreferenceStrength;
	}

	public long getClinicPolicyVersion() {
		return this.clinicPolicyVersion;
	}

	public void setClinicPolicyVersion(long clinicPolicyVersion) {
		this.clinicPolicyVersion = clinicPolicyVersion;
	}

	public String getClinicZoneId() {
		return this.clinicZoneId;
	}

	public void setClinicZoneId(String clinicZoneId) {
		this.clinicZoneId = clinicZoneId;
	}

	public int getRejectionExpiryCount() {
		return this.rejectionExpiryCount;
	}

	public void setRejectionExpiryCount(int rejectionExpiryCount) {
		this.rejectionExpiryCount = rejectionExpiryCount;
	}

	public Long getConfirmedByAccountId() {
		return this.confirmedByAccountId;
	}

	public void setConfirmedByAccountId(Long confirmedByAccountId) {
		this.confirmedByAccountId = confirmedByAccountId;
	}

	public Instant getConfirmedAt() {
		return this.confirmedAt;
	}

	public void setConfirmedAt(Instant confirmedAt) {
		this.confirmedAt = confirmedAt;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Integer getVersion() {
		return this.version;
	}

}
