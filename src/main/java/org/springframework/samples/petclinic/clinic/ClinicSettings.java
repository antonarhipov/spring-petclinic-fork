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

package org.springframework.samples.petclinic.clinic;

import java.time.ZoneId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.samples.petclinic.model.BaseEntity;

@Entity
@Table(name = "clinic_settings")
public class ClinicSettings extends BaseEntity {

	@Column(name = "time_zone", nullable = false, length = 50)
	private String timeZone = "America/New_York";

	@Column(name = "min_visit_minutes", nullable = false)
	private int minVisitMinutes = 15;

	@Column(name = "max_visit_minutes", nullable = false)
	private int maxVisitMinutes = 120;

	@Column(name = "default_visit_minutes", nullable = false)
	private int defaultVisitMinutes = 30;

	@Column(name = "booking_horizon_days", nullable = false)
	private int bookingHorizonDays = 14;

	@Column(name = "hold_duration_minutes", nullable = false)
	private int holdDurationMinutes = 5;

	@Column(name = "grid_minutes", nullable = false)
	private int gridMinutes = 15;

	public ClinicSettings() {
	}

	public ClinicSettings(String timeZone, int minVisitMinutes, int maxVisitMinutes, int defaultVisitMinutes,
			int bookingHorizonDays, int holdDurationMinutes, int gridMinutes) {
		this.timeZone = timeZone;
		this.minVisitMinutes = minVisitMinutes;
		this.maxVisitMinutes = maxVisitMinutes;
		this.defaultVisitMinutes = defaultVisitMinutes;
		this.bookingHorizonDays = bookingHorizonDays;
		this.holdDurationMinutes = holdDurationMinutes;
		this.gridMinutes = gridMinutes;
	}

	public String getTimeZone() {
		return this.timeZone;
	}

	public void setTimeZone(String timeZone) {
		this.timeZone = timeZone;
	}

	public ZoneId getZoneId() {
		return ZoneId.of(this.timeZone);
	}

	public int getMinVisitMinutes() {
		return this.minVisitMinutes;
	}

	public void setMinVisitMinutes(int minVisitMinutes) {
		this.minVisitMinutes = minVisitMinutes;
	}

	public int getMaxVisitMinutes() {
		return this.maxVisitMinutes;
	}

	public void setMaxVisitMinutes(int maxVisitMinutes) {
		this.maxVisitMinutes = maxVisitMinutes;
	}

	public int getDefaultVisitMinutes() {
		return this.defaultVisitMinutes;
	}

	public void setDefaultVisitMinutes(int defaultVisitMinutes) {
		this.defaultVisitMinutes = defaultVisitMinutes;
	}

	public int getBookingHorizonDays() {
		return this.bookingHorizonDays;
	}

	public void setBookingHorizonDays(int bookingHorizonDays) {
		this.bookingHorizonDays = bookingHorizonDays;
	}

	public int getHoldDurationMinutes() {
		return this.holdDurationMinutes;
	}

	public void setHoldDurationMinutes(int holdDurationMinutes) {
		this.holdDurationMinutes = holdDurationMinutes;
	}

	public int getGridMinutes() {
		return this.gridMinutes;
	}

	public void setGridMinutes(int gridMinutes) {
		this.gridMinutes = gridMinutes;
	}

	public int clampDuration(Integer requestedDuration) {
		if (requestedDuration == null || requestedDuration <= 0) {
			return this.defaultVisitMinutes;
		}
		if (requestedDuration < this.minVisitMinutes) {
			return this.minVisitMinutes;
		}
		if (requestedDuration > this.maxVisitMinutes) {
			return this.maxVisitMinutes;
		}
		return requestedDuration;
	}

}
