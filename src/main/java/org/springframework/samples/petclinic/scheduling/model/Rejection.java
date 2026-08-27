package org.springframework.samples.petclinic.scheduling.model;

import java.time.Instant;

import org.springframework.samples.petclinic.model.BaseEntity;
import org.springframework.samples.petclinic.vet.Vet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "rejections")
public class Rejection extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "request_id", nullable = false)
	private SchedulingRequest request;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "vet_id", nullable = false)
	private Vet vet;

	@Column(name = "start_time", nullable = false)
	private Instant startTime;

	@Column(name = "end_time", nullable = false)
	private Instant endTime;

	@Column(name = "reason")
	private String reason;

	@Column(name = "rejected_at", nullable = false)
	private Instant rejectedAt;

	public Rejection() {
	}

	@PrePersist
	public void onPrePersist() {
		if (this.rejectedAt == null) {
			this.rejectedAt = Instant.now();
		}
	}

	public SchedulingRequest getRequest() {
		return request;
	}

	public void setRequest(SchedulingRequest request) {
		this.request = request;
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

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public Instant getRejectedAt() {
		return rejectedAt;
	}

	public void setRejectedAt(Instant rejectedAt) {
		this.rejectedAt = rejectedAt;
	}

}
