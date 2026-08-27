package org.springframework.samples.petclinic.scheduling.controller;

import org.springframework.samples.petclinic.scheduling.model.ClinicSettings;
import org.springframework.samples.petclinic.scheduling.service.ClinicSettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UrgentCareController {

	private final ClinicSettingsService clinicSettingsService;

	public UrgentCareController(ClinicSettingsService clinicSettingsService) {
		this.clinicSettingsService = clinicSettingsService;
	}

	@GetMapping("/guidance/urgent-care")
	public String showUrgentCareGuidance(Model model) {
		ClinicSettings settings = clinicSettingsService.getSettings();
		model.addAttribute("urgentCareGuidance", settings.getUrgentCareGuidance());
		model.addAttribute("emergencyPhone", settings.getEmergencyPhone());
		return "guidance/urgentCare";
	}

}
