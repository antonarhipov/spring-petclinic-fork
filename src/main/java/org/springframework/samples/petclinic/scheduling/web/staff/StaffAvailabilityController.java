package org.springframework.samples.petclinic.scheduling.web.staff;

import java.time.LocalDate;
import java.util.List;

import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.account.AccountRepository;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityCommandService;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityConflictException;
import org.springframework.samples.petclinic.scheduling.availability.ClinicPolicyService;
import org.springframework.samples.petclinic.system.StaleStateException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class StaffAvailabilityController {

	private final AvailabilityCommandService commands;

	private final ClinicPolicyService policies;

	private final AccountRepository accounts;

	public StaffAvailabilityController(AvailabilityCommandService commands, ClinicPolicyService policies,
			AccountRepository accounts) {
		this.commands = commands;
		this.policies = policies;
		this.accounts = accounts;
	}

	@GetMapping("/staff/availability")
	public String availability(Model model) {
		model.addAttribute("form", new AvailabilityRuleForm());
		model.addAttribute("closures", this.commands.closures());
		model.addAttribute("policy", this.policies.current());
		return "scheduling/staff/availability";
	}

	@GetMapping("/staff/availability/rules/new")
	public String ruleForm(Model model) {
		model.addAttribute("form", new AvailabilityRuleForm());
		return "scheduling/staff/availability-rule-form";
	}

	@PostMapping("/staff/availability/shifts")
	public String addShift(@ModelAttribute AvailabilityRuleForm form, Authentication authentication) {
		assertVersion(form.getExpectedVersion());
		this.commands.addShift(form.getVeterinarianId(), form.getDayOfWeek(), form.getStartLocalTime(),
				form.getEndLocalTime(), staff(authentication).getId());
		return "redirect:/staff/availability";
	}

	@PostMapping("/staff/availability/leave")
	public String addLeave(@ModelAttribute AvailabilityRuleForm form, Authentication authentication, Model model) {
		assertVersion(form.getExpectedVersion());
		try {
			this.commands.addLeave(form.getVeterinarianId(), form.getStartLocalDate(), form.getEndLocalDate(),
					staff(authentication).getId());
		}
		catch (AvailabilityConflictException ex) {
			model.addAttribute("affected", ex.getAffectedReservations());
			model.addAttribute("form", form);
			return "scheduling/staff/capacity-conflict";
		}
		return "redirect:/staff/availability";
	}

	@PostMapping("/staff/availability/closures")
	public String addClosure(@ModelAttribute AvailabilityRuleForm form, Authentication authentication, Model model) {
		assertVersion(form.getExpectedVersion());
		try {
			this.commands.addClosure(form.getStartLocalDate(), form.getEndLocalDate(), staff(authentication).getId());
		}
		catch (AvailabilityConflictException ex) {
			model.addAttribute("affected", ex.getAffectedReservations());
			model.addAttribute("form", form);
			return "scheduling/staff/capacity-conflict";
		}
		return "redirect:/staff/availability";
	}

	@PostMapping("/staff/availability/exceptions")
	public String addException(@ModelAttribute AvailabilityRuleForm form, Authentication authentication, Model model) {
		assertVersion(form.getExpectedVersion());
		try {
			this.commands.addDateException(form.getVeterinarianId(), form.getStartLocalDate(),
					form.getStartLocalTime() == null ? List.of() : List
						.of(new AvailabilityCommandService.Interval(form.getStartLocalTime(), form.getEndLocalTime())),
					staff(authentication).getId(), form.isReleaseHolds());
		}
		catch (AvailabilityConflictException ex) {
			model.addAttribute("affected", ex.getAffectedReservations());
			model.addAttribute("form", form);
			return "scheduling/staff/capacity-conflict";
		}
		return "redirect:/staff/availability";
	}

	@ExceptionHandler(AvailabilityConflictException.class)
	public String conflict(AvailabilityConflictException ex, Model model) {
		model.addAttribute("affected", ex.getAffectedReservations());
		model.addAttribute("form", new AvailabilityRuleForm());
		return "scheduling/staff/capacity-conflict";
	}

	private void assertVersion(Integer expectedVersion) {
		if (expectedVersion != null && !expectedVersion.equals(this.policies.current().getVersion())) {
			throw new StaleStateException("stale", this.policies.current(), expectedVersion);
		}
	}

	private Account staff(Authentication authentication) {
		return this.accounts.findByUsername(authentication.getName()).orElseThrow();
	}

	@SuppressWarnings("unused")
	private LocalDate todayHint() {
		return LocalDate.now();
	}

}
