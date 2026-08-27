package org.springframework.samples.petclinic.scheduling.controller;

import java.security.Principal;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.samples.petclinic.scheduling.dto.ClinicSettingsForm;
import org.springframework.samples.petclinic.scheduling.service.ClinicSettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ClinicSettingsController {

	private final ClinicSettingsService clinicSettingsService;

	public ClinicSettingsController(ClinicSettingsService clinicSettingsService) {
		this.clinicSettingsService = clinicSettingsService;
	}

	@GetMapping("/scheduling/admin/settings")
	public String showSettingsForm(Model model) {
		if (!model.containsAttribute("settingsForm")) {
			model.addAttribute("settingsForm", clinicSettingsService.getSettingsForm());
		}
		return "scheduling/settings";
	}

	@PostMapping("/scheduling/admin/settings")
	public String updateSettings(@ModelAttribute("settingsForm") ClinicSettingsForm form, Principal principal,
			Model model, RedirectAttributes redirectAttributes) {
		try {
			String username = principal != null ? principal.getName() : "staff";
			clinicSettingsService.updateSettings(form, username);
			redirectAttributes.addFlashAttribute("successMessage", "Settings updated successfully");
			return "redirect:/scheduling/admin/settings";
		}
		catch (OptimisticLockingFailureException ex) {
			model.addAttribute("errorMessage", ex.getMessage());
			model.addAttribute("settingsForm", clinicSettingsService.getSettingsForm());
			return "scheduling/settings";
		}
		catch (IllegalArgumentException ex) {
			model.addAttribute("errorMessage", ex.getMessage());
			return "scheduling/settings";
		}
	}

}
