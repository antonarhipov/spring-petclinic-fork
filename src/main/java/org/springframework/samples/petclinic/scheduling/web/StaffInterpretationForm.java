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

package org.springframework.samples.petclinic.scheduling.web;

/**
 * Backing form object for staff-authored and staff-edited interpretations (AC-89).
 */
public class StaffInterpretationForm {

	private String reasonSummary;

	private Integer estimatedMinutes;

	private String careType;

	private String specialty;

	private Integer preferredVetId;

	private boolean cannotInterpret;

	private String preferredWindows;

	private String allowedWindows;

	private String excludedWindows;

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

	public String getCareType() {
		return this.careType;
	}

	public void setCareType(String careType) {
		this.careType = careType;
	}

	public String getSpecialty() {
		return this.specialty;
	}

	public void setSpecialty(String specialty) {
		this.specialty = specialty;
	}

	public Integer getPreferredVetId() {
		return this.preferredVetId;
	}

	public void setPreferredVetId(Integer preferredVetId) {
		this.preferredVetId = preferredVetId;
	}

	public boolean isCannotInterpret() {
		return this.cannotInterpret;
	}

	public void setCannotInterpret(boolean cannotInterpret) {
		this.cannotInterpret = cannotInterpret;
	}

	public String getPreferredWindows() {
		return this.preferredWindows;
	}

	public void setPreferredWindows(String preferredWindows) {
		this.preferredWindows = preferredWindows;
	}

	public String getAllowedWindows() {
		return this.allowedWindows;
	}

	public void setAllowedWindows(String allowedWindows) {
		this.allowedWindows = allowedWindows;
	}

	public String getExcludedWindows() {
		return this.excludedWindows;
	}

	public void setExcludedWindows(String excludedWindows) {
		this.excludedWindows = excludedWindows;
	}

}
