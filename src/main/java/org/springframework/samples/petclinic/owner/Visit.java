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

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.samples.petclinic.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

	@Column(name = "pet_id")
	private Integer petId;

	@Column(name = "visit_date")
	@DateTimeFormat(pattern = "yyyy-MM-dd")
	private LocalDate date;

	@NotBlank
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(name = "provenance", nullable = false, length = 32)
	private VisitProvenance provenance = VisitProvenance.LEGACY;

	@Column(name = "appointment_id")
	private Long appointmentId;

	@Column(name = "vet_id")
	private Integer vetId;

	@Column(name = "protected_clinical_payload_id")
	private Long protectedClinicalPayloadId;

	@Column(name = "outcome_event_id")
	private Long outcomeEventId;

	/**
	 * Creates a new instance of Visit for tomorrow
	 */
	public Visit() {
		this.date = LocalDate.now().plusDays(1);
	}

	public Integer getPetId() {
		return this.petId;
	}

	public void setPetId(Integer petId) {
		this.petId = petId;
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

	public VisitProvenance getProvenance() {
		return this.provenance;
	}

	public void setProvenance(VisitProvenance provenance) {
		this.provenance = provenance;
	}

	public Long getAppointmentId() {
		return this.appointmentId;
	}

	public void setAppointmentId(Long appointmentId) {
		this.appointmentId = appointmentId;
	}

	public Integer getVetId() {
		return this.vetId;
	}

	public void setVetId(Integer vetId) {
		this.vetId = vetId;
	}

	public Long getProtectedClinicalPayloadId() {
		return this.protectedClinicalPayloadId;
	}

	public void setProtectedClinicalPayloadId(Long protectedClinicalPayloadId) {
		this.protectedClinicalPayloadId = protectedClinicalPayloadId;
	}

	public Long getOutcomeEventId() {
		return this.outcomeEventId;
	}

	public void setOutcomeEventId(Long outcomeEventId) {
		this.outcomeEventId = outcomeEventId;
	}

}
