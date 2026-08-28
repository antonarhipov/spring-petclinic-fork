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
package org.springframework.samples.petclinic.owner;

import java.time.LocalDate;
import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.samples.petclinic.model.BaseEntity;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.vet.Vet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

/**
 * Simple JavaBean domain object representing a visit.
 *
 * @author Ken Krebs
 * @author Dave Syer
 */
@Entity
@Table(name = "visits")
public class Visit extends BaseEntity {

	@Column(name = "visit_date")
	@DateTimeFormat(pattern = "yyyy-MM-dd")
	private LocalDate date;

	@NotBlank
	private String description;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "appointment_id")
	private Appointment appointment;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "vet_id")
	private Vet veterinarian;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "clinical_notes")
	private String clinicalNotes;

	/**
	 * Creates a new instance of Visit for tomorrow
	 */
	public Visit() {
		this.date = LocalDate.now().plusDays(1);
	}

	public LocalDate getDate() {
		return this.date;
	}

	public void setDate(LocalDate date) {
		this.date = date;
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Integer getAppointmentId() {
		return this.appointment == null ? null : this.appointment.getId();
	}

	public void completeFor(Appointment appointment, Vet veterinarian, Instant completedAt, String clinicalNotes) {
		this.appointment = appointment;
		this.veterinarian = veterinarian;
		this.completedAt = completedAt;
		this.clinicalNotes = clinicalNotes;
		this.date = completedAt.atZone(java.time.ZoneOffset.UTC).toLocalDate();
		this.description = "Completed appointment";
	}

}
