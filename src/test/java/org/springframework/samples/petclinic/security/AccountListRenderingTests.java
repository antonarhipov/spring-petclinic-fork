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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that the account list page renders when open-session-in-view is disabled. This
 * test intentionally does not use {@code @Transactional}, so no Hibernate session is
 * bound to the request thread during view rendering; accessing the lazily-loaded owner
 * association from the template must therefore not fail.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountListRenderingTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private Integer createdAccountId;

	@BeforeEach
	void setUp() {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		UserAccount account = new UserAccount("render_owner", this.passwordEncoder.encode("tempPass123!"),
				UserRole.OWNER, true, owner);
		this.createdAccountId = this.userAccountRepository.save(account).getId();
	}

	@AfterEach
	void tearDown() {
		if (this.createdAccountId != null) {
			this.userAccountRepository.deleteById(this.createdAccountId);
		}
	}

	@Test
	void staffCanRenderAccountListWithLinkedOwner() throws Exception {
		this.mockMvc.perform(get("/staff/accounts").with(user("admin").roles("STAFF")))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("render_owner")));
	}

}
