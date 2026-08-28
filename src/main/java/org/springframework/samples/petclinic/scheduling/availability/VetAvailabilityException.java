package org.springframework.samples.petclinic.scheduling.availability;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.samples.petclinic.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "vet_availability_exceptions")
public class VetAvailabilityException extends BaseEntity {

	@Column(name = "vet_id", nullable = false)
	private Integer vetId;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Column(nullable = false)
	private boolean available;

	@Column(name = "start_time")
	private LocalTime startTime;

	@Column(name = "end_time")
	private LocalTime endTime;

	@Column
	private String reason;

	@Version
	private long version;

	protected VetAvailabilityException() {
	}

	public VetAvailabilityException(Integer vetId, LocalDate startDate, LocalDate endDate, boolean available,
			LocalTime startTime, LocalTime endTime, String reason) {
		this.vetId = vetId;
		this.startDate = startDate;
		this.endDate = endDate;
		this.available = available;
		this.startTime = startTime;
		this.endTime = endTime;
		this.reason = reason;
	}

	public boolean applies(LocalDate date) {
		return !date.isBefore(this.startDate) && !date.isAfter(this.endDate);
	}

	public Integer getVetId() {
		return this.vetId;
	}

	public LocalDate getStartDate() {
		return this.startDate;
	}

	public LocalDate getEndDate() {
		return this.endDate;
	}

	public boolean isAvailable() {
		return this.available;
	}

	public LocalTime getStartTime() {
		return this.startTime;
	}

	public LocalTime getEndTime() {
		return this.endTime;
	}

	public String getReason() {
		return this.reason;
	}

}
