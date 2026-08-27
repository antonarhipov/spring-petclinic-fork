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

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.PetRepository;
import org.springframework.samples.petclinic.scheduling.controller.SchedulingRequestController;
import org.springframework.samples.petclinic.scheduling.dto.AvailabilityIntervalViewDto;
import org.springframework.samples.petclinic.scheduling.dto.AvailabilityWindowViewDto;
import org.springframework.samples.petclinic.scheduling.matching.MatchingCoordinator;
import org.springframework.samples.petclinic.scheduling.model.RequestStatus;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.repository.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.service.AIInterpretationService;
import org.springframework.samples.petclinic.scheduling.service.AvailabilityViewService;
import org.springframework.samples.petclinic.scheduling.service.ReservationService;
import org.springframework.samples.petclinic.scheduling.service.SchedulingRequestService;
import org.springframework.samples.petclinic.security.Account;
import org.springframework.samples.petclinic.security.AccountRepository;
import org.springframework.samples.petclinic.security.AccountRole;
import org.springframework.samples.petclinic.security.CustomAuthenticationFailureHandler;
import org.springframework.samples.petclinic.security.CustomAuthenticationSuccessHandler;
import org.springframework.samples.petclinic.security.UserPrincipal;
import org.springframework.samples.petclinic.security.WebMvcSecurityTestConfig;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SchedulingRequestController.class)
@Import(WebMvcSecurityTestConfig.class)
class SchedulingRequestControllerTests {

	private static final int OWNER_ID = 1;

	private static final int PET_ID = 10;

	private static final int REQUEST_ID = 100;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SchedulingRequestService schedulingRequestService;

	@MockitoBean
	private SchedulingRequestRepository schedulingRequestRepository;

	@MockitoBean
	private AIInterpretationService aiInterpretationService;

	@MockitoBean
	private MatchingCoordinator matchingCoordinator;

	@MockitoBean
	private ReservationService reservationService;

	@MockitoBean
	private PetRepository petRepository;

	@MockitoBean
	private AccountRepository accountRepository;

	@MockitoBean
	private AvailabilityViewService availabilityViewService;

	@MockitoBean
	private CustomAuthenticationSuccessHandler successHandler;

	@MockitoBean
	private CustomAuthenticationFailureHandler failureHandler;

	private UserPrincipal ownerPrincipal;

	private Owner owner;

	private Pet pet;

	private SchedulingRequest request;

	@BeforeEach
	void setUp() {
		owner = new Owner();
		owner.setId(OWNER_ID);
		owner.setFirstName("George");
		owner.setLastName("Franklin");

		pet = new Pet();
		pet.setId(PET_ID);
		pet.setName("Leo");

		Account account = new Account();
		account.setId(50);
		account.setUsername("owner1");
		account.setPassword("hashed");
		account.setRole(AccountRole.OWNER);
		account.setOwner(owner);

		request = new SchedulingRequest();
		request.setId(REQUEST_ID);
		request.setOwner(owner);
		request.setPet(pet);
		request.setStatus(RequestStatus.AWAITING_CONSENT);
		request.setOriginalText("Need vaccination");
		request.setLanguage("en");

		ownerPrincipal = new UserPrincipal(50, "owner1", "hashed", AccountRole.OWNER, OWNER_ID, false, true, null);

		given(accountRepository.findByUsernameIgnoreCase("owner1")).willReturn(Optional.of(account));
		given(petRepository.findByIdAndOwnerId(PET_ID, OWNER_ID)).willReturn(Optional.of(pet));
		given(petRepository.findByIdAndOwnerId(999, OWNER_ID)).willReturn(Optional.empty());
		given(schedulingRequestRepository.findWithDetailsByIdAndOwnerId(REQUEST_ID, OWNER_ID))
			.willReturn(Optional.of(request));
		given(schedulingRequestRepository.findWithDetailsByIdAndOwnerId(999, OWNER_ID)).willReturn(Optional.empty());
		given(schedulingRequestRepository.findByIdAndOwnerId(REQUEST_ID, OWNER_ID)).willReturn(Optional.of(request));
		given(schedulingRequestRepository.findByIdAndOwnerId(999, OWNER_ID)).willReturn(Optional.empty());
	}

