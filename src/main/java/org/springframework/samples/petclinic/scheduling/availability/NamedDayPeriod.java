package org.springframework.samples.petclinic.scheduling.availability;

import java.time.LocalTime;

import org.springframework.samples.petclinic.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "named_day_periods")
public class NamedDayPeriod extends BaseEntity {

	@Column(nullable = false, unique = true)
	private String name;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;

	@Version
	private long version;

	protected NamedDayPeriod() {
	}

	public NamedDayPeriod(String name, LocalTime startTime, LocalTime endTime) {
		if (!endTime.isAfter(startTime)) {
			throw new IllegalArgumentException("A named period must end after it starts");
		}
		this.name = name;
		this.startTime = startTime;
		this.endTime = endTime;
	}

	public String getName() {
		return this.name;
	}

	public LocalTime getStartTime() {
		return this.startTime;
	}

	public LocalTime getEndTime() {
		return this.endTime;
	}

}
