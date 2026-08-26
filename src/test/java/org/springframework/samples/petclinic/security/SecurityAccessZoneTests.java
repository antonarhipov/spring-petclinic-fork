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
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityAccessZoneTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void publicRoutesAreAccessibleWithoutAuthentication() throws Exception {
		this.mockMvc.perform(get("/")).andExpect(status().isOk());
		this.mockMvc.perform(get("/vets.html")).andExpect(status().isOk());
		this.mockMvc.perform(get("/login")).andExpect(status().isOk());
	}

	@Test
	void unauthenticatedUserHittingStaffRouteRedirectsToLogin() throws Exception {
		this.mockMvc.perform(get("/staff/accounts"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
	}

	@Test
	void unauthenticatedUserHittingOwnersRouteRedirectsToLogin() throws Exception {
		this.mockMvc.perform(get("/owners/find"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
	}

	@Test
	void ownerHittingStaffRouteReceives403Forbidden() throws Exception {
		this.mockMvc.perform(get("/staff/accounts").with(user("owner1").roles("OWNER")))
			.andExpect(status().isForbidden());
	}

	@Test
	void ownerHittingOwnersRouteReceives403Forbidden() throws Exception {
		this.mockMvc.perform(get("/owners/find").with(user("owner1").roles("OWNER"))).andExpect(status().isForbidden());
	}

	@Test
	void staffHittingOwnersRouteIsAllowed() throws Exception {
		this.mockMvc.perform(get("/owners/find").with(user("staff1").roles("STAFF"))).andExpect(status().isOk());
	}

	@Test
	void ownerHittingStaffAppointmentsRouteReceives403Forbidden() throws Exception {
		this.mockMvc.perform(get("/staff/appointments").with(user("owner1").roles("OWNER")))
			.andExpect(status().isForbidden());
	}

	@Test
	void ownerHittingStaffQueueRouteReceives403Forbidden() throws Exception {
		this.mockMvc.perform(get("/staff/queue").with(user("owner1").roles("OWNER"))).andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedHittingMyAppointmentsRedirectsToLogin() throws Exception {
		this.mockMvc.perform(get("/my-appointments"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
	}

	@Test
	void staffHittingStaffAppointmentsIsAllowed() throws Exception {
		this.mockMvc.perform(get("/staff/appointments").with(user("staff1").roles("STAFF"))).andExpect(status().isOk());
	}

	@Test
	void staffHittingStaffQueueIsAllowed() throws Exception {
		this.mockMvc.perform(get("/staff/queue").with(user("staff1").roles("STAFF"))).andExpect(status().isOk());
	}

	@Test
	void anonymousHomePageHidesFindOwnersAndVeterinariansLinks() throws Exception {
		this.mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString("/owners/find"))))
			.andExpect(content().string(not(containsString("/vets.html"))));
	}

	@Test
	void ownerHomePageHidesFindOwnersLinkButShowsVeterinarians() throws Exception {
		this.mockMvc.perform(get("/").with(user("owner1").roles("OWNER")))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString("/owners/find"))))
			.andExpect(content().string(containsString("/vets.html")));
	}

	@Test
	void staffHomePageShowsFindOwnersAndVeterinariansLinks() throws Exception {
		this.mockMvc.perform(get("/").with(user("staff1").roles("STAFF")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("/owners/find")))
			.andExpect(content().string(containsString("/vets.html")));
	}

}
