package org.springframework.samples.petclinic.scheduling.web.staff;

import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.account.AccountRepository;
import org.springframework.samples.petclinic.scheduling.availability.ClinicPolicyService;
import org.springframework.samples.petclinic.scheduling.availability.ClinicSchedulingPolicy;
import org.springframework.samples.petclinic.system.StaleStateException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class StaffSettingsController {

	private final ClinicPolicyService policies;

	private final AccountRepository accounts;

	public StaffSettingsController(ClinicPolicyService policies, AccountRepository accounts) {
		this.policies = policies;
		this.accounts = accounts;
	}

	@GetMapping("/staff/settings")
	public String settings(Model model) {
		ClinicSchedulingPolicy policy = this.policies.current();
		ClinicPolicyForm form = new ClinicPolicyForm();
		form.setExpectedVersion(policy.getVersion());
		form.setBookingHorizonDays(policy.getBookingHorizonDays());
		form.setHoldDurationMinutes(policy.getHoldDurationMinutes());
		form.setUrgentCareGuidance(policy.getUrgentCareGuidance());
		form.setZoneId(policy.getZoneId());
		model.addAttribute("form", form);
		model.addAttribute("policy", policy);
		return "scheduling/staff/settings";
	}

	@PostMapping("/staff/settings")
	public String update(@ModelAttribute ClinicPolicyForm form, Authentication authentication) {
		ClinicSchedulingPolicy policy = this.policies.current();
		if (form.getExpectedVersion() != null && !form.getExpectedVersion().equals(policy.getVersion())) {
			throw new StaleStateException("stale", policy, form);
		}
		this.policies.changeZone(form.getZoneId() == null ? policy.getZoneId() : form.getZoneId(),
				staff(authentication).getId());
		this.policies.updateBounds(form.getBookingHorizonDays(), form.getHoldDurationMinutes(),
				staff(authentication).getId());
		if (form.getUrgentCareGuidance() != null) {
			this.policies.updateUrgentGuidance(form.getUrgentCareGuidance(), staff(authentication).getId());
		}
		return "redirect:/staff/settings";
	}

	@PostMapping("/staff/settings/emergency-terms")
	public String addTerm(@RequestParam String term, Authentication authentication) {
		this.policies.addEmergencyTerm(term, staff(authentication).getId());
		return "redirect:/staff/settings";
	}

	private Account staff(Authentication authentication) {
		return this.accounts.findByUsername(authentication.getName()).orElseThrow();
	}

}
