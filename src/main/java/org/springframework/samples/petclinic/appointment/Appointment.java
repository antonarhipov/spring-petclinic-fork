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

import java.time.Duration;
import java.time.Instant;

import org.springframework.samples.petclinic.model.BaseEntity;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.vet.Vet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * An appointment represents a scheduled time slot with a veterinarian for a pet.
 */
@Entity
@Table(name = "appointment")
public class Appointment extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "request_id")
	private AppointmentRequest request;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "pet_id", nullable = false)
	private Pet pet;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "vet_id", nullable = false)
	private Vet vet;

	@Column(name = "start_instant", nullable = false)
	private Instant startInstant;

	@Column(name = "duration_min", nullable = false)
	private Integer durationMin;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 30)
	private AppointmentStatus status = AppointmentStatus.SCHEDULED;

	@Column(name = "reason", length = 500)
	private String reason;

	public AppointmentRequest getRequest() {
		return this.request;
	}

	public void setRequest(AppointmentRequest request) {
		this.request = request;
	}

	public Pet getPet() {
		return this.pet;
	}

	public void setPet(Pet pet) {
		this.pet = pet;
	}

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

	public AppointmentStatus getStatus() {
		return this.status;
	}

	public void setStatus(AppointmentStatus status) {
		this.status = status;
	}

	public String getReason() {
		return this.reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public Instant getEndInstant() {
		if (this.startInstant == null || this.durationMin == null) {
			return null;
		}
		return this.startInstant.plus(Duration.ofMinutes(this.durationMin));
	}

	public boolean overlaps(Instant otherStart, int otherDurationMin) {
		if (this.startInstant == null || this.durationMin == null || otherStart == null || otherDurationMin <= 0) {
			return false;
		}
		Instant end = getEndInstant();
		Instant otherEnd = otherStart.plus(Duration.ofMinutes(otherDurationMin));
		return this.startInstant.isBefore(otherEnd) && end.isAfter(otherStart);
	}

}
