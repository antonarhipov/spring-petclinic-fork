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

package org.springframework.samples.petclinic.scheduling.clinic;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.samples.petclinic.model.BaseEntity;

/**
 * Persistent entity representing a whole-clinic closure date (Layer 4: Clinic closure)
 * (AC-100..104, RULE-34).
 */
@Entity
@Table(name = "clinic_closure")
public class ClinicClosure extends BaseEntity {

	@Column(name = "closure_date", nullable = false)
	private LocalDate closureDate;

	@Column(name = "reason")
	private String reason;

	public LocalDate getClosureDate() {
		return this.closureDate;
	}

	public void setClosureDate(LocalDate closureDate) {
		this.closureDate = closureDate;
	}

	public String getReason() {
		return this.reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

}
