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

package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.samples.petclinic.model.BaseEntity;

/**
 * Audit log for appointment changes and cancellations (RULE-16, RULE-43, AC-122).
 */
@Entity
@Table(name = "appointment_change")
public class AppointmentChange extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "appointment_id", nullable = false)
	private Appointment appointment;

	@Column(name = "actor", nullable = false, length = 100)
	private String actor;

	@Column(name = "action", nullable = false, length = 100)
	private String action;

	@Column(name = "reason", length = 255)
	private String reason;

	@Column(name = "timestamp", nullable = false)
	private ZonedDateTime timestamp;

	@Column(name = "original_start_time")
	private ZonedDateTime originalStartTime;

	public Appointment getAppointment() {
		return this.appointment;
	}

	public void setAppointment(Appointment appointment) {
		this.appointment = appointment;
	}

	public String getActor() {
		return this.actor;
	}

	public void setActor(String actor) {
		this.actor = actor;
	}

	public String getAction() {
		return this.action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public String getReason() {
		return this.reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public ZonedDateTime getTimestamp() {
		return this.timestamp;
	}

	public void setTimestamp(ZonedDateTime timestamp) {
		this.timestamp = timestamp;
	}

	public ZonedDateTime getOriginalStartTime() {
		return this.originalStartTime;
	}

	public void setOriginalStartTime(ZonedDateTime originalStartTime) {
		this.originalStartTime = originalStartTime;
	}

}
