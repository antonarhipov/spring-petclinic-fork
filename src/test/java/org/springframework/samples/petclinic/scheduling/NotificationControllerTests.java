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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.samples.petclinic.scheduling.controller.NotificationController;
import org.springframework.samples.petclinic.scheduling.model.Appointment;
import org.springframework.samples.petclinic.scheduling.model.Notification;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.repository.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.repository.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.service.NotificationService;
import org.springframework.samples.petclinic.security.Account;
import org.springframework.samples.petclinic.security.AccountRepository;
import org.springframework.samples.petclinic.security.AccountRole;
import org.springframework.samples.petclinic.security.CustomAuthenticationFailureHandler;
import org.springframework.samples.petclinic.security.CustomAuthenticationSuccessHandler;
import org.springframework.samples.petclinic.security.UserPrincipal;
import org.springframework.samples.petclinic.security.WebMvcSecurityTestConfig;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationController.class)
@Import(WebMvcSecurityTestConfig.class)
class NotificationControllerTests {

	private static final int OWNER_ID = 1;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private NotificationService notificationService;

	@MockitoBean
	private SchedulingRequestRepository schedulingRequestRepository;

	@MockitoBean
	private AppointmentRepository appointmentRepository;

	@MockitoBean
	private AccountRepository accountRepository;

	@MockitoBean
	private CustomAuthenticationSuccessHandler successHandler;

	@MockitoBean
	private CustomAuthenticationFailureHandler failureHandler;

	private UserPrincipal ownerPrincipal;

	private UserPrincipal staffPrincipal;

	private Owner owner;

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
	}

	@Test
	void testListNotificationsUnauthenticatedRedirectsToLogin() throws Exception {
		mockMvc.perform(get("/scheduling/notifications")).andExpect(status().is3xxRedirection());
	}

	@Test
	void testListNotificationsAsStaffForbidden() throws Exception {
		mockMvc.perform(get("/scheduling/notifications").with(user(staffPrincipal))).andExpect(status().isForbidden());
	}

	@Test
	void testListNotificationsAsOwnerWithTargetAuthorization() throws Exception {
		Notification n1 = new Notification();
		n1.setId(101);
		n1.setOwner(owner);
		n1.setTitleKey("notification.appointmentConfirmed.title");
		n1.setMessageKey("notification.appointmentConfirmed.message");
		n1.setTargetUrl("/requests/1");
		n1.setRead(false);
		n1.setCreatedAt(Instant.now());

		Notification n2 = new Notification();
		n2.setId(102);
		n2.setOwner(owner);
		n2.setTitleKey("notification.staffOffer.title");
		n2.setMessageKey("notification.staffOffer.message");
		n2.setTargetUrl("/requests/999"); // Foreign request
		n2.setRead(false);
		n2.setCreatedAt(Instant.now());

		given(notificationService.findByOwner(OWNER_ID)).willReturn(List.of(n1, n2));

		SchedulingRequest req = new SchedulingRequest();
		req.setId(1);
		req.setOwner(owner);
		given(schedulingRequestRepository.findByIdAndOwnerId(1, OWNER_ID)).willReturn(Optional.of(req));
		given(schedulingRequestRepository.findByIdAndOwnerId(999, OWNER_ID)).willReturn(Optional.empty());

		mockMvc.perform(get("/scheduling/notifications").with(user(ownerPrincipal)))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/notifications"))
			.andExpect(model().attributeExists("notifications"))
			.andExpect(model().attribute("notifications", hasSize(2)))
			.andExpect(content().string(containsString("Appointment Confirmed")))
			.andExpect(content().string(containsString("Your appointment has been successfully booked.")));
	}

	@Test
	void testMarkAsReadAsOwner() throws Exception {
		mockMvc.perform(post("/scheduling/notifications/101/read").with(user(ownerPrincipal)).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/notifications"));

		verify(notificationService).markAsRead(101, OWNER_ID);
	}

	@Test
	void testMarkAsReadWithoutCsrfFails() throws Exception {
		mockMvc.perform(post("/scheduling/notifications/101/read").with(user(ownerPrincipal)))
			.andExpect(status().isForbidden());
	}

}
