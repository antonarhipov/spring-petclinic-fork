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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.PetType;
import org.springframework.samples.petclinic.scheduling.controller.StaffQueueController;
import org.springframework.samples.petclinic.scheduling.model.CareType;
import org.springframework.samples.petclinic.scheduling.model.RequestStatus;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.model.StaffClaim;
import org.springframework.samples.petclinic.scheduling.repository.ReservationRepository;
import org.springframework.samples.petclinic.scheduling.repository.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.service.StaffFallbackService;
import org.springframework.samples.petclinic.security.Account;
import org.springframework.samples.petclinic.security.AccountRepository;
import org.springframework.samples.petclinic.security.AccountRole;
import org.springframework.samples.petclinic.security.CustomAuthenticationFailureHandler;
import org.springframework.samples.petclinic.security.CustomAuthenticationSuccessHandler;
import org.springframework.samples.petclinic.security.UserPrincipal;
import org.springframework.samples.petclinic.security.WebMvcSecurityTestConfig;
import org.springframework.samples.petclinic.vet.SpecialtyRepository;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StaffQueueController.class)
@Import(WebMvcSecurityTestConfig.class)
class StaffQueueControllerTests {

	private static final int REQUEST_ID = 100;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private StaffFallbackService staffFallbackService;

	@MockitoBean
	private SchedulingRequestRepository schedulingRequestRepository;

	@MockitoBean
	private ReservationRepository reservationRepository;

	@MockitoBean
	private VetRepository vetRepository;

	@MockitoBean
	private SpecialtyRepository specialtyRepository;

	@MockitoBean
	private AccountRepository accountRepository;

	@MockitoBean
	private CustomAuthenticationSuccessHandler successHandler;

	@MockitoBean
	private CustomAuthenticationFailureHandler failureHandler;

	private UserPrincipal staffPrincipal;

	private UserPrincipal ownerPrincipal;

	private SchedulingRequest request;

	@BeforeEach
	void setUp() {
		Account staffAccount = new Account();
		staffAccount.setId(40);
		staffAccount.setUsername("staffUser");
		staffAccount.setRole(AccountRole.STAFF);
		staffAccount.setActive(true);
		staffPrincipal = UserPrincipal.fromAccount(staffAccount);

		Owner owner = new Owner();
		owner.setId(1);
		owner.setFirstName("George");
		owner.setLastName("Franklin");

		Account ownerAccount = new Account();
		ownerAccount.setId(50);
		ownerAccount.setUsername("ownerUser");
		ownerAccount.setRole(AccountRole.OWNER);
		ownerAccount.setOwner(owner);
		ownerAccount.setActive(true);
		ownerPrincipal = UserPrincipal.fromAccount(ownerAccount);

		PetType dog = new PetType();
		dog.setId(1);
		dog.setName("dog");

		Pet pet = new Pet();
		pet.setId(10);
		pet.setName("Leo");
		pet.setType(dog);

		request = new SchedulingRequest();
		request.setId(REQUEST_ID);
		request.setOwner(owner);
		request.setPet(pet);
		request.setStatus(RequestStatus.STAFF_QUEUED);
		request.setCareType(CareType.GENERAL);
	}

	@Test
	void testShowQueueAsStaffReturnsOk() throws Exception {
		given(staffFallbackService.getFallbackQueue()).willReturn(List.of(request));

		mockMvc.perform(get("/scheduling/staff/queue").with(user(staffPrincipal)))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/staffQueue"))
			.andExpect(model().attributeExists("queue"));
	}

	@Test
	void testShowQueueAsOwnerReturnsForbidden() throws Exception {
		mockMvc.perform(get("/scheduling/staff/queue").with(user(ownerPrincipal))).andExpect(status().isForbidden());
	}

	@Test
	void testShowQueueAnonymousRedirectsToLogin() throws Exception {
		mockMvc.perform(get("/scheduling/staff/queue")).andExpect(status().is3xxRedirection());
	}

