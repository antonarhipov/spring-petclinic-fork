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

package org.springframework.samples.petclinic.scheduling;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.scheduling.controller.ClinicSettingsController;
import org.springframework.samples.petclinic.scheduling.dto.ClinicSettingsForm;
import org.springframework.samples.petclinic.scheduling.service.ClinicSettingsService;
import org.springframework.samples.petclinic.security.CustomAuthenticationFailureHandler;
import org.springframework.samples.petclinic.security.CustomAuthenticationSuccessHandler;
import org.springframework.samples.petclinic.security.WebMvcSecurityTestConfig;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ClinicSettingsController.class)
@Import(WebMvcSecurityTestConfig.class)
class ClinicSettingsControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ClinicSettingsService clinicSettingsService;

	@MockitoBean
	private CustomAuthenticationSuccessHandler successHandler;

	@MockitoBean
	private CustomAuthenticationFailureHandler failureHandler;

	@Test
	void showSettingsFormAsStaffSucceeds() throws Exception {
		given(clinicSettingsService.getSettingsForm()).willReturn(new ClinicSettingsForm());

		mockMvc.perform(get("/scheduling/admin/settings").with(user("staff1").roles("STAFF")))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/settings"))
			.andExpect(model().attributeExists("settingsForm"));
	}

	@Test
	void updateSettingsAsStaffSucceedsAndRedirects() throws Exception {
		mockMvc
			.perform(post("/scheduling/admin/settings").with(user("staff1").roles("STAFF"))
				.with(csrf())
				.param("minDurationMinutes", "15")
				.param("defaultDurationMinutes", "30")
				.param("maxDurationMinutes", "60")
				.param("ownerBookingHorizonDays", "56")
				.param("staffBookingHorizonDays", "365")
				.param("guidedHoldDurationMinutes", "5")
				.param("staffOfferHoldDurationHours", "24")
				.param("staffClaimInactivityMinutes", "30")
				.param("fallbackDeadlineDays", "7")
				.param("sensitiveDataRetentionDays", "30")
				.param("emergencyPhone", "608-555-0199")
				.param("urgentCareGuidance", "Urgent notice"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/admin/settings"));

		verify(clinicSettingsService).updateSettings(any(ClinicSettingsForm.class), eq("staff1"));
	}

	@Test
	void ownerCannotAccessSettings() throws Exception {
		mockMvc.perform(get("/scheduling/admin/settings").with(user("owner1").roles("OWNER")))
			.andExpect(status().isForbidden());
	}

}
