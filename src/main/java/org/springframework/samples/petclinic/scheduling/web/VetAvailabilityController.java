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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.samples.petclinic.scheduling.clinic.ClinicClosure;
import org.springframework.samples.petclinic.scheduling.clinic.EffectiveAvailabilityService;
import org.springframework.samples.petclinic.scheduling.clinic.VetAvailabilityService;
import org.springframework.samples.petclinic.scheduling.clinic.VetException;
import org.springframework.samples.petclinic.scheduling.clinic.VetLeave;
import org.springframework.samples.petclinic.scheduling.clinic.VetWeeklyBlock;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller for veterinarian availability management (AC-100, AC-101, AC-107).
 */
@Controller
public class VetAvailabilityController {

	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

	private final EffectiveAvailabilityService availabilityService;

	public VetAvailabilityController(EffectiveAvailabilityService availabilityService) {
		this.availabilityService = availabilityService;
	}

	@GetMapping("/staff/vets/{vetId}/availability")
	public String showAvailability(@PathVariable("vetId") Integer vetId, Model model) {
		VetAvailabilityService.VetAvailabilityData data = this.availabilityService.getAvailabilityData(vetId);
		VetAvailabilityForm form = new VetAvailabilityForm();
		form.setVetId(vetId);
		form.setWeeklySchedule(toScheduleMap(data.weeklyBlocks()));
		model.addAttribute("data", data);
		model.addAttribute("vet", data.vet());
		model.addAttribute("form", form);
		return "staff/vetAvailability";
	}

	@PostMapping("/staff/vets/{vetId}/availability")
	public String saveAvailability(@PathVariable("vetId") Integer vetId,
			@ModelAttribute("form") VetAvailabilityForm form,
			@RequestParam(name = "newExceptionDate", required = false) String exceptionDate,
			@RequestParam(name = "newExceptionUnavailable", required = false) Boolean exceptionUnavailable,
			@RequestParam(name = "newExceptionStart", required = false) String exceptionStart,
			@RequestParam(name = "newExceptionEnd", required = false) String exceptionEnd,
			@RequestParam(name = "newLeaveStartDate", required = false) String leaveStart,
			@RequestParam(name = "newLeaveEndDate", required = false) String leaveEnd,
			@RequestParam(name = "newLeaveReason", required = false) String leaveReason,
			@RequestParam(name = "newClosureDate", required = false) String closureDate,
			@RequestParam(name = "newClosureReason", required = false) String closureReason, Principal principal,
			RedirectAttributes redirectAttributes) {
		VetAvailabilityService.VetAvailabilityData current = this.availabilityService.getAvailabilityData(vetId);
		Map<DayOfWeek, List<VetAvailabilityService.TimeInterval>> schedule = parseSchedule(form.getWeeklySchedule());
		List<VetAvailabilityService.VetExceptionDto> exceptions = exceptionDtos(current.exceptions());
		List<VetAvailabilityService.VetLeaveDto> leaves = leaveDtos(current.leaves());
		List<VetAvailabilityService.ClinicClosureDto> closures = closureDtos(current.closures());

		if (hasText(exceptionDate)) {
			LocalDate date = LocalDate.parse(exceptionDate);
			exceptions.removeIf(exception -> exception.date().equals(date));
			if (Boolean.TRUE.equals(exceptionUnavailable)) {
				exceptions.add(new VetAvailabilityService.VetExceptionDto(date, true, null, null));
			}
			else if (hasText(exceptionStart) && hasText(exceptionEnd)) {
				exceptions.add(new VetAvailabilityService.VetExceptionDto(date, false, LocalTime.parse(exceptionStart),
						LocalTime.parse(exceptionEnd)));
			}
		}
		if (hasText(leaveStart) && hasText(leaveEnd)) {
			leaves.add(new VetAvailabilityService.VetLeaveDto(LocalDate.parse(leaveStart), LocalDate.parse(leaveEnd),
					leaveReason));
		}
		if (hasText(closureDate)) {
			closures.add(new VetAvailabilityService.ClinicClosureDto(LocalDate.parse(closureDate), closureReason));
		}

		String actor = principal == null || principal.getName() == null || principal.getName().isBlank() ? "staff"
				: principal.getName();
		EffectiveAvailabilityService.AvailabilityUpdateResult result = this.availabilityService.saveSchedule(vetId,
				schedule, exceptions, leaves, closures, actor);
		if (!result.success()) {
			redirectAttributes.addFlashAttribute("error", "confirmedConflictWarning");
			redirectAttributes.addFlashAttribute("conflicts", result.conflicts());
			return "redirect:/staff/vets/" + vetId + "/availability";
		}
		if (result.outOfHoursWarning()) {
			redirectAttributes.addFlashAttribute("warning", "outOfHoursWarning");
		}
		if (result.holdsInvalidated()) {
			redirectAttributes.addFlashAttribute("notice", "holdConflictNotice");
		}
		redirectAttributes.addFlashAttribute("message", "availabilitySaved");
		return "redirect:/staff/vets/" + vetId + "/availability";
	}

