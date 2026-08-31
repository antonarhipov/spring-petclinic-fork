package org.springframework.samples.petclinic.audit;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_events")
public class AuditEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "actor_account_id")
	private Long actorAccountId;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	@Column(name = "action", nullable = false, length = 100)
	private String action;

	@Column(name = "target_type", nullable = false, length = 100)
	private String targetType;

	@Column(name = "target_id", nullable = false, length = 100)
	private String targetId;

	@Column(name = "outcome", nullable = false, length = 50)
	private String outcome;

	@Column(name = "correlation_id", nullable = false)
	private UUID correlationId;

	@Column(name = "command_id")
	private UUID commandId;

	@Column(name = "payload_id")
	private Long payloadId;

	@PrePersist
	protected void onCreate() {
		if (this.occurredAt == null) {
			this.occurredAt = Instant.now();
		}
	}

	@PreUpdate
	@PreRemove
	protected void preventMutation() {
		throw new IllegalStateException("Audit events are append-only and cannot be updated or deleted");
	}

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getActorAccountId() {
		return this.actorAccountId;
	}

	public void setActorAccountId(Long actorAccountId) {
		this.actorAccountId = actorAccountId;
	}

	public Instant getOccurredAt() {
		return this.occurredAt;
	}

	public void setOccurredAt(Instant occurredAt) {
		this.occurredAt = occurredAt;
	}

	public String getAction() {
		return this.action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public String getTargetType() {
		return this.targetType;
	}

	public void setTargetType(String targetType) {
		this.targetType = targetType;
	}

	public String getTargetId() {
		return this.targetId;
	}

	public void setTargetId(String targetId) {
		this.targetId = targetId;
	}

	public String getOutcome() {
		return this.outcome;
	}

	public void setOutcome(String outcome) {
		this.outcome = outcome;
	}

	public UUID getCorrelationId() {
		return this.correlationId;
	}

	public void setCorrelationId(UUID correlationId) {
		this.correlationId = correlationId;
	}

	public UUID getCommandId() {
		return this.commandId;
	}

	public void setCommandId(UUID commandId) {
		this.commandId = commandId;
	}

	public Long getPayloadId() {
		return this.payloadId;
	}

	public void setPayloadId(Long payloadId) {
		this.payloadId = payloadId;
	}

}
