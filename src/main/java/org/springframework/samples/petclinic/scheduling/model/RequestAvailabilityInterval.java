package org.springframework.samples.petclinic.scheduling.model;

import java.time.Instant;

import org.springframework.samples.petclinic.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "request_availability_intervals")
public class RequestAvailabilityInterval extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id", nullable = false)
	private SchedulingRequest request;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "source_window_id")
	private RequestAvailabilityWindow sourceWindow;

	@Enumerated(EnumType.STRING)
	@Column(name = "window_kind", nullable = false, length = 20)
	private AvailabilityWindowKind kind;

	@Column(name = "start_instant", nullable = false)
	private Instant startInstant;

	@Column(name = "end_instant", nullable = false)
	private Instant endInstant;

	public SchedulingRequest getRequest() {
		return request;
	}

	public void setRequest(SchedulingRequest request) {
		this.request = request;
	}

	public RequestAvailabilityWindow getSourceWindow() {
		return sourceWindow;
	}

	public void setSourceWindow(RequestAvailabilityWindow sourceWindow) {
		this.sourceWindow = sourceWindow;
	}

	public AvailabilityWindowKind getKind() {
		return kind;
	}

	public void setKind(AvailabilityWindowKind kind) {
		this.kind = kind;
	}

	public Instant getStartInstant() {
		return startInstant;
	}

	public void setStartInstant(Instant startInstant) {
		this.startInstant = startInstant;
	}

	public Instant getEndInstant() {
		return endInstant;
	}

	public void setEndInstant(Instant endInstant) {
		this.endInstant = endInstant;
	}

}
