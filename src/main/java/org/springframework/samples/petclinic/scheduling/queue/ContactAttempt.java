package org.springframework.samples.petclinic.scheduling.queue;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.samples.petclinic.audit.ProtectedPayload;

@Entity
@Table(name = "contact_attempts")
public class ContactAttempt {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "queue_item_id", nullable = false)
	private QueueItem queueItem;

	@Column(name = "actor_account_id", nullable = false)
	private Long actorAccountId;

	@Column(name = "attempted_at", nullable = false)
	private Instant attemptedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "outcome", nullable = false)
	private ContactOutcome outcome;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "note_payload_id")
	private ProtectedPayload notePayload;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected ContactAttempt() {
	}

	public ContactAttempt(QueueItem queueItem, Long actorAccountId, Instant attemptedAt, ContactOutcome outcome,
			ProtectedPayload notePayload, Instant createdAt) {
		this.queueItem = queueItem;
		this.actorAccountId = actorAccountId;
		this.attemptedAt = attemptedAt;
		this.outcome = outcome;
		this.notePayload = notePayload;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return this.id;
	}

	public QueueItem getQueueItem() {
		return this.queueItem;
	}

	public void setQueueItem(QueueItem queueItem) {
		this.queueItem = queueItem;
	}

	public Long getActorAccountId() {
		return this.actorAccountId;
	}

	public void setActorAccountId(Long actorAccountId) {
		this.actorAccountId = actorAccountId;
	}

	public Instant getAttemptedAt() {
		return this.attemptedAt;
	}

	public void setAttemptedAt(Instant attemptedAt) {
		this.attemptedAt = attemptedAt;
	}

	public ContactOutcome getOutcome() {
		return this.outcome;
	}

	public void setOutcome(ContactOutcome outcome) {
		this.outcome = outcome;
	}

	public ProtectedPayload getNotePayload() {
		return this.notePayload;
	}

	public void setNotePayload(ProtectedPayload notePayload) {
		this.notePayload = notePayload;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

}
