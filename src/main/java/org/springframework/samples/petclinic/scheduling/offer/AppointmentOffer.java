package org.springframework.samples.petclinic.scheduling.offer;

import java.time.Instant;

import org.springframework.samples.petclinic.model.BaseEntity;
import org.springframework.samples.petclinic.scheduling.request.RequestRevision;
import org.springframework.samples.petclinic.vet.Vet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "appointment_offers")
public class AppointmentOffer extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "revision_id", nullable = false)
	private RequestRevision revision;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "vet_id", nullable = false)
	private Vet vet;

	@Column(name = "start_at", nullable = false)
	private Instant startAt;

	@Column(name = "duration_minutes", nullable = false)
	private int durationMinutes;

	@Column(name = "offered_at", nullable = false)
	private Instant offeredAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OfferState state = OfferState.HELD;

	@Column(nullable = false)
	private String rationale;

	@Column(name = "rejection_reason")
	private String rejectionReason;

	@Version
	private long version;

	protected AppointmentOffer() {
	}

	public AppointmentOffer(RequestRevision revision, Vet vet, Instant startAt, int durationMinutes, Instant offeredAt,
			Instant expiresAt, String rationale) {
		this.revision = revision;
		this.vet = vet;
		this.startAt = startAt;
		this.durationMinutes = durationMinutes;
		this.offeredAt = offeredAt;
		this.expiresAt = expiresAt;
		this.rationale = rationale;
	}

	public RequestRevision getRevision() {
		return this.revision;
	}

	public Vet getVet() {
		return this.vet;
	}

	public Instant getStartAt() {
		return this.startAt;
	}

	public int getDurationMinutes() {
		return this.durationMinutes;
	}

	public Instant getExpiresAt() {
		return this.expiresAt;
	}

	public OfferState getState() {
		return this.state;
	}

	public boolean isActive(Instant now) {
		return this.state == OfferState.HELD && this.expiresAt.isAfter(now);
	}

	public void accept() {
		this.state = OfferState.ACCEPTED;
	}

	public void reject(String reason) {
		this.state = OfferState.REJECTED;
		this.rejectionReason = reason;
	}

	public void expire() {
		this.state = OfferState.EXPIRED;
	}

	public void release() {
		this.state = OfferState.RELEASED;
	}

	public void invalidate() {
		this.state = OfferState.INVALIDATED;
	}

}
