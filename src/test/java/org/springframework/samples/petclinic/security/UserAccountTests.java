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

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class UserAccountTests {

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	@Test
	void passwordIsHashedWithBcryptAndNoPlaintextIsStored() {
		String rawPassword = "mySecretPassword123!";
		String hashedPassword = this.passwordEncoder.encode(rawPassword);

		UserAccount user = new UserAccount("john_doe", hashedPassword, UserRole.OWNER, true, null);

		assertThat(user.getPassword()).isNotEqualTo(rawPassword);
		assertThat(user.getPassword()).startsWith("$2a$").hasSizeGreaterThan(50);
		assertThat(this.passwordEncoder.matches(rawPassword, user.getPassword())).isTrue();
		assertThat(this.passwordEncoder.matches("wrongPassword", user.getPassword())).isFalse();
	}

	@Test
	void userAccountPropertiesAndDefaults() {
		UserAccount user = new UserAccount();
		user.setUsername("staff1");
		user.setPassword("hash");
		user.setRole(UserRole.STAFF);
		user.setMustChangePassword(true);

		assertThat(user.getUsername()).isEqualTo("staff1");
		assertThat(user.getPassword()).isEqualTo("hash");
		assertThat(user.getRole()).isEqualTo(UserRole.STAFF);
		assertThat(user.isMustChangePassword()).isTrue();
		assertThat(user.getOwner()).isNull();
	}

}
