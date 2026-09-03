/*
 * Copyright 2012-2026 the original author or authors.
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests verifying the exact authorization matrix from spec.md under anonymous, owner, and
 * staff actors.
 */
@SpringBootTest
class SecurityMatrixTests {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc;

	@BeforeEach
	void setup() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
	}

	// --- Anonymous user ---

	@Test
	void anonymousCanAccessLoginPage() throws Exception {
		mockMvc.perform(get("/login")).andExpect(status().isOk());
	}

	@Test
	void anonymousCanAccessStaticResources() throws Exception {
		mockMvc.perform(get("/resources/css/petclinic.css")).andExpect(status().isOk());
	}

	@Test
	void anonymousAccessToRootRedirectsToLogin() throws Exception {
		mockMvc.perform(get("/"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", endsWith("/login")));
	}

	@Test
	void anonymousAccessToOwnerRoutesRedirectsToLogin() throws Exception {
		mockMvc.perform(get("/my/appointments"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", endsWith("/login")));

		mockMvc.perform(get("/scheduling/new"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", endsWith("/login")));
	}

	@Test
	void anonymousAccessToStaffRoutesRedirectsToLogin() throws Exception {
		mockMvc.perform(get("/owners/find"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", endsWith("/login")));

		mockMvc.perform(get("/vets.html"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", endsWith("/login")));

		mockMvc.perform(get("/staff/queue"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", endsWith("/login")));

		mockMvc.perform(get("/actuator/health"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", endsWith("/login")));
	}

	// --- Owner user ---

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void ownerAccessToRootRedirectsToMyAppointments() throws Exception {
		mockMvc.perform(get("/"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/my/appointments"));
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void ownerCannotAccessStaffRoutes() throws Exception {
		mockMvc.perform(get("/owners/find")).andExpect(status().isForbidden());

		mockMvc.perform(get("/owners/new")).andExpect(status().isForbidden());

		mockMvc.perform(get("/vets.html")).andExpect(status().isForbidden());

		mockMvc.perform(get("/vets")).andExpect(status().isForbidden());

		mockMvc.perform(get("/staff/queue")).andExpect(status().isForbidden());

		mockMvc.perform(get("/actuator/health")).andExpect(status().isForbidden());

		mockMvc.perform(get("/oups")).andExpect(status().isForbidden());
	}

	// --- Staff user ---

	@Test
	@WithMockUser(username = "staff", roles = "STAFF")
	void staffAccessToRootRedirectsToStaffQueue() throws Exception {
		mockMvc.perform(get("/"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/staff/queue"));
	}

	@Test
	@WithMockUser(username = "staff", roles = "STAFF")
	void staffCanAccessStaffRoutes() throws Exception {
		mockMvc.perform(get("/owners/find")).andExpect(status().isOk());

		mockMvc.perform(get("/vets.html")).andExpect(status().isOk());

		mockMvc.perform(get("/vets")).andExpect(status().isOk());

		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	@Test
	@WithMockUser(username = "staff", roles = "STAFF")
	void staffCannotAccessOwnerRoutes() throws Exception {
		mockMvc.perform(get("/my/appointments")).andExpect(status().isForbidden());

		mockMvc.perform(get("/my/pets")).andExpect(status().isForbidden());

		mockMvc.perform(get("/scheduling/new")).andExpect(status().isForbidden());
	}

}
