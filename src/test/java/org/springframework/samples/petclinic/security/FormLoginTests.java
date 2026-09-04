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
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests verifying form login authentication and redirection behaviors per AC-125, AC-127.
 */
@SpringBootTest
class FormLoginTests {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc;

	@BeforeEach
	void setup() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
	}

	@Test
	void ownerLoginRedirectsToMyAppointments() throws Exception {
		mockMvc.perform(formLogin("/login").user("george").password("george123"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/my/appointments"))
			.andExpect(request().sessionAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
					org.hamcrest.Matchers.<SecurityContext>hasProperty("authentication",
							org.hamcrest.Matchers.hasProperty("authorities",
									org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.hasProperty("authority",
											org.hamcrest.Matchers.is("ROLE_OWNER")))))));
	}

	@Test
	void staffLoginRedirectsToStaffQueue() throws Exception {
		mockMvc.perform(formLogin("/login").user("staff").password("staff123"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/queue"));

		mockMvc.perform(formLogin("/login").user("admin").password("admin123"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/queue"));
	}

	@Test
	void badCredentialsRedirectsToLoginError() throws Exception {
		mockMvc.perform(formLogin("/login").user("george").password("wrongpassword"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login?error"))
			.andExpect(request()
				.sessionAttributeDoesNotExist(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));

		mockMvc.perform(formLogin("/login").user("nonexistent").password("password"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login?error"))
			.andExpect(request()
				.sessionAttributeDoesNotExist(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void authenticatedOwnerAccessingRootRedirectsToMyAppointments() throws Exception {
		mockMvc.perform(get("/"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/my/appointments"));
	}

	@Test
	@WithMockUser(username = "staff", roles = "STAFF")
	void authenticatedStaffAccessingRootRedirectsToStaffQueue() throws Exception {
		mockMvc.perform(get("/"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/staff/queue"));
	}

	@Test
	void logoutRedirectsToLoginWithLogoutParam() throws Exception {
		mockMvc.perform(post("/logout").with(user("george").roles("OWNER")).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login?logout"));
	}

}
