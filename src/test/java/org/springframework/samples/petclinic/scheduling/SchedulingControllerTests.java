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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.PetType;
import org.springframework.samples.petclinic.scheduling.ai.CareType;
import org.springframework.samples.petclinic.scheduling.ai.Interpretation;
import org.springframework.samples.petclinic.scheduling.ai.UrgencyLevel;
import org.springframework.samples.petclinic.scheduling.model.QueueReason;
import org.springframework.samples.petclinic.scheduling.model.RequestState;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequestRepository;
import org.springframework.samples.petclinic.security.UserAccountRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SchedulingController.class)
@Import(AsyncConfig.class)
@WithMockUser(roles = "STAFF")
class SchedulingControllerTests {

	private static final int TEST_OWNER_ID = 1;

	private static final int TEST_PET_ID = 1;

	private static final int TEST_REQUEST_ID = 10;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SchedulingRequestRepository schedulingRequests;

	@MockitoBean
	private OwnerRepository owners;

	@MockitoBean
	private SchedulingOrchestrator orchestrator;

	@MockitoBean
	private HoldService holdService;

	@MockitoBean
	private BookingService bookingService;

	@MockitoBean
	private UserAccountRepository userAccountRepository;

	private Owner owner;

	private Pet pet;

	@BeforeEach
	void setUp() {
		this.owner = new Owner();
		this.owner.setId(TEST_OWNER_ID);
		this.owner.setFirstName("George");
		this.owner.setLastName("Franklin");

		this.pet = new Pet();
		this.pet.setId(TEST_PET_ID);
		this.pet.setName("Leo");
		PetType dogType = new PetType();
		dogType.setName("dog");
		this.pet.setType(dogType);
		this.owner.getPets().add(this.pet);

		when(this.owners.findById(TEST_OWNER_ID)).thenReturn(Optional.of(this.owner));
	}

	@Test
	void shouldShowNewRequestFormWithUrgentCareBanner() throws Exception {
		when(this.schedulingRequests.findByActivePetKey(TEST_PET_ID)).thenReturn(Optional.empty());

		this.mockMvc.perform(get("/owners/{ownerId}/pets/{petId}/schedule/new", TEST_OWNER_ID, TEST_PET_ID))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/requestForm"))
			.andExpect(model().attributeExists("owner", "pet", "rawText", "aiConsent"))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Urgent &amp; Emergency Care Notice")))
			.andExpect(content()
				.string(org.hamcrest.Matchers.containsString("Consent to AI-assisted scheduling analysis")));
	}

	@Test
	void shouldRedirectToActiveRequestIfDuplicateAttempted() throws Exception {
		SchedulingRequest existing = new SchedulingRequest();
		existing.setId(TEST_REQUEST_ID);
		existing.setState(RequestState.INTERPRETING);
		when(this.schedulingRequests.findByActivePetKey(TEST_PET_ID)).thenReturn(Optional.of(existing));

		this.mockMvc.perform(get("/owners/{ownerId}/pets/{petId}/schedule/new", TEST_OWNER_ID, TEST_PET_ID))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/requests/" + TEST_REQUEST_ID));
	}

