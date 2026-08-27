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
package org.springframework.samples.petclinic.calendar;

import java.time.LocalTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@PreAuthorize("hasRole('STAFF')")
public class ClinicSettingsController {

	private final ClinicSettingsRepository settingsRepository;

	public ClinicSettingsController(ClinicSettingsRepository settingsRepository) {
		this.settingsRepository = settingsRepository;
	}

	@GetMapping("/staff/clinic-settings")
	public String showSettings(Model model) {
		model.addAttribute("settings", this.settingsRepository.getClinicSettings());
		return "calendar/settings";
	}

	@PostMapping("/staff/clinic-settings")
	public String saveSettings(@RequestParam Integer gridGranularityMin, @RequestParam Integer holdDurationMin,
			@RequestParam Integer bookingHorizonDays, @RequestParam Integer minVisitMin,
			@RequestParam Integer maxVisitMin, @RequestParam Integer defaultVisitMin, @RequestParam String zoneId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime morningStart,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime morningEnd,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime afternoonStart,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime afternoonEnd,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime eveningStart,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime eveningEnd,
			RedirectAttributes redirectAttributes) {
		ClinicSettings settings = this.settingsRepository.getClinicSettings();
		settings.setGridGranularityMin(gridGranularityMin);
		settings.setHoldDurationMin(holdDurationMin);
		settings.setBookingHorizonDays(bookingHorizonDays);
		settings.setMinVisitMin(minVisitMin);
		settings.setMaxVisitMin(maxVisitMin);
		settings.setDefaultVisitMin(defaultVisitMin);
		settings.setZoneId(zoneId);
		settings.setMorningStart(morningStart);
		settings.setMorningEnd(morningEnd);
		settings.setAfternoonStart(afternoonStart);
		settings.setAfternoonEnd(afternoonEnd);
		settings.setEveningStart(eveningStart);
		settings.setEveningEnd(eveningEnd);
		this.settingsRepository.save(settings);
		redirectAttributes.addFlashAttribute("message", "Settings saved");
		return "redirect:/staff/clinic-settings";
	}

}
