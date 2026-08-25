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

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates a login {@link UserAccount} for every seeded {@link Owner} on startup. The
 * username is the lower-cased first name (e.g. {@code George} becomes {@code george}) and
 * the temporary password is the username with a {@code 123} suffix (e.g.
 * {@code george123}). Accounts are flagged so the owner must change the temporary
 * password on first login. Owners that already have an account, or whose derived username
 * is already taken, are skipped so the bootstrap is idempotent.
 */
@Component
public class OwnerAccountBootstrap implements ApplicationRunner {

	private final UserAccountRepository userAccountRepository;

	private final OwnerRepository ownerRepository;

	private final PasswordEncoder passwordEncoder;

	@Value("${petclinic.bootstrap.owner.enabled:true}")
	private boolean enabled;

	public OwnerAccountBootstrap(UserAccountRepository userAccountRepository, OwnerRepository ownerRepository,
			PasswordEncoder passwordEncoder) {
		this.userAccountRepository = userAccountRepository;
		this.ownerRepository = ownerRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!this.enabled) {
			return;
		}

		for (Owner owner : this.ownerRepository.findAll()) {
			if (this.userAccountRepository.findByOwnerId(owner.getId()).isPresent()) {
				continue;
			}

			String firstName = owner.getFirstName();
			if (firstName == null || firstName.isBlank()) {
				continue;
			}

			String username = firstName.toLowerCase(Locale.ROOT);
			if (this.userAccountRepository.existsByUsername(username)) {
				continue;
			}

			String temporaryPassword = username + "123";
			UserAccount account = new UserAccount(username, this.passwordEncoder.encode(temporaryPassword),
					UserRole.OWNER, false, owner);
			this.userAccountRepository.save(account);
		}
	}

}
