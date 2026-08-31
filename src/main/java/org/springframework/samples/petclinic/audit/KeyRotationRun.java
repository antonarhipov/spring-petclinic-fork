package org.springframework.samples.petclinic.audit;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "key_rotation_runs")
public class KeyRotationRun {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "source_key_id", length = 100)
	private String sourceKeyId;

	@Column(name = "target_key_id", nullable = false, length = 100)
	private String targetKeyId;

	@Column(name = "state", nullable = false, length = 24)
	private String state = "PENDING";

	@Column(name = "last_payload_id")
	private Long lastPayloadId;

	@Column(name = "processed_count", nullable = false)
	private long processedCount = 0;

	@Column(name = "failure_count", nullable = false)
	private long failureCount = 0;

	@Column(name = "lease_token")
	private UUID leaseToken;

	@Column(name = "lease_until")
	private Instant leaseUntil;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "failure_category", length = 100)
	private String failureCategory;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@PrePersist
	protected void onCreate() {
		Instant now = Instant.now();
		if (this.createdAt == null) {
			this.createdAt = now;
		}
		if (this.updatedAt == null) {
			this.updatedAt = now;
		}
	}

	@PreUpdate
	protected void onUpdate() {
		this.updatedAt = Instant.now();
	}

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getSourceKeyId() {
		return this.sourceKeyId;
	}

	public void setSourceKeyId(String sourceKeyId) {
		this.sourceKeyId = sourceKeyId;
	}

	public String getTargetKeyId() {
		return this.targetKeyId;
	}

	public void setTargetKeyId(String targetKeyId) {
		this.targetKeyId = targetKeyId;
	}

	public String getState() {
		return this.state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public Long getLastPayloadId() {
		return this.lastPayloadId;
	}

	public void setLastPayloadId(Long lastPayloadId) {
		this.lastPayloadId = lastPayloadId;
	}

	public long getProcessedCount() {
		return this.processedCount;
	}

	public void setProcessedCount(long processedCount) {
		this.processedCount = processedCount;
	}

	public long getFailureCount() {
		return this.failureCount;
	}

	public void setFailureCount(long failureCount) {
		this.failureCount = failureCount;
	}

	public UUID getLeaseToken() {
		return this.leaseToken;
	}

	public void setLeaseToken(UUID leaseToken) {
		this.leaseToken = leaseToken;
	}

	public Instant getLeaseUntil() {
		return this.leaseUntil;
	}

	public void setLeaseUntil(Instant leaseUntil) {
		this.leaseUntil = leaseUntil;
	}

	public Instant getStartedAt() {
		return this.startedAt;
	}

	public void setStartedAt(Instant startedAt) {
		this.startedAt = startedAt;
	}

	public Instant getCompletedAt() {
		return this.completedAt;
	}

	public void setCompletedAt(Instant completedAt) {
		this.completedAt = completedAt;
	}

	public String getFailureCategory() {
		return this.failureCategory;
	}

	public void setFailureCategory(String failureCategory) {
		this.failureCategory = failureCategory;
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

}
