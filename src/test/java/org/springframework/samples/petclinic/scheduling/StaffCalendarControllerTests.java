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
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.scheduling.controller.StaffCalendarController;
import org.springframework.samples.petclinic.scheduling.service.SchedulingTimeService;
import org.springframework.samples.petclinic.scheduling.service.StaffCalendarService;
import org.springframework.samples.petclinic.security.CustomAuthenticationFailureHandler;
import org.springframework.samples.petclinic.security.CustomAuthenticationSuccessHandler;
import org.springframework.samples.petclinic.security.WebMvcSecurityTestConfig;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StaffCalendarController.class)
@Import(WebMvcSecurityTestConfig.class)
class StaffCalendarControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private StaffCalendarService staffCalendarService;

	@MockitoBean
	private VetRepository vetRepository;

	@MockitoBean
	private SchedulingTimeService timeService;

	@MockitoBean
	private CustomAuthenticationSuccessHandler successHandler;

	@MockitoBean
	private CustomAuthenticationFailureHandler failureHandler;

	@Test
	void showCalendarAsStaffSucceeds() throws Exception {
		given(timeService.today()).willReturn(LocalDate.of(2026, 8, 27));
		given(staffCalendarService.getCalendarItems(any(), any(), any())).willReturn(Collections.emptyList());
		Vet vet = new Vet();
		vet.setId(1);
		vet.setFirstName("Helen");
		vet.setLastName("Leary");
		vet.setActive(true);
		given(vetRepository.findAll()).willReturn(List.of(vet));

		mockMvc.perform(get("/scheduling/staff/calendar").with(user("staff1").roles("STAFF")))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/staffCalendar"))
			.andExpect(model().attributeExists("calendarItems"))
			.andExpect(model().attributeExists("vets"))
			.andExpect(model().attributeExists("startDate"))
			.andExpect(model().attributeExists("endDate"));
	}

	@Test
	void ownerCannotAccessStaffCalendar() throws Exception {
		mockMvc.perform(get("/scheduling/staff/calendar").with(user("owner1").roles("OWNER")))
			.andExpect(status().isForbidden());
	}

}
