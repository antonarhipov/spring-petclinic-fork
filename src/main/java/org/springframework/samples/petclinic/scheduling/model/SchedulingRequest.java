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

package org.springframework.samples.petclinic.scheduling.model;

import java.time.Instant;
import java.time.LocalDate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.samples.petclinic.model.BaseEntity;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.Pet;

@Entity
@Table(name = "scheduling_requests")
public class SchedulingRequest extends BaseEntity {

	@Version
	@Column(name = "version")
	private Integer version;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id", nullable = false)
	private Owner owner;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "pet_id", nullable = false)
	private Pet pet;

	@Enumerated(EnumType.STRING)
	@Column(name = "state", nullable = false, length = 30)
	private RequestState state = RequestState.DRAFT;

	@Enumerated(EnumType.STRING)
	@Column(name = "queue_reason", length = 50)
	private QueueReason queueReason;

	@Column(name = "raw_text", columnDefinition = "TEXT")
	private String rawText;

	@Column(name = "interpretation_json", columnDefinition = "TEXT")
	private String interpretationJson;

	@Column(name = "ai_consent", nullable = false)
	private boolean aiConsent;

	@Column(name = "preferred_date_start")
	private LocalDate preferredDateStart;

	@Column(name = "preferred_date_end")
	private LocalDate preferredDateEnd;

	@Column(name = "active_pet_key")
	private Integer activePetKey;

	@Column(name = "created_at")
	private Instant createdAt;

	@Column(name = "updated_at")
	private Instant updatedAt;

	@Column(name = "queued_at")
	private Instant queuedAt;

	@PrePersist
	public void prePersist() {
		Instant now = Instant.now();
		if (this.createdAt == null) {
			this.createdAt = now;
		}
		this.updatedAt = now;
		syncActivePetKey();
	}

	@PreUpdate
	public void preUpdate() {
		this.updatedAt = Instant.now();
		syncActivePetKey();
	}

	public void syncActivePetKey() {
		if (this.state != null && this.state.isTerminal()) {
			this.activePetKey = null;
		}
		else if (this.pet != null) {
			this.activePetKey = this.pet.getId();
		}
	}

	public Integer getVersion() {
		return this.version;
	}

	public void setVersion(Integer version) {
		this.version = version;
	}

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
		syncActivePetKey();
	}

	public RequestState getState() {
		return this.state;
	}

	public void setState(RequestState state) {
		this.state = state;
		syncActivePetKey();
	}

	public QueueReason getQueueReason() {
		return this.queueReason;
	}

	public void setQueueReason(QueueReason queueReason) {
		this.queueReason = queueReason;
	}

	public String getRawText() {
		return this.rawText;
	}

	public void setRawText(String rawText) {
		this.rawText = rawText;
	}

	public String getInterpretationJson() {
		return this.interpretationJson;
	}

	public void setInterpretationJson(String interpretationJson) {
		this.interpretationJson = interpretationJson;
	}

	public org.springframework.samples.petclinic.scheduling.ai.Interpretation getInterpretation() {
		if (this.interpretationJson == null || this.interpretationJson.isBlank()) {
			return null;
		}
		try {
			return new tools.jackson.databind.ObjectMapper().readValue(this.interpretationJson,
					org.springframework.samples.petclinic.scheduling.ai.Interpretation.class);
		}
		catch (Exception e) {
			return null;
		}
	}

	public void transitionTo(RequestState targetState, QueueReason reason) {
		if (targetState == RequestState.STAFF_QUEUED) {
			if (reason == null) {
				throw new IllegalArgumentException("QueueReason must be provided when transitioning to STAFF_QUEUED");
			}
			this.queueReason = reason;
			this.queuedAt = Instant.now();
		}
		this.state = targetState;
		syncActivePetKey();
	}

	public void cancel() {
		transitionTo(RequestState.CANCELLED, null);
	}

	public boolean isAiConsent() {
		return this.aiConsent;
	}

	public void setAiConsent(boolean aiConsent) {
		this.aiConsent = aiConsent;
	}

	public LocalDate getPreferredDateStart() {
		return this.preferredDateStart;
	}

	public void setPreferredDateStart(LocalDate preferredDateStart) {
		this.preferredDateStart = preferredDateStart;
	}

	public LocalDate getPreferredDateEnd() {
		return this.preferredDateEnd;
	}

	public void setPreferredDateEnd(LocalDate preferredDateEnd) {
		this.preferredDateEnd = preferredDateEnd;
	}

	public Integer getActivePetKey() {
		return this.activePetKey;
	}

	public void setActivePetKey(Integer activePetKey) {
		this.activePetKey = activePetKey;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	public Instant getQueuedAt() {
		return this.queuedAt;
	}

	public void setQueuedAt(Instant queuedAt) {
		this.queuedAt = queuedAt;
	}

}
