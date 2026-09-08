package org.springframework.samples.petclinic.scheduling.config;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class ClinicConfigurationController {

	private final ClinicConfigurationService configurations;

	public ClinicConfigurationController(ClinicConfigurationService configurations) {
		this.configurations = configurations;
	}

	@GetMapping("/staff/settings")
	String settings(Model model) {
		model.addAttribute("configuration", ClinicConfigurationForm.from(this.configurations.current()));
		populate(model);
		return "staff/settings";
	}

	@PostMapping("/staff/settings")
	String save(@ModelAttribute("configuration") ClinicConfigurationForm form, BindingResult bindingResult,
			Model model) {
		populate(model);
		if (bindingResult.hasErrors()) {
			model.addAttribute("actionErrorKey", "scheduling.settings.validation.required");
			return "staff/settings";
		}
		try {
			int gridMinutes = this.configurations.current().gridMinutes();
			ClinicConfigurationService.ChangeResult result = this.configurations
				.change(form.toConfiguration(gridMinutes));
			if (!result.saved()) {
				model.addAttribute("confirmedConflicts", result.confirmedConflicts());
				model.addAttribute("actionErrorKey", "scheduling.settings.confirmedConflicts");
				return "staff/settings";
			}
			model.addAttribute("configuration", ClinicConfigurationForm.from(this.configurations.current()));
			model.addAttribute("affectedRequests", result.affectedRequests());
			model.addAttribute("actionNoticeKey", result.affectedRequests().isEmpty()
					? "scheduling.settings.saved.noneAffected" : "scheduling.settings.saved.affected");
			return "staff/settings";
		}
		catch (ConfigurationValidationException ex) {
			model.addAttribute("actionErrorKey", ex.getMessageKey());
			return "staff/settings";
		}
	}

	private void populate(Model model) {
		model.addAttribute("veterinarians", this.configurations.veterinarians());
	}

}
