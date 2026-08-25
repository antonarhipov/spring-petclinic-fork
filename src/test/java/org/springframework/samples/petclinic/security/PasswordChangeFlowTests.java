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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PasswordChangeFlowTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private UserAccount flaggedUser;

	@BeforeEach
	void setUp() {
		this.userAccountRepository.deleteAll();
		this.flaggedUser = new UserAccount("temp_staff", this.passwordEncoder.encode("temp123"), UserRole.STAFF, true,
				null);
		this.flaggedUser = this.userAccountRepository.saveAndFlush(this.flaggedUser);
	}

	@Test
	void flaggedUserIsRedirectedUntilPasswordChangedThenAllowed() throws Exception {
		// 1. Trying to access any normal route redirects to /change-password
		this.mockMvc.perform(get("/owners/find").with(user("temp_staff").roles("STAFF")))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/change-password"));

		// 2. Accessing /change-password directly is allowed
		this.mockMvc.perform(get("/change-password").with(user("temp_staff").roles("STAFF")))
			.andExpect(status().isOk());

		// 3. Submitting new password
		this.mockMvc
			.perform(post("/change-password").with(user("temp_staff").roles("STAFF"))
				.with(csrf())
				.param("newPassword", "newSecretPassword456!")
				.param("confirmPassword", "newSecretPassword456!"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/"));

		// 4. Verify flag is cleared in repository
		UserAccount updated = this.userAccountRepository.findByUsername("temp_staff").orElseThrow();
		assertThat(updated.isMustChangePassword()).isFalse();
		assertThat(this.passwordEncoder.matches("newSecretPassword456!", updated.getPassword())).isTrue();

		// 5. Now accessing normal route is allowed
		this.mockMvc.perform(get("/owners/find").with(user("temp_staff").roles("STAFF"))).andExpect(status().isOk());
	}

}
