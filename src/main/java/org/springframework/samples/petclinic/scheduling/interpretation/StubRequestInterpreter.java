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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Deterministic offline adapter for {@link RequestInterpreter} (RULE-2, RULE-17, AC-137).
 * Never invokes external LLM services.
 */
@Service
@ConditionalOnProperty(name = "scheduling.ai.provider", havingValue = "stub")
public class StubRequestInterpreter implements RequestInterpreter {

	private final Map<String, InterpretationResult> responsesByKeyword = new HashMap<>();

	private InterpretationResult nextResult;

	public void setNextResult(InterpretationResult nextResult) {
		this.nextResult = nextResult;
	}

	public void registerResponse(String keyword, InterpretationResult result) {
		this.responsesByKeyword.put(keyword.toLowerCase(), result);
	}

	public void clear() {
		this.responsesByKeyword.clear();
		this.nextResult = null;
	}

	@Override
	public InterpretationResult interpret(String reasonText, String availabilityText) {
		if (this.nextResult != null) {
			InterpretationResult res = this.nextResult;
			this.nextResult = null;
			return res;
		}

		String reason = reasonText != null ? reasonText.toLowerCase() : "";
		String avail = availabilityText != null ? availabilityText.toLowerCase() : "";

		for (Map.Entry<String, InterpretationResult> entry : this.responsesByKeyword.entrySet()) {
			if (reason.contains(entry.getKey()) || avail.contains(entry.getKey())) {
				return entry.getValue();
			}
		}

		if (reason.contains("contradiction") || avail.contains("contradiction") || reason.contains("unparseable")
				|| avail.contains("unparseable") || reason.contains("cannot_interpret")
				|| avail.contains("cannot_interpret")) {
			return InterpretationResult.cannotInterpret("{\"error\": \"contradiction\"}", "stub-model", "1.0");
		}

		CareType careType = CareType.GENERAL;
		String specialty = null;
		int estimatedMinutes = 30;

		if (reason.contains("surgery") || reason.contains("operation")) {
			careType = CareType.SPECIALTY;
			specialty = "surgery";
			estimatedMinutes = 60;
		}
		else if (reason.contains("radiology") || reason.contains("x-ray")) {
			careType = CareType.SPECIALTY;
			specialty = "radiology";
			estimatedMinutes = 45;
		}
		else if (reason.contains("dentistry") || reason.contains("teeth")) {
			careType = CareType.SPECIALTY;
			specialty = "dentistry";
			estimatedMinutes = 45;
		}
		else if (reason.contains("quick") || reason.contains("vaccine") || reason.contains("shot")) {
			estimatedMinutes = 15;
		}

		List<AvailabilityWindow> windows = new ArrayList<>();
		if (avail.contains("monday morning") || avail.contains("mon morning")) {
			windows.add(AvailabilityWindow.preferredDayOfWeek(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0),
					"monday morning"));
		}
		else if (avail.contains("tuesday afternoon") || avail.contains("tue afternoon")) {
			windows.add(AvailabilityWindow.preferredDayOfWeek(DayOfWeek.TUESDAY, LocalTime.of(13, 0),
					LocalTime.of(17, 0), "tuesday afternoon"));
		}
		else if (avail.contains("friday") || avail.contains("fri")) {
			windows.add(AvailabilityWindow.preferredDayOfWeek(DayOfWeek.FRIDAY, LocalTime.of(9, 0), LocalTime.of(17, 0),
					"friday"));
		}
		else {
			windows.add(AvailabilityWindow.preferred(LocalDate.of(2026, 9, 8), LocalTime.of(9, 0), LocalTime.of(17, 0),
					"anytime"));
		}

		return new InterpretationResult(reasonText, estimatedMinutes, careType, specialty, null, false, windows,
				"{\"mock\": \"response\"}", "stub-model", "1.0");
	}

}
