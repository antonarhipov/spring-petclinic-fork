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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backing form object for veterinarian availability (AC-100, RULE-34).
 */
public class VetAvailabilityForm {

	private Integer vetId;

	private Map<String, String> weeklySchedule = new LinkedHashMap<>();

	private List<String> exceptions = new ArrayList<>();

	private List<String> leaves = new ArrayList<>();

	public Integer getVetId() {
		return this.vetId;
	}

	public void setVetId(Integer vetId) {
		this.vetId = vetId;
	}

	public Map<String, String> getWeeklySchedule() {
		return this.weeklySchedule;
	}

	public void setWeeklySchedule(Map<String, String> weeklySchedule) {
		this.weeklySchedule = weeklySchedule;
	}

	public List<String> getExceptions() {
		return this.exceptions;
	}

	public void setExceptions(List<String> exceptions) {
		this.exceptions = exceptions;
	}

	public List<String> getLeaves() {
		return this.leaves;
	}

	public void setLeaves(List<String> leaves) {
		this.leaves = leaves;
	}

}
