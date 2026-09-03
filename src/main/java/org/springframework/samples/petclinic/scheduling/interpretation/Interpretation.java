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

package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.springframework.samples.petclinic.model.BaseEntity;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.vet.Vet;

/**
 * Persistent interpretation entity for appointment requests (RULE-1, RULE-8).
 */
@Entity
@Table(name = "interpretation")
public class Interpretation extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "request_id", nullable = false)
	private SchedulingRequest request;

	@Column(name = "version", nullable = false)
	private int version;

	@Enumerated(EnumType.STRING)
	@Column(name = "provenance", nullable = false, length = 20)
	private Provenance provenance;

	@Column(name = "reason_summary", length = 500)
	private String reasonSummary;

	@Column(name = "estimated_minutes")
	private Integer estimatedMinutes;

	@Enumerated(EnumType.STRING)
	@Column(name = "care_type", length = 50)
	private CareType careType;

	@Column(name = "specialty", length = 50)
	private String specialty;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "preferred_vet_id")
	private Vet preferredVet;

	@Column(name = "cannot_interpret", nullable = false)
	private boolean cannotInterpret;

	@Column(name = "raw_response")
	private String rawResponse;

	@Column(name = "model_tag", length = 100)
	private String modelTag;

	@Column(name = "prompt_version", length = 50)
	private String promptVersion;

	@Column(name = "created_at", nullable = false)
	private ZonedDateTime createdAt;

	@OneToMany(mappedBy = "interpretation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	private List<InterpretationWindow> windows = new ArrayList<>();

	public SchedulingRequest getRequest() {
		return this.request;
	}

	public void setRequest(SchedulingRequest request) {
		this.request = request;
	}

	public int getVersion() {
		return this.version;
	}

	public void setVersion(int version) {
		this.version = version;
	}

	public Provenance getProvenance() {
		return this.provenance;
	}

	public void setProvenance(Provenance provenance) {
		this.provenance = provenance;
	}

	public String getReasonSummary() {
		return this.reasonSummary;
	}

	public void setReasonSummary(String reasonSummary) {
		this.reasonSummary = reasonSummary;
	}

	public Integer getEstimatedMinutes() {
		return this.estimatedMinutes;
	}

	public void setEstimatedMinutes(Integer estimatedMinutes) {
		this.estimatedMinutes = estimatedMinutes;
	}

	public CareType getCareType() {
		return this.careType;
	}

	public void setCareType(CareType careType) {
		this.careType = careType;
	}

	public String getSpecialty() {
		return this.specialty;
	}

	public void setSpecialty(String specialty) {
		this.specialty = specialty;
	}

	public Vet getPreferredVet() {
		return this.preferredVet;
	}

	public void setPreferredVet(Vet preferredVet) {
		this.preferredVet = preferredVet;
	}

	public boolean isCannotInterpret() {
		return this.cannotInterpret;
	}

	public void setCannotInterpret(boolean cannotInterpret) {
		this.cannotInterpret = cannotInterpret;
	}

	public String getRawResponse() {
		return this.rawResponse;
	}

	public void setRawResponse(String rawResponse) {
		this.rawResponse = rawResponse;
	}

	public String getModelTag() {
		return this.modelTag;
	}

	public void setModelTag(String modelTag) {
		this.modelTag = modelTag;
	}

	public String getPromptVersion() {
		return this.promptVersion;
	}

	public void setPromptVersion(String promptVersion) {
		this.promptVersion = promptVersion;
	}

	public ZonedDateTime getCreatedAt() {
		return this.createdAt;
	}

	public void setCreatedAt(ZonedDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public List<InterpretationWindow> getWindows() {
		return this.windows;
	}

	public void setWindows(List<InterpretationWindow> windows) {
		this.windows = windows;
	}

	public void addWindow(InterpretationWindow window) {
		this.windows.add(window);
		window.setInterpretation(this);
	}

	public void removeWindow(InterpretationWindow window) {
		this.windows.remove(window);
		window.setInterpretation(null);
	}

}
