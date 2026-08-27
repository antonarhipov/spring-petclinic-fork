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
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(10)
@ConditionalOnProperty(name = "petclinic.demo-seeding", havingValue = "true")
public class DemoAccountSeeder implements CommandLineRunner {

	private final AppUserRepository userRepository;

	private final OwnerRepository ownerRepository;

	private final PasswordEncoder passwordEncoder;

	public DemoAccountSeeder(AppUserRepository userRepository, OwnerRepository ownerRepository,
			PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.ownerRepository = ownerRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	@Transactional
	public void run(String... args) {
		seedStaffUser();
		seedOwnerUsers();
	}

	public void seedStaffUser() {
		if (this.userRepository.findByUsername("staff").isEmpty()) {
			AppUser staff = new AppUser();
			staff.setUsername("staff");
			staff.setPasswordHash(this.passwordEncoder.encode("staff123"));
			staff.setRole(UserRole.STAFF);
			staff.setEnabled(true);
			staff.setMustChangePassword(false);
			this.userRepository.save(staff);
		}
	}

	public void seedOwnerUsers() {
		List<Owner> owners = this.ownerRepository.findAll();
		for (Owner owner : owners) {
			if (owner.getFirstName() != null && !owner.getFirstName().isBlank()) {
				String username = owner.getFirstName().toLowerCase().trim();
				if (this.userRepository.findByUsername(username).isEmpty()
						&& this.userRepository.findByOwnerId(owner.getId()).isEmpty()) {
					AppUser ownerUser = new AppUser();
					ownerUser.setUsername(username);
					ownerUser.setPasswordHash(this.passwordEncoder.encode(username + "123"));
					ownerUser.setRole(UserRole.OWNER);
					ownerUser.setEnabled(true);
					ownerUser.setMustChangePassword(false);
					ownerUser.setOwner(owner);
					this.userRepository.save(ownerUser);
				}
			}
		}
	}

}
