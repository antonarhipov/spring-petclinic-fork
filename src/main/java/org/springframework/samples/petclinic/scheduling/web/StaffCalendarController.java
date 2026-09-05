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

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.samples.petclinic.scheduling.appointment.AppointmentManagementService;
import org.springframework.samples.petclinic.scheduling.calendar.CalendarDayService;
import org.springframework.samples.petclinic.scheduling.calendar.CalendarDayView;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for the staff day calendar (AC-96..98, AC-104..106, RULE-35).
 */
@Controller
public class StaffCalendarController {

	private final CalendarDayService calendarDayService;

	private final AppointmentManagementService appointmentManagementService;

	public StaffCalendarController(CalendarDayService calendarDayService,
			AppointmentManagementService appointmentManagementService) {
		this.calendarDayService = calendarDayService;
		this.appointmentManagementService = appointmentManagementService;
	}

	@GetMapping("/staff/calendar")
	public String calendar(@RequestParam(name = "date", required = false) String dateStr, Model model) {
		LocalDate targetDate = null;
		if (dateStr != null && !dateStr.isBlank()) {
			try {
				targetDate = LocalDate.parse(dateStr);
			}
			catch (DateTimeParseException ignored) {
			}
		}

		CalendarDayView dayView = this.calendarDayService.getDayView(targetDate);
		model.addAttribute("calendarView", dayView);
		List<Integer> appointmentIds = dayView.gridRows()
			.stream()
			.flatMap(row -> row.cells().stream())
			.map(CalendarDayView.Cell::appointmentId)
			.filter(java.util.Objects::nonNull)
			.distinct()
			.toList();
		model.addAttribute("appointmentChanges", this.appointmentManagementService.latestChanges(appointmentIds));
		return "staff/calendar";
	}

}
