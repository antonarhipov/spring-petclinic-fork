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
package org.springframework.samples.petclinic.appointment;

import java.time.Instant;

import org.springframework.samples.petclinic.model.BaseEntity;
import org.springframework.samples.petclinic.vet.Vet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Short-lived hold on a (vet, start_instant) slot guarded by a database unique
 * constraint.
 */
@Entity
@Table(name = "slot_hold", uniqueConstraints = {
		@UniqueConstraint(name = "uk_slot_hold_vet_start", columnNames = { "vet_id", "start_instant" }) })
public class SlotHold extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "vet_id", nullable = false)
	private Vet vet;

	@Column(name = "start_instant", nullable = false)
	private Instant startInstant;

	@Column(name = "duration_min", nullable = false)
	private Integer durationMin;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "request_id", nullable = false)
	private AppointmentRequest request;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	public Vet getVet() {
		return this.vet;
	}

	public void setVet(Vet vet) {
		this.vet = vet;
	}

	public Instant getStartInstant() {
		return this.startInstant;
	}

	public void setStartInstant(Instant startInstant) {
		this.startInstant = startInstant;
	}

	public Integer getDurationMin() {
		return this.durationMin;
	}

	public void setDurationMin(Integer durationMin) {
		this.durationMin = durationMin;
	}

	public AppointmentRequest getRequest() {
		return this.request;
	}

	public void setRequest(AppointmentRequest request) {
		this.request = request;
	}

	public Instant getExpiresAt() {
		return this.expiresAt;
	}

	public void setExpiresAt(Instant expiresAt) {
		this.expiresAt = expiresAt;
	}

}
