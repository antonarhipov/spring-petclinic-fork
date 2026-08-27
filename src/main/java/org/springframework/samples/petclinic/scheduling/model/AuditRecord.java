package org.springframework.samples.petclinic.scheduling.model;

import java.time.Instant;

import org.springframework.samples.petclinic.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_records")
public class AuditRecord extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 50)
	private AuditEventType eventType;

	@Column(name = "actor_username", nullable = false, length = 100)
	private String actorUsername;

	@Column(name = "actor_role", nullable = false, length = 20)
	private String actorRole;

	@Column(name = "target_entity_type", nullable = false, length = 50)
	private String targetEntityType;

	@Column(name = "target_entity_id", nullable = false, length = 50)
	private String targetEntityId;

	@Column(name = "reason_code", length = 50)
	private String reasonCode;

	@Column(name = "metadata_json", columnDefinition = "TEXT")
	private String metadataJson;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	public AuditRecord() {
	}

	@PrePersist
	public void onPrePersist() {
		if (this.occurredAt == null) {
			this.occurredAt = Instant.now();
		}
	}

	public AuditEventType getEventType() {
		return eventType;
	}

	public void setEventType(AuditEventType eventType) {
		this.eventType = eventType;
	}

	public String getActorUsername() {
		return actorUsername;
	}

	public void setActorUsername(String actorUsername) {
		this.actorUsername = actorUsername;
	}

	public String getActorRole() {
		return actorRole;
	}

	public void setActorRole(String actorRole) {
		this.actorRole = actorRole;
	}

	public String getTargetEntityType() {
		return targetEntityType;
	}

	public void setTargetEntityType(String targetEntityType) {
		this.targetEntityType = targetEntityType;
	}

	public String getTargetEntityId() {
		return targetEntityId;
	}

	public void setTargetEntityId(String targetEntityId) {
		this.targetEntityId = targetEntityId;
	}

	public String getReasonCode() {
		return reasonCode;
	}

	public void setReasonCode(String reasonCode) {
		this.reasonCode = reasonCode;
	}

	public String getMetadataJson() {
		return metadataJson;
	}

	public void setMetadataJson(String metadataJson) {
		this.metadataJson = metadataJson;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public void setOccurredAt(Instant occurredAt) {
		this.occurredAt = occurredAt;
	}

	@Override
	public String toString() {
		return "AuditRecord{" + "id=" + getId() + ", eventType=" + eventType + ", actorUsername='" + actorUsername
				+ '\'' + ", actorRole='" + actorRole + '\'' + ", targetEntityType='" + targetEntityType + '\''
				+ ", targetEntityId='" + targetEntityId + '\'' + ", reasonCode='" + reasonCode + '\'' + ", occurredAt="
				+ occurredAt + '}';
	}

}
