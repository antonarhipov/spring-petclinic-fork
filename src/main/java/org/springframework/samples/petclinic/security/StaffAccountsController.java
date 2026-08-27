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

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Staff-only accounts overview. Lists owners and whether each already has an application
 * login, linking to the owner detail screen where the existing provisioning and
 * password-reset actions live. No account behavior is changed here.
 */
@Controller
@PreAuthorize("hasRole('STAFF')")
public class StaffAccountsController {

	private final OwnerRepository ownerRepository;

	private final AppUserRepository userRepository;

	public StaffAccountsController(OwnerRepository ownerRepository, AppUserRepository userRepository) {
		this.ownerRepository = ownerRepository;
		this.userRepository = userRepository;
	}

	@GetMapping("/staff/accounts")
	public String accounts(Model model) {
		Set<Integer> ownersWithLogin = this.userRepository.findAll()
			.stream()
			.map(user -> user.getOwner() != null ? user.getOwner().getId() : null)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());
		model.addAttribute("owners", this.ownerRepository.findAll());
		model.addAttribute("ownersWithLogin", ownersWithLogin);
		return "owners/accounts";
	}

}
