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

/**
 * Value object representing an extracted availability window (RULE-4).
 */
public record AvailabilityWindow(WindowKind kind, LocalDate dateVal, LocalDate startDate, LocalDate endDate,
		DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, String tokens) {

	public static AvailabilityWindow preferred(LocalDate date, LocalTime start, LocalTime end, String tokens) {
		return new AvailabilityWindow(WindowKind.PREFERRED, date, null, null, null, start, end, tokens);
	}

	public static AvailabilityWindow preferredDayOfWeek(DayOfWeek dow, LocalTime start, LocalTime end, String tokens) {
		return new AvailabilityWindow(WindowKind.PREFERRED, null, null, null, dow, start, end, tokens);
	}

	public static AvailabilityWindow allowed(LocalDate startD, LocalDate endD, LocalTime startT, LocalTime endT,
			String tokens) {
		return new AvailabilityWindow(WindowKind.ALLOWED, null, startD, endD, null, startT, endT, tokens);
	}

	public static AvailabilityWindow excluded(LocalDate date, LocalTime start, LocalTime end, String tokens) {
		return new AvailabilityWindow(WindowKind.EXCLUDED, date, null, null, null, start, end, tokens);
	}

}
