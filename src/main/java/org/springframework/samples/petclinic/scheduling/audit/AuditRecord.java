package org.springframework.samples.petclinic.scheduling.audit;

import java.time.Instant;

import org.springframework.samples.petclinic.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "scheduling_audit_records")
public class AuditRecord extends BaseEntity {

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(name = "correlation_id")
	private String correlationId;

	@Column(nullable = false)
	private String actor;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AuditAction action;

	@Column(name = "target_type", nullable = false)
	private String targetType;

	@Column(name = "target_id", nullable = false)
	private String targetId;

	@Column(name = "prior_value")
	private String priorValue;

	@Column(name = "resulting_value")
	private String resultingValue;

	@Column
	private String reason;

	protected AuditRecord() {
	}

	AuditRecord(Instant occurredAt, String correlationId, String actor, AuditAction action, String targetType,
			String targetId, String priorValue, String resultingValue, String reason) {
		this.occurredAt = occurredAt;
		this.correlationId = correlationId;
		this.actor = actor;
		this.action = action;
		this.targetType = targetType;
		this.targetId = targetId;
		this.priorValue = priorValue;
		this.resultingValue = resultingValue;
		this.reason = reason;
	}

	public AuditAction getAction() {
		return this.action;
	}

	public Instant getOccurredAt() {
		return this.occurredAt;
	}

	public String getActor() {
		return this.actor;
	}

	public String getTargetType() {
		return this.targetType;
	}

	public String getTargetId() {
		return this.targetId;
	}

	public String getPriorValue() {
		return this.priorValue;
	}

	public String getResultingValue() {
		return this.resultingValue;
	}

	public String getReason() {
		return this.reason;
	}

}
