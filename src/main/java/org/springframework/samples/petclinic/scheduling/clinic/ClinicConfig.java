/*
 * Copyright 2012-2026 the original author or authors.
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

package org.springframework.samples.petclinic.scheduling.clinic;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.samples.petclinic.model.BaseEntity;

@Entity
@Table(name = "clinic_config")
public class ClinicConfig extends BaseEntity {

	@Column(name = "booking_horizon_days", nullable = false)
	private int bookingHorizonDays;

	@Column(name = "min_duration_minutes", nullable = false)
	private int minDurationMinutes;

	@Column(name = "max_duration_minutes", nullable = false)
	private int maxDurationMinutes;

	@Column(name = "default_duration_minutes", nullable = false)
	private int defaultDurationMinutes;

	@Column(name = "grid_interval_minutes", nullable = false)
	private int gridIntervalMinutes;

	@Column(name = "time_zone", nullable = false)
	private String timeZone;

	@Column(name = "emergency_phone", nullable = false)
	private String emergencyPhone;

	public int getBookingHorizonDays() {
		return this.bookingHorizonDays;
	}

	public void setBookingHorizonDays(int bookingHorizonDays) {
		this.bookingHorizonDays = bookingHorizonDays;
	}

	public int getMinDurationMinutes() {
		return this.minDurationMinutes;
	}

	public void setMinDurationMinutes(int minDurationMinutes) {
		this.minDurationMinutes = minDurationMinutes;
	}

	public int getMaxDurationMinutes() {
		return this.maxDurationMinutes;
	}

	public void setMaxDurationMinutes(int maxDurationMinutes) {
		this.maxDurationMinutes = maxDurationMinutes;
	}

	public int getDefaultDurationMinutes() {
		return this.defaultDurationMinutes;
	}

	public void setDefaultDurationMinutes(int defaultDurationMinutes) {
		this.defaultDurationMinutes = defaultDurationMinutes;
	}

	public int getGridIntervalMinutes() {
		return this.gridIntervalMinutes;
	}

	public void setGridIntervalMinutes(int gridIntervalMinutes) {
		this.gridIntervalMinutes = gridIntervalMinutes;
	}

	public String getTimeZone() {
		return this.timeZone;
	}

	public void setTimeZone(String timeZone) {
		this.timeZone = timeZone;
	}

	public String getEmergencyPhone() {
		return this.emergencyPhone;
	}

	public void setEmergencyPhone(String emergencyPhone) {
		this.emergencyPhone = emergencyPhone;
	}

}
