/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PasswordChangeController {

	private final AppUserRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	public PasswordChangeController(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@GetMapping("/change-password")
	public String showChangePasswordForm() {
		return "security/changePassword";
	}

	@PostMapping("/change-password")
	public String processChangePassword(@RequestParam("newPassword") String newPassword,
			@RequestParam(value = "confirmPassword", required = false) String confirmPassword,
			Authentication authentication, Model model, RedirectAttributes redirectAttributes) {

		if (!StringUtils.hasText(newPassword)) {
			model.addAttribute("error", "Password cannot be empty.");
			return "security/changePassword";
		}

		if (confirmPassword != null && !newPassword.equals(confirmPassword)) {
			model.addAttribute("error", "Passwords do not match.");
			return "security/changePassword";
		}

		String username = authentication.getName();
		AppUser appUser = this.userRepository.findByUsername(username)
			.orElseThrow(() -> new IllegalStateException("User not found: " + username));

		appUser.setPasswordHash(this.passwordEncoder.encode(newPassword));
		appUser.setMustChangePassword(false);
		this.userRepository.save(appUser);

		if (authentication.getPrincipal() instanceof AppUserDetails userDetails) {
			userDetails.setMustChangePassword(false);
		}

		redirectAttributes.addFlashAttribute("message", "Password successfully changed.");
		return "redirect:/";
	}

}
