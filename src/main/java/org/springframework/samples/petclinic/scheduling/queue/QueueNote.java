package org.springframework.samples.petclinic.scheduling.queue;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "queue_notes")
public class QueueNote {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "queue_item_id", nullable = false)
	private Long queueItemId;

	@Column(name = "author_account_id", nullable = false)
	private Long authorAccountId;

	@Column(nullable = false)
	private String body;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public Long getId() {
		return this.id;
	}

	public void setQueueItemId(Long queueItemId) {
		this.queueItemId = queueItemId;
	}

	public void setAuthorAccountId(Long authorAccountId) {
		this.authorAccountId = authorAccountId;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Long getQueueItemId() {
		return this.queueItemId;
	}

	public Long getAuthorAccountId() {
		return this.authorAccountId;
	}

	public String getBody() {
		return this.body;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
