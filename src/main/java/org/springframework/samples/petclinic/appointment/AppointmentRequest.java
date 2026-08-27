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
import org.springframework.samples.petclinic.owner.Owner;
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
 * First-class entity representing a scheduling request.
 */
@Entity
@Table(name = "appointment_request")
public class AppointmentRequest extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id", nullable = false)
	private Owner owner;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "pet_id", nullable = false)
	private Pet pet;

	@Column(name = "free_text")
	private String freeText;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 30)
	private AppointmentRequestStatus status = AppointmentRequestStatus.DRAFT;

	@Column(name = "consent_flag", nullable = false)
	private boolean consentFlag = false;

	@Column(name = "consent_at")
	private Instant consentAt;

	@Column(name = "consent_text_snapshot")
	private String consentTextSnapshot;

	@Column(name = "interpretation_json")
	private String interpretationJson;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "resulting_appointment_id")
	private Appointment resultingAppointment;

	@Column(name = "active_hold_id")
	private Integer activeHoldId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "suggested_vet_id")
	private Vet suggestedVet;

	@Column(name = "suggested_start_instant")
	private Instant suggestedStartInstant;

	public Owner getOwner() {
		return this.owner;
	}

	public void setOwner(Owner owner) {
		this.owner = owner;
	}

	public Pet getPet() {
		return this.pet;
	}

	public void setPet(Pet pet) {
		this.pet = pet;
	}

	public String getFreeText() {
		return this.freeText;
	}

	public void setFreeText(String freeText) {
		this.freeText = freeText;
	}

	public AppointmentRequestStatus getStatus() {
		return this.status;
	}

	public void setStatus(AppointmentRequestStatus status) {
		this.status = status;
	}

	public boolean isConsentFlag() {
		return this.consentFlag;
	}

	public void setConsentFlag(boolean consentFlag) {
		this.consentFlag = consentFlag;
	}

	public Instant getConsentAt() {
		return this.consentAt;
	}

	public void setConsentAt(Instant consentAt) {
		this.consentAt = consentAt;
	}

	public String getConsentTextSnapshot() {
		return this.consentTextSnapshot;
	}

	public void setConsentTextSnapshot(String consentTextSnapshot) {
		this.consentTextSnapshot = consentTextSnapshot;
	}

	public String getInterpretationJson() {
		return this.interpretationJson;
	}

	public void setInterpretationJson(String interpretationJson) {
		this.interpretationJson = interpretationJson;
	}

	public Appointment getResultingAppointment() {
		return this.resultingAppointment;
	}

	public void setResultingAppointment(Appointment resultingAppointment) {
		this.resultingAppointment = resultingAppointment;
	}

	public Integer getActiveHoldId() {
		return this.activeHoldId;
	}

	public void setActiveHoldId(Integer activeHoldId) {
		this.activeHoldId = activeHoldId;
	}

	public Vet getSuggestedVet() {
		return this.suggestedVet;
	}

	public Instant getSuggestedStartInstant() {
		return this.suggestedStartInstant;
	}

	public void offer(Vet vet, Instant startInstant, Integer holdId) {
		this.suggestedVet = vet;
		this.suggestedStartInstant = startInstant;
		this.activeHoldId = holdId;
		this.status = AppointmentRequestStatus.HELD;
	}

	public void clearSuggestion() {
		this.suggestedVet = null;
		this.suggestedStartInstant = null;
		this.activeHoldId = null;
	}

}
