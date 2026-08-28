package org.springframework.samples.petclinic.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
class PasswordController {

	private final AccountService accounts;

	PasswordController(AccountService accounts) {
		this.accounts = accounts;
	}

	@GetMapping("/login")
	String login() {
		return "auth/login";
	}

	@GetMapping("/password/change")
	String form() {
		return "auth/changePassword";
	}

	@PostMapping("/password/change")
	String change(@RequestParam String password, @RequestParam String confirmation, Authentication authentication,
			RedirectAttributes attributes, HttpServletRequest request) {
		if (!password.equals(confirmation)) {
			attributes.addFlashAttribute("error", "Passwords do not match");
			return "redirect:/password/change";
		}
		try {
			this.accounts.changePassword(authentication.getName(), password);
		}
		catch (IllegalArgumentException ex) {
			attributes.addFlashAttribute("error", ex.getMessage());
			return "redirect:/password/change";
		}
		request.changeSessionId();
		return "redirect:/my/appointments";
	}

}