	@Test
	void testClaimRequestSuccess() throws Exception {
		mockMvc.perform(post("/scheduling/staff/queue/{id}/claim", REQUEST_ID).with(user(staffPrincipal)).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/staff/requests/" + REQUEST_ID));

		verify(staffFallbackService).claimRequest(eq(REQUEST_ID), eq("staffuser"));
	}

	@Test
	void testReleaseClaimSuccess() throws Exception {
		mockMvc
			.perform(post("/scheduling/staff/queue/{id}/release", REQUEST_ID).with(user(staffPrincipal)).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/staff/queue"));

		verify(staffFallbackService).releaseClaim(eq(REQUEST_ID), eq("staffuser"));
	}

	@Test
	void testShowRequestDetail() throws Exception {
		given(schedulingRequestRepository.findWithDetailsById(REQUEST_ID)).willReturn(Optional.of(request));
		given(staffFallbackService.getClaimForRequest(REQUEST_ID)).willReturn(Optional.empty());
		given(reservationRepository.findWithVetByRequestId(REQUEST_ID)).willReturn(Collections.emptyList());
		given(vetRepository.findAll()).willReturn(Collections.emptyList());
		given(specialtyRepository.findAll()).willReturn(Collections.emptyList());

		mockMvc.perform(get("/scheduling/staff/requests/{id}", REQUEST_ID).with(user(staffPrincipal)))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/staffRequestDetail"))
			.andExpect(model().attributeExists("request"));
	}

	@Test
	void testUpdateInterpretationSuccess() throws Exception {
		mockMvc
			.perform(post("/scheduling/staff/requests/{id}/edit-interpretation", REQUEST_ID)
				.param("durationMinutes", "45")
				.param("careType", "GENERAL")
				.with(user(staffPrincipal))
				.with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/staff/requests/" + REQUEST_ID));

		verify(staffFallbackService).updateStructuredInterpretation(eq(REQUEST_ID), eq(45), eq(CareType.GENERAL), any(),
				any(), any(), any(), eq("staffuser"));
	}

	@Test
	void testCreateOfferSuccess() throws Exception {
		mockMvc
			.perform(post("/scheduling/staff/requests/{id}/offer", REQUEST_ID).param("vetId", "1")
				.param("startTime", "2026-08-28T10:00:00Z")
				.with(user(staffPrincipal))
				.with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/staff/requests/" + REQUEST_ID));

		verify(staffFallbackService).createStaffOffer(eq(REQUEST_ID), eq(1), eq(Instant.parse("2026-08-28T10:00:00Z")),
				eq("staffuser"));
	}

	@Test
	void testRevokeOfferSuccess() throws Exception {
		mockMvc
			.perform(post("/scheduling/staff/requests/{id}/revoke-offer", REQUEST_ID).param("reservationId", "50")
				.with(user(staffPrincipal))
				.with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/staff/requests/" + REQUEST_ID));

		verify(staffFallbackService).revokeStaffOffer(eq(REQUEST_ID), eq(50), eq("staffuser"));
	}

	@Test
	void testDirectBookSuccess() throws Exception {
		mockMvc
			.perform(post("/scheduling/staff/requests/{id}/direct-book", REQUEST_ID).param("vetId", "1")
				.param("startTime", "2026-08-28T10:00:00Z")
				.param("offlineReason", "Owner called by phone")
				.with(user(staffPrincipal))
				.with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/staff/requests/" + REQUEST_ID));

		verify(staffFallbackService).directBook(eq(REQUEST_ID), eq(1), eq(Instant.parse("2026-08-28T10:00:00Z")),
				eq("Owner called by phone"), eq("staffuser"));
	}

	@Test
	void testCancelRequestSuccess() throws Exception {
		mockMvc
			.perform(post("/scheduling/staff/requests/{id}/cancel", REQUEST_ID)
				.param("reason", "Cancelled by owner request")
				.with(user(staffPrincipal))
				.with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/staff/queue"));

		verify(staffFallbackService).cancelRequestByStaff(eq(REQUEST_ID), eq("Cancelled by owner request"),
				eq("staffuser"));
	}

}
