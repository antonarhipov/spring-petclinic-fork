/* Copyright 2012-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package org.springframework.samples.petclinic.scheduling.clinic;

import java.time.DayOfWeek;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.springframework.samples.petclinic.model.BaseEntity;

@Entity
@Table(name = "clinic_opening_hour")
public class ClinicOpeningHour extends BaseEntity {

	@Column(name = "clinic_name", nullable = false)
	private String clinicName;

	@Enumerated(EnumType.STRING)
	@Column(name = "day_of_week", nullable = false)
	private DayOfWeek dayOfWeek;

	@Column(name = "open_time")
	private LocalTime openTime;

	@Column(name = "close_time")
	private LocalTime closeTime;

	@Column(name = "closed", nullable = false)
	private boolean closed;

	public DayOfWeek getDayOfWeek() {
		return this.dayOfWeek;
	}

	public LocalTime getOpenTime() {
		return this.openTime;
	}

	public LocalTime getCloseTime() {
		return this.closeTime;
	}

	public boolean isClosed() {
		return this.closed;
	}

}
