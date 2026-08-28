package org.springframework.samples.petclinic.scheduling.offer;

import java.time.Instant;

import org.springframework.samples.petclinic.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "reservation_blocks")
public class ReservationBlock extends BaseEntity {

	@Column(name = "resource_type", nullable = false)
	private String resourceType;

	@Column(name = "resource_id", nullable = false)
	private Integer resourceId;

	@Column(name = "slot_start", nullable = false)
	private Instant slotStart;

	@Column(name = "owner_type", nullable = false)
	private String ownerType;

	@Column(name = "owner_id", nullable = false)
	private Integer ownerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ReservationState state;

	@Column(name = "expires_at")
	private Instant expiresAt;

	protected ReservationBlock() {
	}

	public ReservationBlock(String resourceType, Integer resourceId, Instant slotStart, String ownerType,
			Integer ownerId, ReservationState state, Instant expiresAt) {
		this.resourceType = resourceType;
		this.resourceId = resourceId;
		this.slotStart = slotStart;
		this.ownerType = ownerType;
		this.ownerId = ownerId;
		this.state = state;
		this.expiresAt = expiresAt;
	}

	public void confirm() {
		this.state = ReservationState.CONFIRMED;
		this.expiresAt = null;
	}

	public void confirmForAppointment(Integer appointmentId) {
		this.ownerType = "APPOINTMENT";
		this.ownerId = appointmentId;
		confirm();
	}

}
