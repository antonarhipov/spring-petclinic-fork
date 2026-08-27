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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.scheduling.controller.AppointmentController;
import org.springframework.samples.petclinic.scheduling.dto.AppointmentDto;
import org.springframework.samples.petclinic.scheduling.model.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.model.BookingSource;
import org.springframework.samples.petclinic.scheduling.model.CareType;
import org.springframework.samples.petclinic.scheduling.service.AppointmentService;
import org.springframework.samples.petclinic.security.Account;
import org.springframework.samples.petclinic.security.AccountRepository;
import org.springframework.samples.petclinic.security.AccountRole;
import org.springframework.samples.petclinic.security.CustomAuthenticationFailureHandler;
import org.springframework.samples.petclinic.security.CustomAuthenticationSuccessHandler;
import org.springframework.samples.petclinic.security.UserPrincipal;
import org.springframework.samples.petclinic.security.WebMvcSecurityTestConfig;
import org.springframework.samples.petclinic.system.ResourceNotFoundException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AppointmentController.class)
@Import(WebMvcSecurityTestConfig.class)
class AppointmentControllerTests {

	private static final int OWNER_ID = 1;

	private static final int APPOINTMENT_ID = 10;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AppointmentService appointmentService;

	@MockitoBean
	private AccountRepository accountRepository;

	@MockitoBean
	private CustomAuthenticationSuccessHandler successHandler;

	@MockitoBean
	private CustomAuthenticationFailureHandler failureHandler;

	private UserPrincipal ownerPrincipal;

	private UserPrincipal staffPrincipal;

	private Owner owner;

	private AppointmentDto appointmentDto;

	@BeforeEach
	void setUp() {
		owner = new Owner();
		owner.setId(OWNER_ID);
		owner.setFirstName("George");
		owner.setLastName("Franklin");

		Account ownerAccount = new Account();
		ownerAccount.setId(10);
		ownerAccount.setUsername("george");
		ownerAccount.setRole(AccountRole.OWNER);
		ownerAccount.setOwner(owner);
		ownerPrincipal = UserPrincipal.fromAccount(ownerAccount);

		Account staffAccount = new Account();
		staffAccount.setId(20);
		staffAccount.setUsername("vetstaff");
		staffAccount.setRole(AccountRole.STAFF);
		staffPrincipal = UserPrincipal.fromAccount(staffAccount);

		given(accountRepository.findByUsernameIgnoreCase("george")).willReturn(Optional.of(ownerAccount));
		given(accountRepository.findByUsernameIgnoreCase("vetstaff")).willReturn(Optional.of(staffAccount));

		Instant start = Instant.parse("2026-08-28T10:00:00Z");
		Instant end = Instant.parse("2026-08-28T10:30:00Z");
		appointmentDto = new AppointmentDto(APPOINTMENT_ID, OWNER_ID, "George Franklin", 1, "Leo", 1, "James Carter",
				start, end, 30, CareType.GENERAL, null, null, AppointmentStatus.BOOKED, BookingSource.OWNER_GUIDED,
				null, null, null, null, null, 0L, null);
	}

	@Test
	void testListUpcomingAppointmentsUnauthenticatedRedirectsToLogin() throws Exception {
		mockMvc.perform(get("/scheduling/appointments")).andExpect(status().is3xxRedirection());
	}

	@Test
	void testListUpcomingAppointmentsForOwnerSuccess() throws Exception {
		given(appointmentService.getOwnerUpcomingAppointments(OWNER_ID)).willReturn(List.of(appointmentDto));

		mockMvc.perform(get("/scheduling/appointments").with(user(ownerPrincipal)))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/appointments"))
			.andExpect(model().attributeExists("appointments"));
	}

	@Test
	void testShowAppointmentDetailsOwnerSuccess() throws Exception {
		given(appointmentService.getAppointmentDtoForOwner(APPOINTMENT_ID, OWNER_ID)).willReturn(appointmentDto);

		mockMvc.perform(get("/scheduling/appointments/" + APPOINTMENT_ID).with(user(ownerPrincipal)))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/appointmentDetail"))
			.andExpect(model().attributeExists("appointment"));
	}

	@Test
	void testShowAppointmentDetailsForeignOwnerReturns404() throws Exception {
		given(appointmentService.getAppointmentDtoForOwner(APPOINTMENT_ID, OWNER_ID))
			.willThrow(new ResourceNotFoundException("Appointment not found"));

		mockMvc.perform(get("/scheduling/appointments/" + APPOINTMENT_ID).with(user(ownerPrincipal)))
			.andExpect(status().isNotFound());
	}

	@Test
	void testCancelAppointmentOwnerSuccess() throws Exception {
		given(appointmentService.cancelAppointmentByOwner(eq(APPOINTMENT_ID), eq(OWNER_ID), anyString(), eq("george")))
			.willReturn(appointmentDto);

		mockMvc
			.perform(post("/scheduling/appointments/" + APPOINTMENT_ID + "/cancel").with(user(ownerPrincipal))
				.with(csrf())
				.param("cancellationReason", "Scheduling conflict"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/appointments/" + APPOINTMENT_ID))
			.andExpect(flash().attributeExists("successMessage"));

		verify(appointmentService).cancelAppointmentByOwner(APPOINTMENT_ID, OWNER_ID, "Scheduling conflict", "george");
	}

	@Test
	void testCancelAppointmentWithoutCsrfForbidden() throws Exception {
		mockMvc
			.perform(post("/scheduling/appointments/" + APPOINTMENT_ID + "/cancel").with(user(ownerPrincipal))
				.param("cancellationReason", "No CSRF"))
			.andExpect(status().isForbidden());
	}

	@Test
	void testCancelAppointmentIllegalStateRedirectsWithError() throws Exception {
		given(appointmentService.cancelAppointmentByOwner(eq(APPOINTMENT_ID), eq(OWNER_ID), any(), eq("george")))
			.willThrow(new IllegalStateException("Cannot cancel an appointment at or after its scheduled start time"));

		mockMvc
			.perform(post("/scheduling/appointments/" + APPOINTMENT_ID + "/cancel").with(user(ownerPrincipal))
				.with(csrf())
				.param("cancellationReason", "Late cancel"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/appointments/" + APPOINTMENT_ID))
			.andExpect(flash().attribute("errorMessage",
					"Cannot cancel an appointment at or after its scheduled start time"));
	}

	@Test
	void testCancelAppointmentNotFoundReturns404() throws Exception {
		given(appointmentService.cancelAppointmentByOwner(eq(APPOINTMENT_ID), eq(OWNER_ID), any(), eq("george")))
			.willThrow(new ResourceNotFoundException("Appointment not found"));

		mockMvc
			.perform(post("/scheduling/appointments/" + APPOINTMENT_ID + "/cancel").with(user(ownerPrincipal))
				.with(csrf()))
			.andExpect(status().isNotFound());
	}

	@Test
	void testOwnerRoutesForbiddenForStaffAccountWithoutOwner() throws Exception {
		mockMvc.perform(get("/scheduling/appointments").with(user(staffPrincipal))).andExpect(status().isForbidden());
	}

}
