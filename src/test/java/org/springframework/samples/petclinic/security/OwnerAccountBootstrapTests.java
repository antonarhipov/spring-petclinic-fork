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

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OwnerAccountBootstrapTests {

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void ownerAccountExistsAfterStartupWithDerivedCredentials() {
		Optional<UserAccount> account = this.userAccountRepository.findByUsername("george");

		assertThat(account).isPresent();
		assertThat(account.get().getRole()).isEqualTo(UserRole.OWNER);
		assertThat(account.get().isMustChangePassword()).isTrue();
		assertThat(account.get().getOwner()).isNotNull();
		assertThat(account.get().getOwner().getFirstName()).isEqualTo("George");
		assertThat(this.passwordEncoder.matches("george123", account.get().getPassword())).isTrue();
	}

	@Test
	void everySeededOwnerHasAnAccount() {
		for (String firstName : new String[] { "george", "betty", "eduardo", "harold", "peter", "jean", "jeff", "maria",
				"david", "carlos" }) {
			Optional<UserAccount> account = this.userAccountRepository.findByUsername(firstName);
			assertThat(account).as("account for owner '%s'", firstName).isPresent();
			assertThat(this.passwordEncoder.matches(firstName + "123", account.get().getPassword())).isTrue();
		}
	}

}
