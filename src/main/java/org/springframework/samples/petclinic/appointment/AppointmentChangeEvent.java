package org.springframework.samples.petclinic.appointment;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "appointment_change_events")
public class AppointmentChangeEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "appointment_id", nullable = false)
	private Long appointmentId;

	@Column(name = "actor_account_id")
	private Long actorAccountId;

	@Column(name = "actor_role", nullable = false, length = 32)
	private String actorRole;

	@Column(name = "event_type", nullable = false, length = 64)
	private String eventType;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(name = "owner_agreement_recorded", nullable = false)
	private boolean ownerAgreementRecorded = false;

	@Column(name = "agreement_medium", length = 32)
	private String agreementMedium;

	@Column(name = "protected_reason_payload_id")
	private Long protectedReasonPayloadId;

	@Column(name = "protected_snapshot_payload_id")
	private Long protectedSnapshotPayloadId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	protected void onCreate() {
		Instant now = Instant.now();
		if (this.occurredAt == null) {
			this.occurredAt = now;
		}
		if (this.createdAt == null) {
			this.createdAt = now;
		}
	}

	public AppointmentChangeEvent() {
	}

	public AppointmentChangeEvent(Long appointmentId, Long actorAccountId, String actorRole, String eventType,
			boolean ownerAgreementRecorded, String agreementMedium) {
		this.appointmentId = appointmentId;
		this.actorAccountId = actorAccountId;
		this.actorRole = actorRole;
		this.eventType = eventType;
		this.ownerAgreementRecorded = ownerAgreementRecorded;
		this.agreementMedium = agreementMedium;
	}

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getAppointmentId() {
		return this.appointmentId;
	}

	public void setAppointmentId(Long appointmentId) {
		this.appointmentId = appointmentId;
	}

	public Long getActorAccountId() {
		return this.actorAccountId;
	}

	public void setActorAccountId(Long actorAccountId) {
		this.actorAccountId = actorAccountId;
	}

	public String getActorRole() {
		return this.actorRole;
	}

	public void setActorRole(String actorRole) {
		this.actorRole = actorRole;
	}

	public String getEventType() {
		return this.eventType;
	}

	public void setEventType(String eventType) {
		this.eventType = eventType;
	}

	public Instant getOccurredAt() {
		return this.occurredAt;
	}

	public void setOccurredAt(Instant occurredAt) {
		this.occurredAt = occurredAt;
	}

	public boolean isOwnerAgreementRecorded() {
		return this.ownerAgreementRecorded;
	}

	public void setOwnerAgreementRecorded(boolean ownerAgreementRecorded) {
		this.ownerAgreementRecorded = ownerAgreementRecorded;
	}

	public String getAgreementMedium() {
		return this.agreementMedium;
	}

	public void setAgreementMedium(String agreementMedium) {
		this.agreementMedium = agreementMedium;
	}

	public Long getProtectedReasonPayloadId() {
		return this.protectedReasonPayloadId;
	}

	public void setProtectedReasonPayloadId(Long protectedReasonPayloadId) {
		this.protectedReasonPayloadId = protectedReasonPayloadId;
	}

	public Long getProtectedSnapshotPayloadId() {
		return this.protectedSnapshotPayloadId;
	}

	public void setProtectedSnapshotPayloadId(Long protectedSnapshotPayloadId) {
		this.protectedSnapshotPayloadId = protectedSnapshotPayloadId;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

}
