package org.springframework.samples.petclinic.scheduling.model;

import java.time.Instant;

import org.springframework.samples.petclinic.model.BaseEntity;
import org.springframework.samples.petclinic.vet.Vet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "calendar_conflicts")
public class CalendarConflict extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "appointment_id", nullable = false)
	private Appointment appointment;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "vet_id", nullable = false)
	private Vet vet;

	@Column(name = "start_time", nullable = false)
	private Instant startTime;

	@Column(name = "end_time", nullable = false)
	private Instant endTime;

	@Enumerated(EnumType.STRING)
	@Column(name = "conflict_type", nullable = false, length = 30)
	private ConflictType conflictType;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 30)
	private ConflictStatus status = ConflictStatus.UNRESOLVED;

	@Column(name = "detected_at", nullable = false)
	private Instant detectedAt;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	@Version
	@Column(name = "version", nullable = false)
	private Long version = 0L;

	public CalendarConflict() {
	}

	@PrePersist
	public void onPrePersist() {
		if (this.detectedAt == null) {
			this.detectedAt = Instant.now();
		}
	}

	public Appointment getAppointment() {
		return appointment;
	}

	public void setAppointment(Appointment appointment) {
		this.appointment = appointment;
	}

	public Vet getVet() {
		return vet;
	}

	public void setVet(Vet vet) {
		this.vet = vet;
	}

	public Instant getStartTime() {
		return startTime;
	}

	public void setStartTime(Instant startTime) {
		this.startTime = startTime;
	}

	public Instant getEndTime() {
		return endTime;
	}

	public void setEndTime(Instant endTime) {
		this.endTime = endTime;
	}

	public ConflictType getConflictType() {
		return conflictType;
	}

	public void setConflictType(ConflictType conflictType) {
		this.conflictType = conflictType;
	}

	public ConflictStatus getStatus() {
		return status;
	}

	public void setStatus(ConflictStatus status) {
		this.status = status;
	}

	public Instant getDetectedAt() {
		return detectedAt;
	}

	public void setDetectedAt(Instant detectedAt) {
		this.detectedAt = detectedAt;
	}

	public Instant getResolvedAt() {
		return resolvedAt;
	}

	public void setResolvedAt(Instant resolvedAt) {
		this.resolvedAt = resolvedAt;
	}

	public Long getVersion() {
		return version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

}