	private Map<String, String> toScheduleMap(List<VetWeeklyBlock> blocks) {
		Map<String, String> values = new LinkedHashMap<>();
		for (DayOfWeek day : DayOfWeek.values()) {
			for (int slot = 1; slot <= 2; slot++) {
				values.put(day.name() + "_" + slot + "_active", "false");
				values.put(day.name() + "_" + slot + "_start", slot == 1 ? "09:00" : "13:00");
				values.put(day.name() + "_" + slot + "_end", slot == 1 ? "12:00" : "17:00");
			}
		}
		Map<DayOfWeek, Integer> positions = new LinkedHashMap<>();
		for (VetWeeklyBlock block : blocks) {
			int slot = positions.merge(block.getDayOfWeek(), 1, Integer::sum);
			if (slot <= 2) {
				String prefix = block.getDayOfWeek().name() + "_" + slot;
				values.put(prefix + "_active", "true");
				values.put(prefix + "_start", block.getStartTime().format(TIME_FORMATTER));
				values.put(prefix + "_end", block.getEndTime().format(TIME_FORMATTER));
			}
		}
		return values;
	}

	private Map<DayOfWeek, List<VetAvailabilityService.TimeInterval>> parseSchedule(Map<String, String> raw) {
		if (raw == null || raw.isEmpty()) {
			return null;
		}
		Map<DayOfWeek, List<VetAvailabilityService.TimeInterval>> schedule = new LinkedHashMap<>();
		for (DayOfWeek day : DayOfWeek.values()) {
			for (int slot = 1; slot <= 2; slot++) {
				String prefix = day.name() + "_" + slot;
				if ("true".equalsIgnoreCase(raw.get(prefix + "_active"))) {
					schedule.computeIfAbsent(day, ignored -> new ArrayList<>())
						.add(new VetAvailabilityService.TimeInterval(LocalTime.parse(raw.get(prefix + "_start")),
								LocalTime.parse(raw.get(prefix + "_end"))));
				}
			}
		}
		return schedule;
	}

	private List<VetAvailabilityService.VetExceptionDto> exceptionDtos(List<VetException> exceptions) {
		return new ArrayList<>(exceptions.stream()
			.map(exception -> new VetAvailabilityService.VetExceptionDto(exception.getExceptionDate(),
					exception.isUnavailable(), exception.getStartTime(), exception.getEndTime()))
			.toList());
	}

	private List<VetAvailabilityService.VetLeaveDto> leaveDtos(List<VetLeave> leaves) {
		return new ArrayList<>(leaves.stream()
			.map(leave -> new VetAvailabilityService.VetLeaveDto(leave.getStartDate(), leave.getEndDate(),
					leave.getReason()))
			.toList());
	}

	private List<VetAvailabilityService.ClinicClosureDto> closureDtos(List<ClinicClosure> closures) {
		return new ArrayList<>(closures.stream()
			.map(closure -> new VetAvailabilityService.ClinicClosureDto(closure.getClosureDate(), closure.getReason()))
			.toList());
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

}
