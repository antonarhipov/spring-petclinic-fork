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

package org.springframework.samples.petclinic.scheduling.solver;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import org.springframework.samples.petclinic.scheduling.ai.UrgencyLevel;

@PlanningEntity
public class ProposedBooking {

	private Integer requestId;

	private int durationMinutes;

	private UrgencyLevel urgency = UrgencyLevel.ROUTINE;

	@PlanningVariable(valueRangeProviderRefs = "slotRange")
	private CandidateSlot selectedSlot;

	public ProposedBooking() {
	}

	public ProposedBooking(Integer requestId, int durationMinutes) {
		this(requestId, durationMinutes, UrgencyLevel.ROUTINE);
	}

	public ProposedBooking(Integer requestId, int durationMinutes, UrgencyLevel urgency) {
		this.requestId = requestId;
		this.durationMinutes = durationMinutes;
		this.urgency = urgency != null ? urgency : UrgencyLevel.ROUTINE;
	}

	public Integer getRequestId() {
		return this.requestId;
	}

	public void setRequestId(Integer requestId) {
		this.requestId = requestId;
	}

	public int getDurationMinutes() {
		return this.durationMinutes;
	}

	public void setDurationMinutes(int durationMinutes) {
		this.durationMinutes = durationMinutes;
	}

	public UrgencyLevel getUrgency() {
		return this.urgency;
	}

	public void setUrgency(UrgencyLevel urgency) {
		this.urgency = urgency != null ? urgency : UrgencyLevel.ROUTINE;
	}

	public CandidateSlot getSelectedSlot() {
		return this.selectedSlot;
	}

	public void setSelectedSlot(CandidateSlot selectedSlot) {
		this.selectedSlot = selectedSlot;
	}

}
