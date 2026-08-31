package org.springframework.samples.petclinic.appointment;

import java.time.Instant;

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
@Table(name = "appointment_outcome_events")
public class AppointmentOutcomeEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "appointment_id", nullable = false)
	private Long appointmentId;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 32)
	private AppointmentOutcomeEventType eventType;

	@Column(name = "actor_account_id")
	private Long actorAccountId;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "previous_outcome", length = 32)
	private OutcomeState previousOutcome;

	@Enumerated(EnumType.STRING)
	@Column(name = "new_outcome", nullable = false, length = 32)
	private OutcomeState newOutcome;

	@Column(name = "resulting_visit_id")
	private Integer resultingVisitId;

	@Column(name = "reason_payload_id")
	private Long reasonPayloadId;

	@Column(name = "clinical_payload_id")
	private Long clinicalPayloadId;

	@Version
	@Column(name = "version", nullable = false)
	private Long version;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected AppointmentOutcomeEvent() {
	}

	public AppointmentOutcomeEvent(Long appointmentId, AppointmentOutcomeEventType eventType, Long actorAccountId,
			Instant occurredAt, OutcomeState previousOutcome, OutcomeState newOutcome, Integer resultingVisitId,
			Long reasonPayloadId, Long clinicalPayloadId) {
		this.appointmentId = appointmentId;
		this.eventType = eventType;
		this.actorAccountId = actorAccountId;
		this.occurredAt = occurredAt;
		this.previousOutcome = previousOutcome;
		this.newOutcome = newOutcome;
		this.resultingVisitId = resultingVisitId;
		this.reasonPayloadId = reasonPayloadId;
		this.clinicalPayloadId = clinicalPayloadId;
		this.createdAt = occurredAt;
	}

	public Long getId() {
		return this.id;
	}

	public Long getAppointmentId() {
		return this.appointmentId;
	}

	public AppointmentOutcomeEventType getEventType() {
		return this.eventType;
	}

	public Long getActorAccountId() {
		return this.actorAccountId;
	}

	public Instant getOccurredAt() {
		return this.occurredAt;
	}

	public OutcomeState getPreviousOutcome() {
		return this.previousOutcome;
	}

	public OutcomeState getNewOutcome() {
		return this.newOutcome;
	}

	public Integer getResultingVisitId() {
		return this.resultingVisitId;
	}

	public Long getReasonPayloadId() {
		return this.reasonPayloadId;
	}

	public Long getClinicalPayloadId() {
		return this.clinicalPayloadId;
	}

	public Long getVersion() {
		return this.version;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
