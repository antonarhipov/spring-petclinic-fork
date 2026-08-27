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

import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@PreAuthorize("hasRole('STAFF')")
public class StaffSecurityController {

	private final AppUserRepository userRepository;

	private final OwnerRepository ownerRepository;

	private final PasswordEncoder passwordEncoder;

	public StaffSecurityController(AppUserRepository userRepository, OwnerRepository ownerRepository,
			PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.ownerRepository = ownerRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@PostMapping({ "/staff/owners/{ownerId}/reset-password", "/owners/{ownerId}/reset-password" })
	public String resetOwnerPassword(@PathVariable("ownerId") int ownerId,
			@RequestParam(value = "temporaryPassword", required = false) String temporaryPassword,
			RedirectAttributes redirectAttributes) {

		Owner owner = this.ownerRepository.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found with id: " + ownerId));

		AppUser appUser = this.userRepository.findByOwnerId(ownerId).orElseGet(() -> {
			AppUser newUser = new AppUser();
			String username = owner.getFirstName() != null ? owner.getFirstName().toLowerCase().trim()
					: "owner" + ownerId;
			newUser.setUsername(username);
			newUser.setRole(UserRole.OWNER);
			newUser.setEnabled(true);
			newUser.setOwner(owner);
			return newUser;
		});

		String password = StringUtils.hasText(temporaryPassword) ? temporaryPassword : appUser.getUsername() + "123";

		appUser.setPasswordHash(this.passwordEncoder.encode(password));
		appUser.setMustChangePassword(true);
		this.userRepository.save(appUser);

		redirectAttributes.addFlashAttribute("message",
				"Password reset successfully for " + appUser.getUsername() + ". Forced password change is armed.");
		return "redirect:/owners/" + ownerId;
	}

	@PostMapping("/staff/owners/{ownerId}/provision")
	public String provisionOwnerAccount(@PathVariable("ownerId") int ownerId,
			@RequestParam(value = "username", required = false) String requestedUsername,
			@RequestParam(value = "initialPassword", required = false) String initialPassword,
			RedirectAttributes redirectAttributes) {

		Owner owner = this.ownerRepository.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found with id: " + ownerId));

		if (this.userRepository.findByOwnerId(ownerId).isPresent()) {
			redirectAttributes.addFlashAttribute("error", "Owner already has an application account.");
			return "redirect:/owners/" + ownerId;
		}

		String username = StringUtils.hasText(requestedUsername) ? requestedUsername.trim()
				: (owner.getFirstName() != null ? owner.getFirstName().toLowerCase().trim() : "owner" + ownerId);

		String password = StringUtils.hasText(initialPassword) ? initialPassword : username + "123";

		AppUser newUser = new AppUser();
		newUser.setUsername(username);
		newUser.setPasswordHash(this.passwordEncoder.encode(password));
		newUser.setRole(UserRole.OWNER);
		newUser.setEnabled(true);
		newUser.setMustChangePassword(true);
		newUser.setOwner(owner);
		this.userRepository.save(newUser);

		redirectAttributes.addFlashAttribute("message",
				"Account provisioned for " + username + " with forced password change on first login.");
		return "redirect:/owners/" + ownerId;
	}

}