	@Test
	void showNewRequestFormForOwnedPetSucceeds() throws Exception {
		mockMvc
			.perform(get("/scheduling/requests/new").param("petId", String.valueOf(PET_ID)).with(user(ownerPrincipal)))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/requestNew"))
			.andExpect(model().attributeExists("pet"));
	}

	@Test
	void showNewRequestFormForUnownedPetReturnsNotFound() throws Exception {
		mockMvc.perform(get("/scheduling/requests/new").param("petId", "999").with(user(ownerPrincipal)))
			.andExpect(status().isNotFound());
	}

	@Test
	void submitRequestSucceedsAndRedirects() throws Exception {
		given(schedulingRequestService.createRequest(eq(OWNER_ID), eq(PET_ID), anyString(), anyString(), eq("owner1")))
			.willReturn(request);

		mockMvc
			.perform(post("/scheduling/requests").with(user(ownerPrincipal))
				.with(csrf())
				.param("petId", String.valueOf(PET_ID))
				.param("originalText", "Checkup next week")
				.param("language", "en"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/requests/" + REQUEST_ID));
	}

	@Test
	void showRequestStatusForOwnedRequestSucceeds() throws Exception {
		given(availabilityViewService.getSymbolicWindows(REQUEST_ID))
			.willReturn(List.of(new AvailabilityWindowViewDto(0, "ALLOWED", "RELATIVE_WEEK", "weekOffset=1, THURSDAY",
					"AFTERNOON, 12:00-17:00")));
		given(availabilityViewService.getResolvedIntervals(REQUEST_ID))
			.willReturn(List.of(new AvailabilityIntervalViewDto("ALLOWED", "2026-09-03 12:00 Europe/Tallinn",
					"2026-09-03 17:00 Europe/Tallinn")));

		mockMvc.perform(get("/scheduling/requests/{id}", REQUEST_ID).with(user(ownerPrincipal)))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/requestStatus"))
			.andExpect(model().attributeExists("request", "availabilityWindows", "availabilityIntervals"))
			.andExpect(content().string(containsString("RELATIVE_WEEK")))
			.andExpect(content().string(containsString("weekOffset=1, THURSDAY")))
			.andExpect(content().string(containsString("2026-09-03 12:00 Europe/Tallinn")));
	}

	@Test
	void localizesOutsideHorizonClarification() throws Exception {
		request.setStatus(RequestStatus.CLARIFICATION_REQUIRED);
		request.setLastClarificationReason("OUTSIDE_BOOKING_HORIZON");

		mockMvc.perform(get("/scheduling/requests/{id}", REQUEST_ID).param("lang", "es").with(user(ownerPrincipal)))
			.andExpect(status().isOk())
			.andExpect(content()
				.string(containsString("El período solicitado está fuera del horizonte de reservas actual.")));
	}

	@Test
	void showRequestStatusForUnownedRequestReturnsNotFound() throws Exception {
		mockMvc.perform(get("/scheduling/requests/{id}", 999).with(user(ownerPrincipal)))
			.andExpect(status().isNotFound());
	}

	@Test
	void submitConsentTrueDispatchesAIAndRedirects() throws Exception {
		given(schedulingRequestService.recordConsentAndDispatchAI(eq(REQUEST_ID), eq(OWNER_ID), eq(true), eq("owner1")))
			.willReturn("tok-123");

		mockMvc
			.perform(post("/scheduling/requests/{id}/consent", REQUEST_ID).with(user(ownerPrincipal))
				.with(csrf())
				.param("consent", "true"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/requests/" + REQUEST_ID));

		verify(aiInterpretationService).asyncInterpret("tok-123", REQUEST_ID);
	}

	@Test
	void getStatusJsonReturnsStatusDetails() throws Exception {
		mockMvc.perform(get("/scheduling/requests/{id}/status", REQUEST_ID).with(user(ownerPrincipal)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(REQUEST_ID))
			.andExpect(jsonPath("$.status").value("AWAITING_CONSENT"));
	}

	@Test
	void confirmRequestCallsServiceAndRedirects() throws Exception {
		mockMvc
			.perform(post("/scheduling/requests/{id}/confirm", REQUEST_ID).with(user(ownerPrincipal))
				.with(csrf())
				.param("confirmUnrestricted", "false"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/requests/" + REQUEST_ID));

		verify(schedulingRequestService).confirmInterpretation(eq(REQUEST_ID), eq(OWNER_ID), eq(false), eq("owner1"));
	}

	@Test
	void disputeRequestCallsServiceAndRedirects() throws Exception {
		mockMvc
			.perform(post("/scheduling/requests/{id}/dispute", REQUEST_ID).with(user(ownerPrincipal))
				.with(csrf())
				.param("reason", "Wrong duration"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/requests/" + REQUEST_ID));

		verify(schedulingRequestService).disputeClinicalFacts(eq(REQUEST_ID), eq(OWNER_ID), eq("Wrong duration"),
				eq("owner1"));
	}

	@Test
	void replaceTextCallsServiceAndRedirects() throws Exception {
		mockMvc
			.perform(post("/scheduling/requests/{id}/replace-text", REQUEST_ID).with(user(ownerPrincipal))
				.with(csrf())
				.param("newText", "Updated reason for visit"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/requests/" + REQUEST_ID));

		verify(schedulingRequestService).replaceOriginalText(eq(REQUEST_ID), eq(OWNER_ID),
				eq("Updated reason for visit"), eq("owner1"));
	}

	@Test
	void cancelRequestCallsServiceAndRedirects() throws Exception {
		mockMvc.perform(post("/scheduling/requests/{id}/cancel", REQUEST_ID).with(user(ownerPrincipal)).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/requests/" + REQUEST_ID));

		verify(schedulingRequestService).cancelRequest(eq(REQUEST_ID), eq(OWNER_ID), eq("owner1"));
	}

	@Test
	void askAgainCallsMatchingCoordinatorAndRedirects() throws Exception {
		mockMvc.perform(post("/scheduling/requests/{id}/ask-again", REQUEST_ID).with(user(ownerPrincipal)).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/requests/" + REQUEST_ID));

		verify(matchingCoordinator).dispatchMatching(eq(REQUEST_ID), eq("owner1"));
	}

	@Test
	void acceptSuggestionCallsReservationServiceAndRedirects() throws Exception {
		mockMvc
			.perform(post("/scheduling/requests/{id}/accept-suggestion", REQUEST_ID).with(user(ownerPrincipal))
				.param("reservationId", "50")
				.param("requestVersion", "1")
				.with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/requests/" + REQUEST_ID));

		verify(reservationService).acceptGuidedHold(eq(REQUEST_ID), eq(OWNER_ID), eq(50), eq(1L), eq("owner1"));
	}

	@Test
	void rejectSuggestionCallsReservationServiceAndRedirects() throws Exception {
		mockMvc
			.perform(post("/scheduling/requests/{id}/reject-suggestion", REQUEST_ID).with(user(ownerPrincipal))
				.param("reservationId", "50")
				.param("requestVersion", "1")
				.with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/requests/" + REQUEST_ID));

		verify(reservationService).rejectGuidedHold(eq(REQUEST_ID), eq(OWNER_ID), eq(50), eq(1L), eq("owner1"));
	}

	@Test
	void acceptOfferCallsReservationServiceAndRedirects() throws Exception {
		mockMvc
			.perform(post("/scheduling/requests/{id}/accept-offer", REQUEST_ID).with(user(ownerPrincipal))
				.param("reservationId", "50")
				.param("requestVersion", "1")
				.with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/requests/" + REQUEST_ID));

		verify(reservationService).acceptGuidedHold(eq(REQUEST_ID), eq(OWNER_ID), eq(50), eq(1L), eq("owner1"));
	}

	@Test
	void rejectOfferCallsReservationServiceAndRedirects() throws Exception {
		mockMvc
			.perform(post("/scheduling/requests/{id}/reject-offer", REQUEST_ID).with(user(ownerPrincipal))
				.param("reservationId", "50")
				.param("requestVersion", "1")
				.with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/requests/" + REQUEST_ID));

		verify(reservationService).rejectGuidedHold(eq(REQUEST_ID), eq(OWNER_ID), eq(50), eq(1L), eq("owner1"));
	}

}
