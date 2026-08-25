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
class AccountAdminControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void staffCanCreateOwnerAccountAndItRequiresPasswordChangeOnFirstLogin() throws Exception {
		this.mockMvc
			.perform(post("/staff/accounts/owner/new").with(user("admin").roles("STAFF"))
				.with(csrf())
				.param("username", "franklin_owner")
				.param("temporaryPassword", "tempPass123!")
				.param("ownerId", "1"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/accounts"));

		UserAccount created = this.userAccountRepository.findByUsername("franklin_owner").orElseThrow();
		assertThat(created.getRole()).isEqualTo(UserRole.OWNER);
		assertThat(created.isMustChangePassword()).isTrue();
		assertThat(created.getOwner()).isNotNull();
		assertThat(created.getOwner().getId()).isEqualTo(1);
		assertThat(this.passwordEncoder.matches("tempPass123!", created.getPassword())).isTrue();
	}

	@Test
	void staffCanCreateStaffAccountAndItRequiresPasswordChangeOnFirstLogin() throws Exception {
		this.mockMvc
			.perform(post("/staff/accounts/staff/new").with(user("admin").roles("STAFF"))
				.with(csrf())
				.param("username", "assistant_vet")
				.param("temporaryPassword", "tempStaff123!"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/accounts"));

		UserAccount created = this.userAccountRepository.findByUsername("assistant_vet").orElseThrow();
		assertThat(created.getRole()).isEqualTo(UserRole.STAFF);
		assertThat(created.isMustChangePassword()).isTrue();
		assertThat(created.getOwner()).isNull();
		assertThat(this.passwordEncoder.matches("tempStaff123!", created.getPassword())).isTrue();
	}

	@Test
	void staffCanResetPasswordAndItRequiresPasswordChange() throws Exception {
		UserAccount account = new UserAccount("user_to_reset", this.passwordEncoder.encode("oldPassword"),
				UserRole.STAFF, false, null);
		account = this.userAccountRepository.saveAndFlush(account);

		this.mockMvc
			.perform(post("/staff/accounts/" + account.getId() + "/reset-password").with(user("admin").roles("STAFF"))
				.with(csrf())
				.param("temporaryPassword", "resetTempPass789!"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/accounts"));

		UserAccount updated = this.userAccountRepository.findById(account.getId()).orElseThrow();
		assertThat(updated.isMustChangePassword()).isTrue();
		assertThat(this.passwordEncoder.matches("resetTempPass789!", updated.getPassword())).isTrue();
	}

	@Test
	void ownerCannotAccessStaffAccountAdmin() throws Exception {
		this.mockMvc.perform(get("/staff/accounts").with(user("owner1").roles("OWNER")))
			.andExpect(status().isForbidden());
	}

}
