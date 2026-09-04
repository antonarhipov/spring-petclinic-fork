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

package org.springframework.samples.petclinic.scheduling.calendar;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * View model representing a complete day on the staff calendar with five capacity layers
 * and slot states (AC-96, AC-98, RULE-33).
 */
public record CalendarDayView(LocalDate date, LocalDate prevDate, LocalDate nextDate, LocalDate today,
		LocalDate minDate, LocalDate maxDate, String clinicHoursSummary, boolean clinicClosed,
		List<VetColumn> vetColumns, List<GridRow> gridRows) {

	public record VetColumn(Integer vetId, String vetName, String specialties, String openingHoursLayer,
			String effectiveBlocksLayer, int bookedCount, int bookedMinutes, int heldCount, int heldMinutes,
			int remainingCapacityMinutes) {
	}

	public record GridRow(LocalTime time, String timeLabel, List<Cell> cells) {
	}

	public record Cell(Integer vetId, String vetName, LocalTime time, String timeLabel, String state, // "closed",
																										// "off-shift",
																										// "free",
																										// "booked",
																										// "held"
			Integer appointmentId, Integer requestId, String label) {
	}

}
