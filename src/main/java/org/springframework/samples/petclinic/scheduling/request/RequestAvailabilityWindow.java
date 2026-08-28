package org.springframework.samples.petclinic.scheduling.request;

import java.time.LocalDate;
import java.time.LocalTime;

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
@Table(name = "request_availability_windows")
public class RequestAvailabilityWindow extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "revision_id", nullable = false)
	private RequestRevision revision;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private WindowKind kind;

	@Column(name = "applicable_date")
	private LocalDate applicableDate;

	@Column(name = "day_of_week")
	private Integer dayOfWeek;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;

	@Column(nullable = false)
	private String source;

	protected RequestAvailabilityWindow() {
	}

	public RequestAvailabilityWindow(WindowKind kind, LocalDate applicableDate, Integer dayOfWeek, LocalTime startTime,
			LocalTime endTime, String source) {
		if (!endTime.isAfter(startTime)) {
			throw new IllegalArgumentException("Availability window must end after it starts");
		}
		this.kind = kind;
		this.applicableDate = applicableDate;
		this.dayOfWeek = dayOfWeek;
		this.startTime = startTime;
		this.endTime = endTime;
		this.source = source;
	}

	void setRevision(RequestRevision revision) {
		this.revision = revision;
	}

	public WindowKind getKind() {
		return this.kind;
	}

	public LocalDate getApplicableDate() {
		return this.applicableDate;
	}

	public LocalTime getStartTime() {
		return this.startTime;
	}

	public LocalTime getEndTime() {
		return this.endTime;
	}

}
