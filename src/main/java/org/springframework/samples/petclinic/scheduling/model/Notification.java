package org.springframework.samples.petclinic.scheduling.model;

import java.time.Instant;

import org.springframework.samples.petclinic.model.BaseEntity;
import org.springframework.samples.petclinic.owner.Owner;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id", nullable = false)
	private Owner owner;

	@Column(name = "title_key", nullable = false, length = 100)
	private String titleKey;

	@Column(name = "message_key", nullable = false, length = 100)
	private String messageKey;

	@Column(name = "message_params", length = 500)
	private String messageParams;

	@Column(name = "target_url", length = 255)
	private String targetUrl;

	@Column(name = "is_read", nullable = false)
	private boolean read = false;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public Notification() {
	}

	@PrePersist
	public void onPrePersist() {
		if (this.createdAt == null) {
			this.createdAt = Instant.now();
		}
	}

	public Owner getOwner() {
		return owner;
	}

	public void setOwner(Owner owner) {
		this.owner = owner;
	}

	public String getTitleKey() {
		return titleKey;
	}

	public void setTitleKey(String titleKey) {
		this.titleKey = titleKey;
	}

	public String getMessageKey() {
		return messageKey;
	}

	public void setMessageKey(String messageKey) {
		this.messageKey = messageKey;
	}

	public String getMessageParams() {
		return messageParams;
	}

	public void setMessageParams(String messageParams) {
		this.messageParams = messageParams;
	}

	public String getTargetUrl() {
		return targetUrl;
	}

	public void setTargetUrl(String targetUrl) {
		this.targetUrl = targetUrl;
	}

	public boolean isRead() {
		return read;
	}

	public void setRead(boolean read) {
		this.read = read;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	@Override
	public String toString() {
		return "Notification{" + "id=" + getId() + ", ownerId=" + (owner != null ? owner.getId() : null)
				+ ", titleKey='" + titleKey + '\'' + ", isRead=" + read + ", createdAt=" + createdAt + '}';
	}

}
