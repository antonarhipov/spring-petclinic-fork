package org.springframework.samples.petclinic.scheduling.appointment;

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
@Table(name = "holds")
public class Hold {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "offer_id", nullable = false, unique = true)
	private Long offerId;

	@Column(name = "request_id", nullable = false)
	private Long requestId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private HoldStatus state;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "release_reason")
	private String releaseReason;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	@Version
	private Integer version;

	public Long getId() {
		return this.id;
	}

	public Long getOfferId() {
		return this.offerId;
	}

	public void setOfferId(Long offerId) {
		this.offerId = offerId;
	}

	public Long getRequestId() {
		return this.requestId;
	}

	public void setRequestId(Long requestId) {
		this.requestId = requestId;
	}

	public HoldStatus getState() {
		return this.state;
	}

	public void setState(HoldStatus state) {
		this.state = state;
	}

	public Instant getExpiresAt() {
		return this.expiresAt;
	}

	public void setExpiresAt(Instant expiresAt) {
		this.expiresAt = expiresAt;
	}

	public String getReleaseReason() {
		return this.releaseReason;
	}

	public void setReleaseReason(String releaseReason) {
		this.releaseReason = releaseReason;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getResolvedAt() {
		return this.resolvedAt;
	}

	public void setResolvedAt(Instant resolvedAt) {
		this.resolvedAt = resolvedAt;
	}

	public Integer getVersion() {
		return this.version;
	}

}
