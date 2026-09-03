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

package org.springframework.samples.petclinic.scheduling.solver;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.samples.petclinic.scheduling.interpretation.Interpretation;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.vet.Vet;

/**
 * Deterministic test double for {@link SlotRanker} (RULE-2, AC-138).
 */
public class StubSlotRanker implements SlotRanker {

	private final Map<Integer, List<RankedSlot>> slotsByRequestId = new HashMap<>();

	private List<RankedSlot> nextSlots;

	private Vet fallbackVet;

	private ZonedDateTime fallbackStart;

	public void setNextSlots(List<RankedSlot> nextSlots) {
		this.nextSlots = nextSlots;
	}

	public void registerSlotsForRequest(Integer requestId, List<RankedSlot> slots) {
		this.slotsByRequestId.put(requestId, slots);
	}

	public void setFallback(Vet vet, ZonedDateTime startTime) {
		this.fallbackVet = vet;
		this.fallbackStart = startTime;
	}

	public void clear() {
		this.slotsByRequestId.clear();
		this.nextSlots = null;
		this.fallbackVet = null;
		this.fallbackStart = null;
	}

	@Override
	public List<RankedSlot> rankSlots(SchedulingRequest request, Interpretation interpretation) {
		if (this.nextSlots != null) {
			List<RankedSlot> res = this.nextSlots;
			this.nextSlots = null;
			return res;
		}

		if (request != null && request.getId() != null && this.slotsByRequestId.containsKey(request.getId())) {
			return this.slotsByRequestId.get(request.getId());
		}

		List<RankedSlot> results = new ArrayList<>();
		if (this.fallbackVet != null && this.fallbackStart != null) {
			int duration = interpretation != null && interpretation.getEstimatedMinutes() != null
					? interpretation.getEstimatedMinutes() : 30;
			results.add(new RankedSlot(this.fallbackVet, this.fallbackStart, duration, "Optimal match",
					"0hard/1medium/2soft"));
		}
		return results;
	}

}
