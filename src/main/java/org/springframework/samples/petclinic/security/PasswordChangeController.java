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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PasswordChangeController {

	private final UserAccountRepository userAccountRepository;

	private final PasswordEncoder passwordEncoder;

	public PasswordChangeController(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
		this.userAccountRepository = userAccountRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@GetMapping("/change-password")
	public String showChangePasswordForm(Authentication authentication, Model model) {
		if (authentication == null || !authentication.isAuthenticated()) {
			return "redirect:/login";
		}
		return "security/changePassword";
	}

	@PostMapping("/change-password")
	public String processChangePassword(@RequestParam("newPassword") String newPassword,
			@RequestParam("confirmPassword") String confirmPassword, Authentication authentication, Model model) {
		if (authentication == null || !authentication.isAuthenticated()) {
			return "redirect:/login";
		}

		if (newPassword == null || newPassword.trim().isEmpty()) {
			model.addAttribute("errorMessage", "Password cannot be empty.");
			return "security/changePassword";
		}

		if (!newPassword.equals(confirmPassword)) {
			model.addAttribute("errorMessage", "Passwords do not match.");
			return "security/changePassword";
		}

		String username = authentication.getName();
		UserAccount account = this.userAccountRepository.findByUsername(username).orElse(null);
		if (account != null) {
			account.setPassword(this.passwordEncoder.encode(newPassword));
			account.setMustChangePassword(false);
			this.userAccountRepository.save(account);
		}

		return "redirect:/";
	}

}
