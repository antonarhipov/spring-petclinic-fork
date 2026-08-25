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

import java.util.List;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/staff/accounts")
@PreAuthorize("hasRole('STAFF')")
public class AccountAdminController {

	private final UserAccountRepository userAccountRepository;

	private final OwnerRepository ownerRepository;

	private final PasswordEncoder passwordEncoder;

	public AccountAdminController(UserAccountRepository userAccountRepository, OwnerRepository ownerRepository,
			PasswordEncoder passwordEncoder) {
		this.userAccountRepository = userAccountRepository;
		this.ownerRepository = ownerRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@GetMapping
	public String listAccounts(Model model) {
		List<UserAccount> accounts = this.userAccountRepository.findAll();
		model.addAttribute("accounts", accounts);
		return "security/accountList";
	}

	@GetMapping("/owner/new")
	public String initCreateOwnerAccountForm(@RequestParam(value = "ownerId", required = false) Integer ownerId,
			Model model) {
		model.addAttribute("owners", this.ownerRepository.findAll());
		model.addAttribute("selectedOwnerId", ownerId);
		return "security/createOwnerAccount";
	}

	@PostMapping("/owner/new")
	public String processCreateOwnerAccountForm(@RequestParam("username") String username,
			@RequestParam("temporaryPassword") String temporaryPassword, @RequestParam("ownerId") int ownerId,
			Model model) {
		if (this.userAccountRepository.existsByUsername(username)) {
			model.addAttribute("errorMessage", "Username already exists.");
			model.addAttribute("owners", this.ownerRepository.findAll());
			model.addAttribute("selectedOwnerId", ownerId);
			return "security/createOwnerAccount";
		}

		Owner owner = this.ownerRepository.findById(ownerId).orElse(null);
		if (owner == null) {
			model.addAttribute("errorMessage", "Owner not found.");
			model.addAttribute("owners", this.ownerRepository.findAll());
			return "security/createOwnerAccount";
		}

		if (this.userAccountRepository.findByOwnerId(ownerId).isPresent()) {
			model.addAttribute("errorMessage", "Account already exists for this owner.");
			model.addAttribute("owners", this.ownerRepository.findAll());
			return "security/createOwnerAccount";
		}

		UserAccount account = new UserAccount(username, this.passwordEncoder.encode(temporaryPassword), UserRole.OWNER,
				true, owner);
		this.userAccountRepository.save(account);

		return "redirect:/staff/accounts";
	}

	@GetMapping("/staff/new")
	public String initCreateStaffAccountForm() {
		return "security/createStaffAccount";
	}

	@PostMapping("/staff/new")
	public String processCreateStaffAccountForm(@RequestParam("username") String username,
			@RequestParam("temporaryPassword") String temporaryPassword, Model model) {
		if (this.userAccountRepository.existsByUsername(username)) {
			model.addAttribute("errorMessage", "Username already exists.");
			return "security/createStaffAccount";
		}

		UserAccount account = new UserAccount(username, this.passwordEncoder.encode(temporaryPassword), UserRole.STAFF,
				true, null);
		this.userAccountRepository.save(account);

		return "redirect:/staff/accounts";
	}

	@GetMapping("/{id}/reset-password")
	public String initResetPasswordForm(@PathVariable("id") int id, Model model) {
		UserAccount account = this.userAccountRepository.findById(id).orElse(null);
		if (account == null) {
			return "redirect:/staff/accounts";
		}
		model.addAttribute("account", account);
		return "security/resetPassword";
	}

	@PostMapping("/{id}/reset-password")
	public String processResetPasswordForm(@PathVariable("id") int id,
			@RequestParam("temporaryPassword") String temporaryPassword) {
		UserAccount account = this.userAccountRepository.findById(id).orElse(null);
		if (account != null) {
			account.setPassword(this.passwordEncoder.encode(temporaryPassword));
			account.setMustChangePassword(true);
			this.userAccountRepository.save(account);
		}
		return "redirect:/staff/accounts";
	}

}
