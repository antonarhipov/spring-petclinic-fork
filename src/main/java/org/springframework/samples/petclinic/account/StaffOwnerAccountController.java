package org.springframework.samples.petclinic.account;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.samples.petclinic.security.PetClinicPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/staff/owners/{ownerId}/account")
public class StaffOwnerAccountController {

	private final AccountRepository accountRepository;

	private final AccountProvisioningService accountProvisioningService;

	private final PasswordResetService passwordResetService;

	private final UsernamePolicy usernamePolicy;

	public StaffOwnerAccountController(AccountRepository accountRepository,
			AccountProvisioningService accountProvisioningService, PasswordResetService passwordResetService,
			UsernamePolicy usernamePolicy) {
		this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository must not be null");
		this.accountProvisioningService = Objects.requireNonNull(accountProvisioningService,
				"accountProvisioningService must not be null");
		this.passwordResetService = Objects.requireNonNull(passwordResetService,
				"passwordResetService must not be null");
		this.usernamePolicy = Objects.requireNonNull(usernamePolicy, "usernamePolicy must not be null");
	}

	@GetMapping
	public String showAccountForm(@PathVariable("ownerId") Integer ownerId, Model model, HttpServletResponse response) {
		response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
		response.setHeader("Pragma", "no-cache");

		Optional<Account> account = this.accountRepository.findByOwnerId(ownerId);
		model.addAttribute("ownerId", ownerId);
		model.addAttribute("account", account.orElse(null));
		model.addAttribute("commandId", UUID.randomUUID());
		if (account.isEmpty()) {
			model.addAttribute("suggestedUsername", "owner" + ownerId);
		}

		return "staff/owners/account-form";
	}

	@PostMapping("/provision")
	public String provisionAccount(@PathVariable("ownerId") Integer ownerId, @RequestParam("username") String username,
			@RequestParam(value = "commandId", required = false) UUID commandId,
			@AuthenticationPrincipal PetClinicPrincipal principal, RedirectAttributes redirectAttributes,
			HttpServletResponse response) {
		response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
		response.setHeader("Pragma", "no-cache");

		try {
			AccountProvisioningService.ProvisionAccountResult result = this.accountProvisioningService
				.provisionOwnerAccount(ownerId, username, principal != null ? principal.getId() : null, commandId);
			redirectAttributes.addFlashAttribute("provisionResult", result);
			redirectAttributes.addFlashAttribute("message", "Owner account successfully provisioned.");
			return "redirect:/staff/owners/" + ownerId + "/account/result";
		}
		catch (Exception e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
			return "redirect:/staff/owners/" + ownerId + "/account";
		}
	}

	@PostMapping("/reset")
	public String resetPassword(@PathVariable("ownerId") Integer ownerId,
			@RequestParam(value = "commandId", required = false) UUID commandId,
			@AuthenticationPrincipal PetClinicPrincipal principal, RedirectAttributes redirectAttributes,
			HttpServletResponse response) {
		response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
		response.setHeader("Pragma", "no-cache");

		try {
			Account account = this.accountRepository.findByOwnerId(ownerId)
				.orElseThrow(() -> new IllegalArgumentException("Account not found for owner ID: " + ownerId));

			PasswordResetService.PasswordResetResult result = this.passwordResetService.resetPassword(account.getId(),
					principal != null ? principal.getId() : null, commandId);
			redirectAttributes.addFlashAttribute("resetResult", result);
			redirectAttributes.addFlashAttribute("message",
					"Temporary password generated and existing sessions invalidated.");
			return "redirect:/staff/owners/" + ownerId + "/account/result";
		}
		catch (Exception e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
			return "redirect:/staff/owners/" + ownerId + "/account";
		}
	}

	@GetMapping("/result")
	public String showResult(@PathVariable("ownerId") Integer ownerId, Model model, HttpServletResponse response) {
		response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
		response.setHeader("Pragma", "no-cache");

		model.addAttribute("ownerId", ownerId);
		return "staff/owners/account-result";
	}

}
