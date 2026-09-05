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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.samples.petclinic.scheduling.clinic.ClinicConfig;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicOpeningHour;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicPartOfDay;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicSettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller for clinic settings editor (AC-96, AC-99, RULE-37).
 */
@Controller
public class ClinicSettingsController {

	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

	private final ClinicSettingsService settingsService;

	public ClinicSettingsController(ClinicSettingsService settingsService) {
		this.settingsService = settingsService;
	}

	@GetMapping("/staff/settings")
	public String showSettings(Model model) {
		ClinicConfig config = this.settingsService.current();
		List<ClinicOpeningHour> openingHours = this.settingsService.allOpeningHours();
		List<ClinicPartOfDay> partsOfDay = this.settingsService.allPartsOfDay();

		ClinicSettingsForm form = new ClinicSettingsForm();
		form.setHorizonDays(config.getBookingHorizonDays());
		form.setMinDurationMinutes(config.getMinDurationMinutes());
		form.setMaxDurationMinutes(config.getMaxDurationMinutes());
		form.setDefaultDurationMinutes(config.getDefaultDurationMinutes());
		form.setEmergencyPhone(config.getEmergencyPhone());
		form.setTimeZone(config.getTimeZone());

		Map<String, String> hoursMap = new LinkedHashMap<>();
		for (ClinicOpeningHour h : openingHours) {
			String day = h.getDayOfWeek().name();
			hoursMap.put(day + "_closed", String.valueOf(h.isClosed()));
			hoursMap.put(day + "_open", h.getOpenTime() != null ? h.getOpenTime().format(TIME_FORMATTER) : "09:00");
			hoursMap.put(day + "_close", h.getCloseTime() != null ? h.getCloseTime().format(TIME_FORMATTER) : "17:00");
		}
		form.setOpeningHours(hoursMap);

		for (ClinicPartOfDay part : partsOfDay) {
			if ("morning".equalsIgnoreCase(part.getName())) {
				form.setMorningStart(part.getStartTime().format(TIME_FORMATTER));
				form.setMorningEnd(part.getEndTime().format(TIME_FORMATTER));
			}
			else if ("afternoon".equalsIgnoreCase(part.getName())) {
				form.setAfternoonStart(part.getStartTime().format(TIME_FORMATTER));
				form.setAfternoonEnd(part.getEndTime().format(TIME_FORMATTER));
			}
			else if ("evening".equalsIgnoreCase(part.getName())) {
				form.setEveningStart(part.getStartTime().format(TIME_FORMATTER));
				form.setEveningEnd(part.getEndTime().format(TIME_FORMATTER));
			}
		}

		model.addAttribute("form", form);
		model.addAttribute("openingHoursList", openingHours);
		model.addAttribute("partsOfDayList", partsOfDay);
		return "staff/settings";
	}

	@PostMapping("/staff/settings")
	public String saveSettings(@ModelAttribute("form") ClinicSettingsForm form, BindingResult bindingResult,
			Principal principal, Model model, RedirectAttributes redirectAttributes) {

		// 1. Duration bounds: min <= default <= max
		if (form.getMinDurationMinutes() != null && form.getMaxDurationMinutes() != null) {
			if (form.getMinDurationMinutes() > form.getMaxDurationMinutes()) {
				bindingResult
					.addError(new FieldError("form", "minDurationMinutes", "Min duration must be <= max duration"));
			}
		}
		if (form.getDefaultDurationMinutes() != null) {
			if (form.getMinDurationMinutes() != null
					&& form.getDefaultDurationMinutes() < form.getMinDurationMinutes()) {
				bindingResult.addError(
						new FieldError("form", "defaultDurationMinutes", "Default duration must be >= min duration"));
			}
			if (form.getMaxDurationMinutes() != null
					&& form.getDefaultDurationMinutes() > form.getMaxDurationMinutes()) {
				bindingResult.addError(
						new FieldError("form", "defaultDurationMinutes", "Default duration must be <= max duration"));
			}
		}

		// 2. Opening hours: start < end for each non-closed day
		Map<String, String> hours = form.getOpeningHours();
		if (hours != null) {
			for (ClinicOpeningHour h : this.settingsService.allOpeningHours()) {
				String day = h.getDayOfWeek().name();
				boolean isClosed = "true".equalsIgnoreCase(hours.get(day + "_closed"));
				if (!isClosed) {
					String openStr = hours.get(day + "_open");
					String closeStr = hours.get(day + "_close");
					if (openStr != null && closeStr != null) {
						try {
							LocalTime openTime = LocalTime.parse(openStr);
							LocalTime closeTime = LocalTime.parse(closeStr);
							if (!openTime.isBefore(closeTime)) {
								bindingResult.addError(new FieldError("form", "openingHours",
										"Opening time must be before closing time for " + day));
							}
						}
						catch (Exception ex) {
							bindingResult.addError(
									new FieldError("form", "openingHours", "Invalid opening hours format for " + day));
						}
					}
				}
			}
		}

		// 3. Parts of day: contiguous and start < end
		try {
			LocalTime mStart = LocalTime.parse(form.getMorningStart());
			LocalTime mEnd = LocalTime.parse(form.getMorningEnd());
			LocalTime aStart = LocalTime.parse(form.getAfternoonStart());
			LocalTime aEnd = LocalTime.parse(form.getAfternoonEnd());
			LocalTime eStart = LocalTime.parse(form.getEveningStart());
			LocalTime eEnd = LocalTime.parse(form.getEveningEnd());

			if (!mStart.isBefore(mEnd)) {
				bindingResult
					.addError(new FieldError("form", "morningStart", "Morning start must be before morning end"));
			}
			if (!mEnd.equals(aStart)) {
				bindingResult.addError(new FieldError("form", "afternoonStart",
						"Morning end must equal afternoon start (contiguous)"));
			}
			if (!aStart.isBefore(aEnd)) {
				bindingResult
					.addError(new FieldError("form", "afternoonStart", "Afternoon start must be before afternoon end"));
			}
			if (!aEnd.equals(eStart)) {
				bindingResult.addError(
						new FieldError("form", "eveningStart", "Afternoon end must equal evening start (contiguous)"));
			}
			if (!eStart.isBefore(eEnd)) {
				bindingResult
					.addError(new FieldError("form", "eveningStart", "Evening start must be before evening end"));
			}
		}
		catch (Exception ex) {
			bindingResult.addError(new FieldError("form", "morningStart", "Invalid time format for parts of day"));
		}

		if (bindingResult.hasErrors()) {
			model.addAttribute("openingHoursList", this.settingsService.allOpeningHours());
			model.addAttribute("partsOfDayList", this.settingsService.allPartsOfDay());
			return "staff/settings";
		}

		String actor = (principal != null && principal.getName() != null && !principal.getName().isBlank())
				? principal.getName() : "staff";
		var result = this.settingsService.updateSettings(form, actor);
		if (!result.applied()) {
			redirectAttributes.addFlashAttribute("error", "confirmedConflictWarning");
			redirectAttributes.addFlashAttribute("conflicts", result.conflicts());
			return "redirect:/staff/settings";
		}
		if (result.holdsInvalidated()) {
			redirectAttributes.addFlashAttribute("notice", "holdConflictNotice");
		}

		redirectAttributes.addFlashAttribute("message", "settingsSaved");
		return "redirect:/staff/settings";
	}

}
