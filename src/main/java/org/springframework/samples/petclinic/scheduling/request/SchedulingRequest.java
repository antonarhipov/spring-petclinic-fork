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

package org.springframework.samples.petclinic.scheduling.request;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.samples.petclinic.model.BaseEntity;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.vet.Vet;

/**
 * Persistent scheduling request aggregate (RULE-1, RULE-7, RULE-8, RULE-9).
 */
@Entity
@Table(name = "scheduling_request")
public class SchedulingRequest extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "pet_id", nullable = false)
	private Pet pet;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id", nullable = false)
	private Owner owner;

	@Enumerated(EnumType.STRING)
	@Column(name = "state", nullable = false, length = 50)
	private RequestState state;

	@Column(name = "reason_text", nullable = false)
	private String reasonText;

	@Column(name = "availability_text", nullable = false)
	private String availabilityText;

	@Column(name = "active_pet_id")
	private Integer activePetId;

	@Column(name = "failed_attempts", nullable = false)
	private int failedAttempts = 0;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "held_vet_id")
	private Vet heldVet;

	@Column(name = "held_start")
	private ZonedDateTime heldStart;

	@Column(name = "held_duration")
	private Integer heldDuration;

	@Column(name = "created_at", nullable = false)
	private ZonedDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private ZonedDateTime updatedAt;

	public Pet getPet() {
		return this.pet;
	}

	public void setPet(Pet pet) {
		this.pet = pet;
	}

	public Owner getOwner() {
		return this.owner;
	}

	public void setOwner(Owner owner) {
		this.owner = owner;
	}

	public RequestState getState() {
		return this.state;
	}

	public void setState(RequestState state) {
		this.state = state;
	}

	public String getReasonText() {
		return this.reasonText;
	}

	public void setReasonText(String reasonText) {
		this.reasonText = reasonText;
	}

	public String getAvailabilityText() {
		return this.availabilityText;
	}

	public void setAvailabilityText(String availabilityText) {
		this.availabilityText = availabilityText;
	}

	public Integer getActivePetId() {
		return this.activePetId;
	}

	public void setActivePetId(Integer activePetId) {
		this.activePetId = activePetId;
	}

	public int getFailedAttempts() {
		return this.failedAttempts;
	}

	public void setFailedAttempts(int failedAttempts) {
		this.failedAttempts = failedAttempts;
	}

	public Vet getHeldVet() {
		return this.heldVet;
	}

	public void setHeldVet(Vet heldVet) {
		this.heldVet = heldVet;
	}

	public ZonedDateTime getHeldStart() {
		return this.heldStart;
	}

	public void setHeldStart(ZonedDateTime heldStart) {
		this.heldStart = heldStart;
	}

	public Integer getHeldDuration() {
		return this.heldDuration;
	}

	public void setHeldDuration(Integer heldDuration) {
		this.heldDuration = heldDuration;
	}

	public ZonedDateTime getCreatedAt() {
		return this.createdAt;
	}

	public void setCreatedAt(ZonedDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public ZonedDateTime getUpdatedAt() {
		return this.updatedAt;
	}

	public void setUpdatedAt(ZonedDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

	public void clearHold() {
		this.heldVet = null;
		this.heldStart = null;
		this.heldDuration = null;
	}

	public void setHold(Vet vet, ZonedDateTime start, int duration) {
		this.heldVet = vet;
		this.heldStart = start;
		this.heldDuration = duration;
	}

	public boolean hasHold() {
		return this.heldVet != null && this.heldStart != null && this.heldDuration != null;
	}

}
