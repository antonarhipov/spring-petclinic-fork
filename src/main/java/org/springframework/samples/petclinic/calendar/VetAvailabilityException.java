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

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.samples.petclinic.model.BaseEntity;
import org.springframework.samples.petclinic.vet.Vet;

@Entity
@Table(name = "vet_availability_exception")
public class VetAvailabilityException extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "vet_id", nullable = false)
	private Vet vet;

	@Column(name = "date", nullable = false)
	private LocalDate date;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false)
	private ExceptionType type;

	@Column(name = "start_local")
	private LocalTime startLocal;

	@Column(name = "end_local")
	private LocalTime endLocal;

	public VetAvailabilityException() {
	}

	public VetAvailabilityException(Vet vet, LocalDate date, ExceptionType type, LocalTime startLocal,
			LocalTime endLocal) {
		this.vet = vet;
		this.date = date;
		this.type = type;
		this.startLocal = startLocal;
		this.endLocal = endLocal;
	}

	public Vet getVet() {
		return this.vet;
	}

	public void setVet(Vet vet) {
		this.vet = vet;
	}

	public Integer getVetId() {
		return this.vet != null ? this.vet.getId() : null;
	}

	public LocalDate getDate() {
		return this.date;
	}

	public void setDate(LocalDate date) {
		this.date = date;
	}

	public ExceptionType getType() {
		return this.type;
	}

	public void setType(ExceptionType type) {
		this.type = type;
	}

	public LocalTime getStartLocal() {
		return this.startLocal;
	}

	public void setStartLocal(LocalTime startLocal) {
		this.startLocal = startLocal;
	}

	public LocalTime getEndLocal() {
		return this.endLocal;
	}

	public void setEndLocal(LocalTime endLocal) {
		this.endLocal = endLocal;
	}

}
