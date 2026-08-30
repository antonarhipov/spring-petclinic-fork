package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Instant;
import java.util.UUID;

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
@Table(name = "offers")
public class Offer {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "request_revision_id", nullable = false)
	private Long requestRevisionId;

	@Column(name = "execution_id")
	private UUID executionId;

	@Column(name = "veterinarian_id", nullable = false)
	private Integer veterinarianId;

	@Column(name = "start_at", nullable = false)
	private Instant startAt;

	@Column(name = "end_at", nullable = false)
	private Instant endAt;

	@Column(name = "duration_minutes", nullable = false)
	private int durationMinutes;

	@Column(nullable = false)
	private String source;

	@Column(nullable = false)
	private String classification;

	@Column(name = "public_explanation_code", nullable = false)
	private String publicExplanationCode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OfferStatus status;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "owner_rejection_reason")
	private String ownerRejectionReason;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	@Version
	private Integer version;

	public Long getId() {
		return this.id;
	}

	public Long getRequestRevisionId() {
		return this.requestRevisionId;
	}

	public void setRequestRevisionId(Long requestRevisionId) {
		this.requestRevisionId = requestRevisionId;
	}

	public UUID getExecutionId() {
		return this.executionId;
	}

	public void setExecutionId(UUID executionId) {
		this.executionId = executionId;
	}

	public Integer getVeterinarianId() {
		return this.veterinarianId;
	}

	public void setVeterinarianId(Integer veterinarianId) {
		this.veterinarianId = veterinarianId;
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

	public int getDurationMinutes() {
		return this.durationMinutes;
	}

	public void setDurationMinutes(int durationMinutes) {
		this.durationMinutes = durationMinutes;
	}

	public String getSource() {
		return this.source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getClassification() {
		return this.classification;
	}

	public void setClassification(String classification) {
		this.classification = classification;
	}

	public String getPublicExplanationCode() {
		return this.publicExplanationCode;
	}

	public void setPublicExplanationCode(String publicExplanationCode) {
		this.publicExplanationCode = publicExplanationCode;
	}

	public OfferStatus getStatus() {
		return this.status;
	}

	public void setStatus(OfferStatus status) {
		this.status = status;
	}

	public Instant getExpiresAt() {
		return this.expiresAt;
	}

	public void setExpiresAt(Instant expiresAt) {
		this.expiresAt = expiresAt;
	}

	public String getOwnerRejectionReason() {
		return this.ownerRejectionReason;
	}

	public void setOwnerRejectionReason(String ownerRejectionReason) {
		this.ownerRejectionReason = ownerRejectionReason;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getResolvedAt() {
		return this.resolvedAt;
	}

	public void setResolvedAt(Instant resolvedAt) {
		this.resolvedAt = resolvedAt;
	}

	public Integer getVersion() {
		return this.version;
	}

}
