package org.springframework.samples.petclinic.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PasswordChangeController {

	private final AccountService accountService;

	public PasswordChangeController(AccountService accountService) {
		this.accountService = accountService;
	}

	@GetMapping("/change-password")
	public String showChangePasswordForm() {
		return "security/changePassword";
	}

	@PostMapping("/change-password")
	public String processChangePassword(@RequestParam("oldPassword") String oldPassword,
			@RequestParam("newPassword") String newPassword, @RequestParam("confirmPassword") String confirmPassword,
			Authentication authentication, Model model) {

		if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
			return "redirect:/login";
		}

		if (newPassword == null || newPassword.length() < 8) {
			model.addAttribute("error", "Password must be at least 8 characters long");
			return "security/changePassword";
		}

		if (!newPassword.equals(confirmPassword)) {
			model.addAttribute("error", "New password and confirmation do not match");
			return "security/changePassword";
		}

		try {
			accountService.changePassword(principal.getId(), oldPassword, newPassword, principal.getUsername(),
					principal.getRole().name());
			return "redirect:/?passwordChanged=true";
		}
		catch (Exception e) {
			model.addAttribute("error", e.getMessage());
			return "security/changePassword";
		}
	}

}
