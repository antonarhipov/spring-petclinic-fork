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

package org.springframework.samples.petclinic.scheduling.ai;

import java.util.List;

public record Interpretation(String summary, Integer visitDurationMinutes, CareType careType, String requiredSpecialty,
		UrgencyLevel urgency, List<SymbolicWindow> preferredWindows, List<SymbolicWindow> allowedWindows,
		List<SymbolicWindow> excludedWindows, String preferredVetName, Double confidence) {

	public Interpretation {
		if (careType == null) {
			careType = CareType.GENERAL;
		}
		if (urgency == null) {
			urgency = UrgencyLevel.ROUTINE;
		}
		if (preferredWindows == null) {
			preferredWindows = List.of();
		}
		if (allowedWindows == null) {
			allowedWindows = List.of();
		}
		if (excludedWindows == null) {
			excludedWindows = List.of();
		}
		if (confidence == null) {
			confidence = 1.0;
		}
	}

	public boolean isLowConfidence() {
		return confidence < 0.6 || summary == null || summary.isBlank();
	}

}
