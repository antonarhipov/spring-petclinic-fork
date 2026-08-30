package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "reservation_blocks")
@IdClass(ReservationBlockId.class)
public class ReservationBlock {

	@Id
	@Enumerated(EnumType.STRING)
	@Column(name = "resource_type", nullable = false)
	private ReservationResourceType resourceType;

	@Id
	@Column(name = "resource_id", nullable = false)
	private Integer resourceId;

	@Id
	@Column(name = "block_start", nullable = false)
	private Instant blockStart;

	@Column(name = "hold_id")
	private Long holdId;

	@Column(name = "appointment_id")
	private Long appointmentId;

	public ReservationResourceType getResourceType() {
		return this.resourceType;
	}

	public void setResourceType(ReservationResourceType resourceType) {
		this.resourceType = resourceType;
	}

	public Integer getResourceId() {
		return this.resourceId;
	}

	public void setResourceId(Integer resourceId) {
		this.resourceId = resourceId;
	}

	public Instant getBlockStart() {
		return this.blockStart;
	}

	public void setBlockStart(Instant blockStart) {
		this.blockStart = blockStart;
	}

	public Long getHoldId() {
		return this.holdId;
	}

	public void setHoldId(Long holdId) {
		this.holdId = holdId;
	}

	public Long getAppointmentId() {
		return this.appointmentId;
	}

	public void setAppointmentId(Long appointmentId) {
		this.appointmentId = appointmentId;
	}

}
