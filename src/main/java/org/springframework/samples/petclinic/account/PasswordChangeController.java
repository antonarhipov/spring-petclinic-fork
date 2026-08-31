package org.springframework.samples.petclinic.account;

import java.util.Objects;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.samples.petclinic.security.PetClinicPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PasswordChangeController {

	private final PasswordChangeService passwordChangeService;

	public PasswordChangeController(PasswordChangeService passwordChangeService) {
		this.passwordChangeService = Objects.requireNonNull(passwordChangeService,
				"passwordChangeService must not be null");
	}

	@GetMapping({ "/auth/password-change", "/account/password-change" })
	public String showPasswordChangeForm(@AuthenticationPrincipal PetClinicPrincipal principal, Model model,
			HttpServletResponse response) {
		response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
		response.setHeader("Pragma", "no-cache");

		if (principal != null) {
			model.addAttribute("username", principal.getUsername());
			model.addAttribute("passwordChangeRequired", principal.isPasswordChangeRequired());
		}

		return "auth/password-change";
	}

	@PostMapping({ "/auth/password-change", "/account/password-change" })
	public String processPasswordChange(@RequestParam("currentPassword") String currentPassword,
			@RequestParam("newPassword") String newPassword, @RequestParam("confirmPassword") String confirmPassword,
			@AuthenticationPrincipal PetClinicPrincipal principal, HttpServletRequest request,
			HttpServletResponse response, Model model, RedirectAttributes redirectAttributes) {
		response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
		response.setHeader("Pragma", "no-cache");

		if (principal == null) {
			return "redirect:/auth/login";
		}

		try {
			this.passwordChangeService.changePassword(principal.getId(), currentPassword, newPassword, confirmPassword,
					request);
			redirectAttributes.addFlashAttribute("message", "Password changed successfully.");

			if (principal.getRole() == Role.OWNER) {
				return "redirect:/owner/dashboard";
			}
			else if (principal.getRole() == Role.STAFF) {
				return "redirect:/staff/calendar/week";
			}
			return "redirect:/";
		}
		catch (Exception e) {
			model.addAttribute("error", e.getMessage());
			model.addAttribute("username", principal.getUsername());
			model.addAttribute("passwordChangeRequired", principal.isPasswordChangeRequired());
			return "auth/password-change";
		}
	}

}
