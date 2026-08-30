package org.springframework.samples.petclinic.account;

import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class AccountWebController {

	private final AccountProvisioningService provisioning;

	private final PasswordService passwords;

	private final AccountRepository accounts;

	private final OwnerRepository owners;

	private final UsernameSuggester suggester;

	public AccountWebController(AccountProvisioningService provisioning, PasswordService passwords,
			AccountRepository accounts, OwnerRepository owners, UsernameSuggester suggester) {
		this.provisioning = provisioning;
		this.passwords = passwords;
		this.accounts = accounts;
		this.owners = owners;
		this.suggester = suggester;
	}

	@GetMapping("/account/password/change")
	public String changeForm(Model model) {
		if (!model.containsAttribute("passwordChangeForm")) {
			model.addAttribute("passwordChangeForm", new PasswordChangeForm());
		}
		return "account/change-password";
	}

	@PostMapping("/account/password/change")
	public String change(@ModelAttribute("passwordChangeForm") PasswordChangeForm form, BindingResult result,
			Authentication authentication, HttpServletRequest request) {
		try {
			this.passwords.changePassword(authentication.getName(), form.getCurrentPassword(), form.getNewPassword(),
					form.getConfirmPassword(), request);
			return "redirect:/";
		}
		catch (PasswordChangeException ex) {
			result.reject(ex.getMessage());
			form.clearSecrets();
			return "account/change-password";
		}
	}

	@GetMapping("/staff/owners/{ownerId}/account")
	public String provisionForm(@PathVariable Integer ownerId, Model model) {
		Owner owner = this.owners.findById(ownerId).orElseThrow();
		Account existing = this.accounts.findByOwnerId(ownerId).orElse(null);
		AccountProvisioningForm form = new AccountProvisioningForm();
		if (existing == null) {
			form.setUsername(this.suggester.suggest(owner.getFirstName(), this.accounts::existsByUsername));
			form.setExpectedVersion(0);
		}
		else {
			form.setUsername(existing.getUsername());
			form.setExpectedVersion(existing.getVersion() == null ? 0 : existing.getVersion());
		}
		model.addAttribute("owner", owner);
		model.addAttribute("account", existing);
		model.addAttribute("accountProvisioningForm", form);
		return "account/provision-owner";
	}

	@PostMapping("/staff/owners/{ownerId}/account")
	public String provision(@PathVariable Integer ownerId,
			@ModelAttribute("accountProvisioningForm") AccountProvisioningForm form, BindingResult result,
			Authentication authentication, RedirectAttributes redirectAttributes, Model model) {
		try {
			OneTimeCredentialResult credential = this.provisioning.provision(ownerId, form.getUsername(),
					form.getExpectedVersion(), staff(authentication).getId());
			flash(redirectAttributes, credential);
			return "redirect:/staff/owners/" + ownerId + "/account/one-time";
		}
		catch (DuplicateUsernameException ex) {
			result.rejectValue("username", "duplicate");
			model.addAttribute("owner", this.owners.findById(ownerId).orElseThrow());
			model.addAttribute("accountProvisioningForm", form);
			return "account/provision-owner";
		}
	}

	@PostMapping("/staff/owners/{ownerId}/account/reset")
	public String reset(@PathVariable Integer ownerId, @RequestParam Integer expectedVersion,
			@RequestParam(defaultValue = "false") boolean confirm, Authentication authentication,
			RedirectAttributes redirectAttributes) {
		OneTimeCredentialResult credential = this.provisioning.reset(ownerId, expectedVersion, confirm,
				staff(authentication).getId());
		flash(redirectAttributes, credential);
		return "redirect:/staff/owners/" + ownerId + "/account/one-time";
	}

	@GetMapping("/staff/owners/{ownerId}/account/one-time")
	public String oneTime(@PathVariable Integer ownerId, Model model, HttpServletResponse response) {
		response.setHeader("Cache-Control", "no-store");
		model.addAttribute("ownerId", ownerId);
		if (!model.containsAttribute("oneTimePassword")) {
			return "redirect:/staff/owners/" + ownerId + "/account";
		}
		return "account/one-time-credential";
	}

	private static void flash(RedirectAttributes redirectAttributes, OneTimeCredentialResult credential) {
		redirectAttributes.addFlashAttribute("oneTimePassword", credential.oneTimePassword());
		redirectAttributes.addFlashAttribute("username", credential.username());
		redirectAttributes.addFlashAttribute("expiresAt", credential.expiresAt());
	}

	private Account staff(Authentication authentication) {
		return this.accounts.findByUsername(authentication.getName()).orElseThrow();
	}

}
