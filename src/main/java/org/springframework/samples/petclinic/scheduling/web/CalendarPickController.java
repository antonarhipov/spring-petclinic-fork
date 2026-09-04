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

import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;

import org.springframework.samples.petclinic.scheduling.calendar.CalendarDayService;
import org.springframework.samples.petclinic.scheduling.calendar.CalendarDayView;
import org.springframework.samples.petclinic.scheduling.interpretation.Interpretation;
import org.springframework.samples.petclinic.scheduling.request.HoldService;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.StaffSuggestionService;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller for calendar picking mode (AC-90, AC-97, RULE-31, RULE-33).
 */
@Controller
public class CalendarPickController {

	private final CalendarDayService calendarDayService;

	private final StaffOperationsQueryService queryService;

	private final StaffSuggestionService staffSuggestionService;

	private final HoldService holdService;

	private final Clock clock;

	public CalendarPickController(CalendarDayService calendarDayService, StaffOperationsQueryService queryService,
			StaffSuggestionService staffSuggestionService, HoldService holdService, Clock clock) {
		this.calendarDayService = calendarDayService;
		this.queryService = queryService;
		this.staffSuggestionService = staffSuggestionService;
		this.holdService = holdService;
		this.clock = clock;
	}

	@GetMapping("/staff/calendar/pick/{requestId}")
	public String pickCalendar(@PathVariable("requestId") Integer requestId,
			@RequestParam(name = "date", required = false) String dateStr, Model model) {
		SchedulingRequest request = this.queryService.getRequest(requestId);
		if (request.getState() != RequestState.WITH_STAFF) {
			return "redirect:/staff/requests/" + requestId;
		}

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
		model.addAttribute("pickingRequestId", requestId);
		model.addAttribute("request", request);
		return "staff/calendar";
	}

	@PostMapping("/staff/calendar/pick/{requestId}")
	public String handlePick(@PathVariable("requestId") Integer requestId, @RequestParam("vetId") Integer vetId,
			@RequestParam("appointmentDate") String appointmentDateStr, @RequestParam("startTime") String startTimeStr,
			@RequestParam(name = "durationMinutes", required = false) Integer durationMinutes, Principal principal,
			RedirectAttributes redirectAttributes) {
		SchedulingRequest request = this.queryService.getRequest(requestId);

		if (request.getState() != RequestState.WITH_STAFF) {
			redirectAttributes.addFlashAttribute("error", "requestActionNotAllowed");
			return "redirect:/staff/calendar/pick/" + requestId + "?date=" + appointmentDateStr;
		}

		Interpretation interpretation = this.queryService.getLatestInterpretation(requestId).orElse(null);
		int duration = (durationMinutes != null) ? durationMinutes
				: (interpretation != null && interpretation.getEstimatedMinutes() != null)
						? interpretation.getEstimatedMinutes() : 30;

		LocalDate appointmentDate;
		LocalTime startTime;
		try {
			appointmentDate = LocalDate.parse(appointmentDateStr);
			startTime = LocalTime.parse(startTimeStr);
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("error", "holdUnavailable");
			return "redirect:/staff/calendar/pick/" + requestId;
		}

		CalendarDayView dayView = this.calendarDayService.getDayView(appointmentDate);

		// Validate cell is free in the calendar day view
		boolean isFree = dayView.gridRows()
			.stream()
			.filter(row -> row.time().equals(startTime))
			.flatMap(row -> row.cells().stream())
			.anyMatch(cell -> Objects.equals(cell.vetId(), vetId) && "free".equals(cell.state()));

		if (!isFree) {
			redirectAttributes.addFlashAttribute("error", "holdUnavailable");
			return "redirect:/staff/calendar/pick/" + requestId + "?date=" + appointmentDateStr;
		}

		Vet vet = this.queryService.getVet(vetId);

		ZoneId zoneId = this.clock.getZone();
		ZonedDateTime startDateTime = appointmentDate.atTime(startTime).atZone(zoneId);

		// Also check holdService isAvailable
		if (!this.holdService.isAvailable(vetId, startDateTime, duration, requestId)) {
			redirectAttributes.addFlashAttribute("error", "holdUnavailable");
			return "redirect:/staff/calendar/pick/" + requestId + "?date=" + appointmentDateStr;
		}

		String actor = (principal != null && principal.getName() != null && !principal.getName().isBlank())
				? principal.getName() : "staff";
		this.staffSuggestionService.placeHold(request, vet, startDateTime, duration, actor);

		redirectAttributes.addFlashAttribute("message", "suggestionOffered");
		return "redirect:/staff/requests/" + requestId;
	}

}
