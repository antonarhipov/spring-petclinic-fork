package org.springframework.samples.petclinic.scheduling.model;

import java.time.Instant;

import org.springframework.samples.petclinic.model.BaseEntity;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.vet.Specialty;
import org.springframework.samples.petclinic.vet.Vet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "scheduling_requests")
public class SchedulingRequest extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id", nullable = false)
	private Owner owner;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "pet_id", nullable = false)
	private Pet pet;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 40)
	private RequestStatus status = RequestStatus.AWAITING_CONSENT;

	@Column(name = "original_text", length = 2000)
	private String originalText;

	@Column(name = "language", nullable = false, length = 10)
	private String language = "en";

	@Column(name = "urgent", nullable = false)
	private boolean urgent = false;

	@Enumerated(EnumType.STRING)
	@Column(name = "care_type", length = 30)
	private CareType careType;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "required_specialty_id")
	private Specialty requiredSpecialty;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "preferred_vet_id")
	private Vet preferredVet;

	@Column(name = "duration_minutes")
	private Integer durationMinutes;

	@Column(name = "preferred_start_window")
	private Instant preferredStartWindow;

	@Column(name = "preferred_end_window")
	private Instant preferredEndWindow;

	@Column(name = "owner_horizon_end")
	private Instant ownerHorizonEnd;

	@Column(name = "first_queued_at")
	private Instant firstQueuedAt;

	@Column(name = "fallback_deadline")
	private Instant fallbackDeadline;

	@Column(name = "last_clarification_reason", length = 255)
	private String lastClarificationReason;

	@Column(name = "clarification_count", nullable = false)
	private int clarificationCount = 0;

	@Column(name = "ai_summary", length = 1000)
	private String aiSummary;

	@Column(name = "full_ai_response", columnDefinition = "TEXT")
	private String fullAiResponse;

	@Column(name = "retention_deadline")
	private Instant retentionDeadline;

	@Column(name = "purged_at")
	private Instant purgedAt;

	@Column(name = "consent_given_at")
	private Instant consentGivenAt;

	@Column(name = "recovery_required", nullable = false)
	private boolean recoveryRequired = false;

	@Version
	@Column(name = "version", nullable = false)
	private Long version = 0L;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public SchedulingRequest() {
	}

	@PrePersist
	public void onPrePersist() {
		Instant now = Instant.now();
		if (this.createdAt == null) {
			this.createdAt = now;
		}
		if (this.updatedAt == null) {
			this.updatedAt = now;
		}
	}

	@PreUpdate
	public void onPreUpdate() {
		this.updatedAt = Instant.now();
	}

	public Owner getOwner() {
		return owner;
	}

	public void setOwner(Owner owner) {
		this.owner = owner;
	}

	public Pet getPet() {
		return pet;
	}

	public void setPet(Pet pet) {
		this.pet = pet;
	}

	public RequestStatus getStatus() {
		return status;
	}

	public void setStatus(RequestStatus status) {
		this.status = status;
	}

	public String getOriginalText() {
		return originalText;
	}

	public void setOriginalText(String originalText) {
		this.originalText = originalText;
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public boolean isUrgent() {
		return urgent;
	}

	public void setUrgent(boolean urgent) {
		this.urgent = urgent;
	}

	public CareType getCareType() {
		return careType;
	}

	public void setCareType(CareType careType) {
		this.careType = careType;
	}

	public Specialty getRequiredSpecialty() {
		return requiredSpecialty;
	}

	public void setRequiredSpecialty(Specialty requiredSpecialty) {
		this.requiredSpecialty = requiredSpecialty;
	}

	public Vet getPreferredVet() {
		return preferredVet;
	}

	public void setPreferredVet(Vet preferredVet) {
		this.preferredVet = preferredVet;
	}

	public Integer getDurationMinutes() {
		return durationMinutes;
	}

	public void setDurationMinutes(Integer durationMinutes) {
		this.durationMinutes = durationMinutes;
	}

	public Instant getPreferredStartWindow() {
		return preferredStartWindow;
	}

	public void setPreferredStartWindow(Instant preferredStartWindow) {
		this.preferredStartWindow = preferredStartWindow;
	}

	public Instant getPreferredEndWindow() {
		return preferredEndWindow;
	}

	public void setPreferredEndWindow(Instant preferredEndWindow) {
		this.preferredEndWindow = preferredEndWindow;
	}

	public Instant getOwnerHorizonEnd() {
		return ownerHorizonEnd;
	}

	public void setOwnerHorizonEnd(Instant ownerHorizonEnd) {
		this.ownerHorizonEnd = ownerHorizonEnd;
	}

	public Instant getFirstQueuedAt() {
		return firstQueuedAt;
	}

	public void setFirstQueuedAt(Instant firstQueuedAt) {
		this.firstQueuedAt = firstQueuedAt;
	}

	public Instant getFallbackDeadline() {
		return fallbackDeadline;
	}

	public void setFallbackDeadline(Instant fallbackDeadline) {
		this.fallbackDeadline = fallbackDeadline;
	}

	public String getLastClarificationReason() {
		return lastClarificationReason;
	}

	public void setLastClarificationReason(String lastClarificationReason) {
		this.lastClarificationReason = lastClarificationReason;
	}

	public int getClarificationCount() {
		return clarificationCount;
	}

	public void setClarificationCount(int clarificationCount) {
		this.clarificationCount = clarificationCount;
	}

	public String getAiSummary() {
		return aiSummary;
	}

	public void setAiSummary(String aiSummary) {
		this.aiSummary = aiSummary;
	}

	public String getFullAiResponse() {
		return fullAiResponse;
	}

	public void setFullAiResponse(String fullAiResponse) {
		this.fullAiResponse = fullAiResponse;
	}

	public Instant getRetentionDeadline() {
		return retentionDeadline;
	}

	public void setRetentionDeadline(Instant retentionDeadline) {
		this.retentionDeadline = retentionDeadline;
	}

	public Instant getPurgedAt() {
		return purgedAt;
	}

	public void setPurgedAt(Instant purgedAt) {
		this.purgedAt = purgedAt;
	}

	public Instant getConsentGivenAt() {
		return consentGivenAt;
	}

	public void setConsentGivenAt(Instant consentGivenAt) {
		this.consentGivenAt = consentGivenAt;
	}

	public boolean isRecoveryRequired() {
		return recoveryRequired;
	}

	public void setRecoveryRequired(boolean recoveryRequired) {
		this.recoveryRequired = recoveryRequired;
	}

	public Long getVersion() {
		return version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	@Override
	public String toString() {
		return "SchedulingRequest{" + "id=" + getId() + ", ownerId=" + (owner != null ? owner.getId() : null)
				+ ", petId=" + (pet != null ? pet.getId() : null) + ", status=" + status + ", urgent=" + urgent
				+ ", careType=" + careType + ", durationMinutes=" + durationMinutes + ", version=" + version + '}';
	}

}
