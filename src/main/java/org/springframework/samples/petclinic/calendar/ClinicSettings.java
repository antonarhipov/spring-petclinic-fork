/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.calendar;

import java.time.LocalTime;
import java.time.ZoneId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.samples.petclinic.model.BaseEntity;

@Entity
@Table(name = "clinic_settings")
public class ClinicSettings extends BaseEntity {

	@Column(name = "grid_granularity_min", nullable = false)
	private Integer gridGranularityMin = 15;

	@Column(name = "hold_duration_min", nullable = false)
	private Integer holdDurationMin = 10;

	@Column(name = "booking_horizon_days", nullable = false)
	private Integer bookingHorizonDays = 60;

	@Column(name = "min_visit_min", nullable = false)
	private Integer minVisitMin = 15;

	@Column(name = "max_visit_min", nullable = false)
	private Integer maxVisitMin = 120;

	@Column(name = "default_visit_min", nullable = false)
	private Integer defaultVisitMin = 30;

	@Column(name = "zone_id", nullable = false)
	private String zoneId = "Europe/Amsterdam";

	@Column(name = "morning_start", nullable = false)
	private LocalTime morningStart = LocalTime.of(8, 0);

	@Column(name = "morning_end", nullable = false)
	private LocalTime morningEnd = LocalTime.of(12, 0);

	@Column(name = "afternoon_start", nullable = false)
	private LocalTime afternoonStart = LocalTime.of(12, 0);

	@Column(name = "afternoon_end", nullable = false)
	private LocalTime afternoonEnd = LocalTime.of(17, 0);

	@Column(name = "evening_start", nullable = false)
	private LocalTime eveningStart = LocalTime.of(17, 0);

	@Column(name = "evening_end", nullable = false)
	private LocalTime eveningEnd = LocalTime.of(20, 0);

	public ClinicSettings() {
	}

	public Integer getGridGranularityMin() {
		return this.gridGranularityMin;
	}

	public void setGridGranularityMin(Integer gridGranularityMin) {
		this.gridGranularityMin = gridGranularityMin;
	}

	public Integer getHoldDurationMin() {
		return this.holdDurationMin;
	}

	public void setHoldDurationMin(Integer holdDurationMin) {
		this.holdDurationMin = holdDurationMin;
	}

	public Integer getBookingHorizonDays() {
		return this.bookingHorizonDays;
	}

	public void setBookingHorizonDays(Integer bookingHorizonDays) {
		this.bookingHorizonDays = bookingHorizonDays;
	}

	public Integer getMinVisitMin() {
		return this.minVisitMin;
	}

	public void setMinVisitMin(Integer minVisitMin) {
		this.minVisitMin = minVisitMin;
	}

	public Integer getMaxVisitMin() {
		return this.maxVisitMin;
	}

	public void setMaxVisitMin(Integer maxVisitMin) {
		this.maxVisitMin = maxVisitMin;
	}

	public Integer getDefaultVisitMin() {
		return this.defaultVisitMin;
	}

	public void setDefaultVisitMin(Integer defaultVisitMin) {
		this.defaultVisitMin = defaultVisitMin;
	}

	public String getZoneId() {
		return this.zoneId;
	}

	public void setZoneId(String zoneId) {
		this.zoneId = zoneId;
	}

	public ZoneId getZone() {
		return ZoneId.of(this.zoneId != null ? this.zoneId : "Europe/Amsterdam");
	}

	public LocalTime getMorningStart() {
		return this.morningStart;
	}

	public void setMorningStart(LocalTime morningStart) {
		this.morningStart = morningStart;
	}

	public LocalTime getMorningEnd() {
		return this.morningEnd;
	}

	public void setMorningEnd(LocalTime morningEnd) {
		this.morningEnd = morningEnd;
	}

	public LocalTime getAfternoonStart() {
		return this.afternoonStart;
	}

	public void setAfternoonStart(LocalTime afternoonStart) {
		this.afternoonStart = afternoonStart;
	}

	public LocalTime getAfternoonEnd() {
		return this.afternoonEnd;
	}

	public void setAfternoonEnd(LocalTime afternoonEnd) {
		this.afternoonEnd = afternoonEnd;
	}

	public LocalTime getEveningStart() {
		return this.eveningStart;
	}

	public void setEveningStart(LocalTime eveningStart) {
		this.eveningStart = eveningStart;
	}

	public LocalTime getEveningEnd() {
		return this.eveningEnd;
	}

	public void setEveningEnd(LocalTime eveningEnd) {
		this.eveningEnd = eveningEnd;
	}

	public int clampDuration(Integer requestedDurationMin) {
		if (requestedDurationMin == null || requestedDurationMin <= 0) {
			return this.defaultVisitMin != null ? this.defaultVisitMin : 30;
		}
		int min = this.minVisitMin != null ? this.minVisitMin : 15;
		int max = this.maxVisitMin != null ? this.maxVisitMin : 120;
		return Math.max(min, Math.min(max, requestedDurationMin));
	}

}
