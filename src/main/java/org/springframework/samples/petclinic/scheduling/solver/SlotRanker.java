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
import java.util.List;

import org.springframework.samples.petclinic.scheduling.interpretation.Interpretation;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.vet.Vet;

/**
 * Adapter interface for smart slot ranking and assignment (RULE-2, AC-138). Encapsulates
 * Timefold solver logic without leaking Timefold types in method signatures.
 */
public interface SlotRanker {

	List<RankedSlot> rankSlots(SchedulingRequest request, Interpretation interpretation);

	record RankedSlot(Vet vet, ZonedDateTime startTime, int duration, String explanation, String score) {
	}

}
