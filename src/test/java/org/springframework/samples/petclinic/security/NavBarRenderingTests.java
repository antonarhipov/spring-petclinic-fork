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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests verifying conditional navigation bar rendering across anonymous, owner, and staff
 * roles.
 */
@SpringBootTest
class NavBarRenderingTests {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc;

	@BeforeEach
	void setup() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
	}

	@Test
	void anonymousNavBarRendersBrandHomeAndLoginLink() throws Exception {
		this.mockMvc.perform(get("/login"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("href=\"/\"")))
			.andExpect(content().string(containsString("href=\"/login\"")))
			.andExpect(content().string(not(containsString("href=\"/logout\""))))
			.andExpect(content().string(not(containsString("href=\"/my/appointments\""))))
			.andExpect(content().string(not(containsString("href=\"/scheduling/new\""))))
			.andExpect(content().string(not(containsString("href=\"/staff/queue\""))))
			.andExpect(content().string(not(containsString("href=\"/staff/config\""))));
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	@org.junit.jupiter.api.DisplayName("AC-14: owner navigation contains only owner entries")
	void ownerNavBarRendersOwnerLinksAndSessionWidget() throws Exception {
		this.mockMvc.perform(get("/login"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("href=\"/my/appointments\"")))
			.andExpect(content().string(containsString("href=\"/my/pets\"")))
			.andExpect(content().string(containsString("george")))
			.andExpect(content().string(containsString("Owner")))
			.andExpect(content().string(containsString("action=\"/logout\"")))
			.andExpect(content().string(not(containsString("href=\"/staff/queue\""))))
			.andExpect(content().string(not(containsString("href=\"/owners/find\""))))
			.andExpect(content().string(not(containsString("href=\"/vets.html\""))))
			.andExpect(content().string(not(containsString("href=\"/staff/config\""))));
	}

	@Test
	@WithMockUser(username = "staff", roles = "STAFF")
	@org.junit.jupiter.api.DisplayName("AC-15: staff navigation contains stock and scheduling entries")
	void staffNavBarRendersStaffLinksAndSessionWidget() throws Exception {
		this.mockMvc.perform(get("/vets.html"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("href=\"/staff/queue\"")))
			.andExpect(content().string(containsString("href=\"/owners/find\"")))
			.andExpect(content().string(containsString("href=\"/vets.html\"")))
			.andExpect(content().string(containsString("href=\"/staff/calendar\"")))
			.andExpect(content().string(containsString("href=\"/staff/settings\"")))
			.andExpect(content().string(containsString("staff")))
			.andExpect(content().string(containsString("Staff")))
			.andExpect(content().string(containsString("action=\"/logout\"")))
			.andExpect(content().string(not(containsString("href=\"/my/appointments\""))))
			.andExpect(content().string(not(containsString("href=\"/my/pets\""))));
	}

}