	@Test
	void shouldSubmitRequestWithConsentAndReturnImmediately() throws Exception {
		when(this.schedulingRequests.findByActivePetKey(TEST_PET_ID)).thenReturn(Optional.empty());

		SchedulingRequest saved = new SchedulingRequest();
		saved.setId(TEST_REQUEST_ID);
		saved.setOwner(this.owner);
		saved.setPet(this.pet);
		saved.setAiConsent(true);
		saved.setRawText("Ear infection checkup");
		when(this.schedulingRequests.saveAndFlush(any(SchedulingRequest.class))).thenReturn(saved);

		this.mockMvc
			.perform(post("/owners/{ownerId}/pets/{petId}/schedule/new", TEST_OWNER_ID, TEST_PET_ID).with(csrf())
				.param("rawText", "Ear infection checkup")
				.param("aiConsent", "true"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/requests/" + TEST_REQUEST_ID));

		verify(this.orchestrator).processInterpretationAsync(TEST_REQUEST_ID);
	}

	@Test
	void shouldQueueWhenConsentDeclinedWithoutInvokingOrchestrator() throws Exception {
		when(this.schedulingRequests.findByActivePetKey(TEST_PET_ID)).thenReturn(Optional.empty());

		SchedulingRequest saved = new SchedulingRequest();
		saved.setId(TEST_REQUEST_ID);
		saved.setOwner(this.owner);
		saved.setPet(this.pet);
		saved.setAiConsent(false);
		saved.setRawText("Vaccination");
		when(this.schedulingRequests.saveAndFlush(any(SchedulingRequest.class))).thenReturn(saved);

		this.mockMvc
			.perform(post("/owners/{ownerId}/pets/{petId}/schedule/new", TEST_OWNER_ID, TEST_PET_ID).with(csrf())
				.param("rawText", "Vaccination")
				.param("aiConsent", "false"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/requests/" + TEST_REQUEST_ID));

		assertThat(saved.getState()).isEqualTo(RequestState.STAFF_QUEUED);
		assertThat(saved.getQueueReason()).isEqualTo(QueueReason.CONSENT_DECLINED);
		verify(this.orchestrator, never()).processInterpretationAsync(anyInt());
	}

	@Test
	void shouldRenderInterpretingStateWithAutoRefreshAndUrgentBanner() throws Exception {
		SchedulingRequest request = new SchedulingRequest();
		request.setId(TEST_REQUEST_ID);
		request.setOwner(this.owner);
		request.setPet(this.pet);
		request.setState(RequestState.INTERPRETING);
		when(this.schedulingRequests.findById(TEST_REQUEST_ID)).thenReturn(Optional.of(request));

		this.mockMvc.perform(get("/scheduling/requests/{requestId}", TEST_REQUEST_ID))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/status"))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Urgent &amp; Emergency Care Notice")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Analyzing your request...")));
	}

	@Test
	void shouldRenderAwaitingConfirmationWithInterpretationDetails() throws Exception {
		SchedulingRequest request = new SchedulingRequest();
		request.setId(TEST_REQUEST_ID);
		request.setOwner(this.owner);
		request.setPet(this.pet);
		request.setState(RequestState.AWAITING_CONFIRMATION);
		request.setRawText("Ear infection");
		when(this.schedulingRequests.findById(TEST_REQUEST_ID)).thenReturn(Optional.of(request));

		Interpretation interpretation = new Interpretation("Ear infection exam", 30, CareType.GENERAL, null,
				UrgencyLevel.ROUTINE, java.util.List.of(), java.util.List.of(), java.util.List.of(), null, 0.9);
		when(this.orchestrator.getInterpretation(request)).thenReturn(Optional.of(interpretation));

		this.mockMvc.perform(get("/scheduling/requests/{requestId}", TEST_REQUEST_ID))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/status"))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Confirm Appointment Details")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Ear infection exam")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("30")));
	}

	@Test
	void shouldConfirmInterpretationAndTransitionToSuggesting() throws Exception {
		SchedulingRequest request = new SchedulingRequest();
		request.setId(TEST_REQUEST_ID);
		request.setState(RequestState.AWAITING_CONFIRMATION);
		when(this.schedulingRequests.findById(TEST_REQUEST_ID)).thenReturn(Optional.of(request));

		this.mockMvc.perform(post("/scheduling/requests/{requestId}/confirm", TEST_REQUEST_ID).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/requests/" + TEST_REQUEST_ID));

		assertThat(request.getState()).isEqualTo(RequestState.SUGGESTING);
		verify(this.schedulingRequests).saveAndFlush(request);
		verify(this.orchestrator).processSolveAsync(TEST_REQUEST_ID);
	}

	@Test
	void shouldEditTextAndResetToDraftWithReinterpretation() throws Exception {
		SchedulingRequest request = new SchedulingRequest();
		request.setId(TEST_REQUEST_ID);
		request.setState(RequestState.AWAITING_CONFIRMATION);
		request.setRawText("Original description");
		when(this.schedulingRequests.findById(TEST_REQUEST_ID)).thenReturn(Optional.of(request));

		this.mockMvc
			.perform(post("/scheduling/requests/{requestId}/edit-text", TEST_REQUEST_ID).with(csrf())
				.param("rawText", "Updated description with new details")
				.param("aiConsent", "true"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/requests/" + TEST_REQUEST_ID));

		assertThat(request.getState()).isEqualTo(RequestState.DRAFT);
		assertThat(request.getRawText()).isEqualTo("Updated description with new details");
		assertThat(request.isAiConsent()).isTrue();
		verify(this.orchestrator).processInterpretationAsync(TEST_REQUEST_ID);
	}

	@Test
	void shouldRenderStaffQueuedState() throws Exception {
		SchedulingRequest request = new SchedulingRequest();
		request.setId(TEST_REQUEST_ID);
		request.setOwner(this.owner);
		request.setPet(this.pet);
		request.setState(RequestState.STAFF_QUEUED);
		request.setQueueReason(QueueReason.CONSENT_DECLINED);
		when(this.schedulingRequests.findById(TEST_REQUEST_ID)).thenReturn(Optional.of(request));

		this.mockMvc.perform(get("/scheduling/requests/{requestId}", TEST_REQUEST_ID))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/status"))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Routed to Clinic Staff")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("CONSENT_DECLINED")));
	}

	@Test
	void shouldAcceptSlotAndRedirect() throws Exception {
		SchedulingRequest request = new SchedulingRequest();
		request.setId(TEST_REQUEST_ID);
		when(this.schedulingRequests.findById(TEST_REQUEST_ID)).thenReturn(Optional.of(request));

		this.mockMvc.perform(post("/scheduling/requests/{requestId}/accept", TEST_REQUEST_ID).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/requests/" + TEST_REQUEST_ID));

		verify(this.bookingService).acceptHold(TEST_REQUEST_ID);
	}

	@Test
	void shouldRejectSlotAndRedirect() throws Exception {
		org.springframework.samples.petclinic.scheduling.model.Hold hold = new org.springframework.samples.petclinic.scheduling.model.Hold();
		org.springframework.samples.petclinic.vet.Vet vet = new org.springframework.samples.petclinic.vet.Vet();
		vet.setId(1);
		hold.setVet(vet);
		hold.setStartTime(java.time.LocalDateTime.of(2026, 9, 1, 10, 0));
		when(this.holdService.getActiveHold(TEST_REQUEST_ID)).thenReturn(Optional.of(hold));

		SchedulingRequest request = new SchedulingRequest();
		request.setId(TEST_REQUEST_ID);
		request.setState(RequestState.SLOT_HELD);
		when(this.schedulingRequests.findById(TEST_REQUEST_ID)).thenReturn(Optional.of(request));

		this.mockMvc.perform(post("/scheduling/requests/{requestId}/reject", TEST_REQUEST_ID).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/requests/" + TEST_REQUEST_ID));

		verify(this.holdService).recordExclusion(request, hold.getVet(), hold.getStartTime());
		verify(this.holdService).releaseHold(hold);
		assertThat(request.getState()).isEqualTo(RequestState.SUGGESTING);
		verify(this.orchestrator).processSolveAsync(TEST_REQUEST_ID);
	}

	@Test
	void shouldCancelRequestAndReleaseHold() throws Exception {
		org.springframework.samples.petclinic.scheduling.model.Hold hold = new org.springframework.samples.petclinic.scheduling.model.Hold();
		when(this.holdService.getActiveHold(TEST_REQUEST_ID)).thenReturn(Optional.of(hold));

		SchedulingRequest request = new SchedulingRequest();
		request.setId(TEST_REQUEST_ID);
		request.setOwner(this.owner);
		request.setPet(this.pet);
		request.setState(RequestState.SLOT_HELD);
		when(this.schedulingRequests.findById(TEST_REQUEST_ID)).thenReturn(Optional.of(request));

		this.mockMvc.perform(post("/scheduling/requests/{requestId}/cancel", TEST_REQUEST_ID).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/requests/" + TEST_REQUEST_ID));

		verify(this.holdService).releaseHold(hold);
		assertThat(request.getState()).isEqualTo(RequestState.CANCELLED);
		assertThat(request.getActivePetKey()).isNull();
		verify(this.schedulingRequests).saveAndFlush(request);
	}

}
