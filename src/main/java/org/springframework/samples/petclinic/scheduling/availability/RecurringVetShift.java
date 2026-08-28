package org.springframework.samples.petclinic.scheduling.availability;

import java.time.LocalTime;

import org.springframework.samples.petclinic.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "recurring_vet_shifts")
public class RecurringVetShift extends BaseEntity {

	@Column(name = "vet_id", nullable = false)
	private Integer vetId;

	@Column(name = "day_of_week", nullable = false)
	private int dayOfWeek;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;

	@Version
	private long version;

	protected RecurringVetShift() {
	}

	public RecurringVetShift(Integer vetId, int dayOfWeek, LocalTime startTime, LocalTime endTime) {
		this.vetId = vetId;
		this.dayOfWeek = dayOfWeek;
		this.startTime = startTime;
		this.endTime = endTime;
	}

	public Integer getVetId() {
		return this.vetId;
	}

	public int getDayOfWeek() {
		return this.dayOfWeek;
	}

	public LocalTime getStartTime() {
		return this.startTime;
	}

	public LocalTime getEndTime() {
		return this.endTime;
	}

}
