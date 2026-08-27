package org.springframework.samples.petclinic.scheduling.dto;

import java.time.Instant;
import org.springframework.samples.petclinic.scheduling.model.AuditEventType;

public class AuditRecordDto {

	private Integer id;

	private AuditEventType eventType;

	private String actorUsername;

	private String actorRole;

	private String targetEntityType;

	private String targetEntityId;

	private String reasonCode;

	private Instant occurredAt;

	private String metadataJson;

	public AuditRecordDto() {
	}

	public AuditRecordDto(Integer id, AuditEventType eventType, String actorUsername, String actorRole,
			String targetEntityType, String targetEntityId, String reasonCode, Instant occurredAt,
			String metadataJson) {
		this.id = id;
		this.eventType = eventType;
		this.actorUsername = actorUsername;
		this.actorRole = actorRole;
		this.targetEntityType = targetEntityType;
		this.targetEntityId = targetEntityId;
		this.reasonCode = reasonCode;
		this.occurredAt = occurredAt;
		this.metadataJson = metadataJson;
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
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

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public void setOccurredAt(Instant occurredAt) {
		this.occurredAt = occurredAt;
	}

	public String getMetadataJson() {
		return metadataJson;
	}

	public void setMetadataJson(String metadataJson) {
		this.metadataJson = metadataJson;
	}

}
