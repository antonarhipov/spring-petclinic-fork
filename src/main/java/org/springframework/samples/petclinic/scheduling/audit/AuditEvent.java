package org.springframework.samples.petclinic.scheduling.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_events")
public class AuditEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "actor_type", nullable = false)
	private String actorType;

	@Column(name = "actor_account_id")
	private Long actorAccountId;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(nullable = false)
	private String action;

	@Column(name = "target_type", nullable = false)
	private String targetType;

	@Column(name = "target_id", nullable = false)
	private String targetId;

	@Column(name = "request_id")
	private Long requestId;

	@Column(name = "before_json")
	private String beforeJson;

	@Column(name = "after_json")
	private String afterJson;

	@Column(name = "reason_json")
	private String reasonJson;

	public Long getId() {
		return this.id;
	}

	public String getAction() {
		return this.action;
	}

	public String getActorType() {
		return this.actorType;
	}

	public Long getActorAccountId() {
		return this.actorAccountId;
	}

	public Instant getOccurredAt() {
		return this.occurredAt;
	}

	public String getTargetType() {
		return this.targetType;
	}

	public String getTargetId() {
		return this.targetId;
	}

	public Long getRequestId() {
		return this.requestId;
	}

	public void setActorType(String actorType) {
		this.actorType = actorType;
	}

	public void setActorAccountId(Long actorAccountId) {
		this.actorAccountId = actorAccountId;
	}

	public void setOccurredAt(Instant occurredAt) {
		this.occurredAt = occurredAt;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public void setTargetType(String targetType) {
		this.targetType = targetType;
	}

	public void setTargetId(String targetId) {
		this.targetId = targetId;
	}

	public void setRequestId(Long requestId) {
		this.requestId = requestId;
	}

	public void setBeforeJson(String beforeJson) {
		this.beforeJson = beforeJson;
	}

	public void setAfterJson(String afterJson) {
		this.afterJson = afterJson;
	}

	public String getBeforeJson() {
		return this.beforeJson;
	}

	public String getAfterJson() {
		return this.afterJson;
	}

}
