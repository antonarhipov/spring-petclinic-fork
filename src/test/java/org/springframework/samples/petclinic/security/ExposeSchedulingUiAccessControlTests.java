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
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.samples.petclinic.appointment.AppointmentRequest;
import org.springframework.samples.petclinic.appointment.AppointmentRequestRepository;
import org.springframework.samples.petclinic.appointment.AppointmentRequestStatus;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Authorization and reachability tests for the UI-exposure change: owner navigation and
 * self-scheduling, and the staff-only calendar, clinic-settings, and accounts screens.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ExposeSchedulingUiAccessControlTests {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc;

	@Autowired
	private AppointmentRequestRepository requestRepository;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
	}

	@Test
	void anonymousIsRedirectedToLoginOnNewScreens() throws Exception {
		this.mockMvc.perform(get("/owners/1/scheduling/new")).andExpect(redirectedUrl("/login"));
		this.mockMvc.perform(get("/staff/calendar")).andExpect(redirectedUrl("/login"));
		this.mockMvc.perform(get("/staff/clinic-settings")).andExpect(redirectedUrl("/login"));
		this.mockMvc.perform(get("/staff/accounts")).andExpect(redirectedUrl("/login"));
	}

	@Test
	@WithUserDetails("george")
	void ownerReachesOwnSelfSchedulingButNotOthersNorStaffScreens() throws Exception {
		// George is owner 1
		this.mockMvc.perform(get("/owners/1/scheduling/new"))
			.andExpect(status().isOk())
			.andExpect(view().name("owners/scheduling/newRequest"));

		// Cannot start scheduling scoped to another owner
		this.mockMvc.perform(get("/owners/2/scheduling/new")).andExpect(status().isForbidden());

		// Staff management screens are off-limits to owners
		this.mockMvc.perform(get("/staff/calendar")).andExpect(status().isForbidden());
		this.mockMvc.perform(get("/staff/clinic-settings")).andExpect(status().isForbidden());
		this.mockMvc.perform(get("/staff/accounts")).andExpect(status().isForbidden());
	}

	@Test
	@WithUserDetails("staff")
	void staffReachesManagementScreens() throws Exception {
		this.mockMvc.perform(get("/staff/calendar"))
			.andExpect(status().isOk())
			.andExpect(view().name("calendar/index"));
		this.mockMvc.perform(get("/staff/calendar/vets/1"))
			.andExpect(status().isOk())
			.andExpect(view().name("calendar/vetSchedule"));
		this.mockMvc.perform(get("/staff/calendar/closures"))
			.andExpect(status().isOk())
			.andExpect(view().name("calendar/closures"));
		this.mockMvc.perform(get("/staff/clinic-settings"))
			.andExpect(status().isOk())
			.andExpect(view().name("calendar/settings"));
		this.mockMvc.perform(get("/staff/accounts"))
			.andExpect(status().isOk())
			.andExpect(view().name("owners/accounts"));
	}

	@Test
	@WithUserDetails("george")
	void ownerNavShownToOwners() throws Exception {
		String ownerPage = this.mockMvc.perform(get("/owners/1"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		// Owner sees owner-scoped links, not the staff management links
		assertThat(ownerPage).contains("/owners/1/scheduling/new");
		assertThat(ownerPage).doesNotContain("/staff/calendar");
	}

	@Test
	@WithUserDetails("staff")
	void staffNavShownToStaff() throws Exception {
		String staffPage = this.mockMvc.perform(get("/owners/1"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		// Staff sees the staff management links, not the owner-scoped nav
		assertThat(staffPage).contains("/staff/calendar");
		assertThat(staffPage).contains("/staff/accounts");
		assertThat(staffPage).doesNotContain("/scheduling/new");
	}

	@Test
	@WithUserDetails("george")
	void ownerInitiatesRequestAndConsentGateThenProgresses() throws Exception {
		// Initiating for a pet the owner does not own is refused (routed back to the
		// form)
		this.mockMvc
			.perform(post("/owners/1/scheduling").with(csrf()).param("petId", "999").param("freeText", "annual check"))
			.andExpect(redirectedUrl("/owners/1/scheduling/new"));

		// Initiating for an owned pet creates a DRAFT that immediately awaits consent
		String location = this.mockMvc
			.perform(post("/owners/1/scheduling").with(csrf())
				.param("petId", "1")
				.param("freeText", "My dog needs a check-up next week in the morning"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrlPattern("/owners/1/scheduling/*"))
			.andReturn()
			.getResponse()
			.getRedirectedUrl();
		Integer requestId = Integer.valueOf(location.substring(location.lastIndexOf('/') + 1));

		AppointmentRequest created = this.requestRepository.findById(requestId).orElseThrow();
		assertThat(created.getStatus()).isEqualTo(AppointmentRequestStatus.AWAITING_CONSENT);
		assertThat(created.getOwner().getId()).isEqualTo(1);

		this.mockMvc.perform(get("/owners/1/scheduling/" + requestId))
			.andExpect(status().isOk())
			.andExpect(view().name("owners/scheduling/request"));

		// Granting consent passes the gate; interpretation runs (or falls back to the
		// staff queue when the AI is unavailable), so the request never stays awaiting
		// consent.
		this.mockMvc.perform(post("/owners/1/scheduling/" + requestId + "/consent").with(csrf()))
			.andExpect(status().is3xxRedirection());
		AppointmentRequest afterConsent = this.requestRepository.findById(requestId).orElseThrow();
		assertThat(afterConsent.getStatus()).isNotEqualTo(AppointmentRequestStatus.AWAITING_CONSENT);
		assertThat(afterConsent.getStatus()).isNotEqualTo(AppointmentRequestStatus.DRAFT);
	}

	@Test
	@WithUserDetails("george")
	void ownerCannotDriveAnotherOwnersScheduling() throws Exception {
		this.mockMvc.perform(post("/owners/2/scheduling").with(csrf()).param("petId", "2").param("freeText", "hi"))
			.andExpect(status().isForbidden());
	}

}
