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

package org.springframework.samples.petclinic.scheduling.web;

import java.time.ZonedDateTime;
import java.util.List;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.interpretation.Interpretation;
import org.springframework.samples.petclinic.scheduling.interpretation.RequestInterpretationService;
import org.springframework.samples.petclinic.scheduling.request.RequestLifecycleService;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEventRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.solver.SlotRanker;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class OwnerRequestContractTests {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private RequestLifecycleService lifecycleService;

	@Autowired
	private RequestInterpretationService interpretationService;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private SchedulingRequestEventRepository eventRepository;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private VetRepository vetRepository;

	@Autowired
	private EntityManager entityManager;

	@MockitoBean
	private SlotRanker slotRanker;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
	}

	@Test
	void newRequestOmitsOnlyPetsWithActiveRequests_AC21() throws Exception {
		Owner jean = this.ownerRepository.findById(6).orElseThrow();
		Pet active = jean.getPets().stream().filter(pet -> pet.getName().equals("Samantha")).findFirst().orElseThrow();
		this.lifecycleService.createRequest(jean, active, "Routine check", "Tuesday afternoon", "jean");

		this.mockMvc.perform(get("/my/requests/new").session(login("jean", "jean123")))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString(">Samantha</option>"))))
			.andExpect(content().string(containsString(">Max</option>")));
	}

	@Test
	void confirmationInvokesRankerAndHoldsItsFirstTuple_AC58() throws Exception {
		SchedulingRequest request = interpretedRequest();
		Vet firstVet = this.vetRepository.findById(1).orElseThrow();
		Vet secondVet = this.vetRepository.findById(2).orElseThrow();
		ZonedDateTime firstStart = ZonedDateTime.parse("2026-09-08T09:00:00+02:00[Europe/Amsterdam]");
		ZonedDateTime secondStart = firstStart.plusHours(1);
		SlotRanker.RankedSlot first = new SlotRanker.RankedSlot(firstVet, firstStart, 30, "first", "10");
		SlotRanker.RankedSlot second = new SlotRanker.RankedSlot(secondVet, secondStart, 45, "second", "9");
		when(this.slotRanker.rankSlots(any(SchedulingRequest.class), any(Interpretation.class)))
			.thenReturn(List.of(first, second));

		this.mockMvc
			.perform(post("/my/requests/{id}/confirm", request.getId()).session(login("george", "george123"))
				.with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/my/requests/" + request.getId()));

		this.entityManager.flush();
		this.entityManager.clear();
		SchedulingRequest held = this.requestRepository.findById(request.getId()).orElseThrow();
		assertThat(held.getState()).isEqualTo(RequestState.SUGGESTION_OFFERED);
		assertThat(held.getHeldVet().getId()).isEqualTo(firstVet.getId());
		assertThat(held.getHeldStart()).isEqualTo(firstStart);
		assertThat(held.getHeldDuration()).isEqualTo(30);
		verify(this.slotRanker).rankSlots(any(SchedulingRequest.class), any(Interpretation.class));
	}

	@Test
	void everyOwnerActionRefusalRedirectsWithoutMutation_AC123() throws Exception {
		SchedulingRequest request = createRequest(1);
		this.lifecycleService.abandon(request, "george", "test terminal state");
		when(this.slotRanker.rankSlots(any(), any())).thenReturn(List.of());
		long events = this.eventRepository.count();
		long appointments = this.appointmentRepository.count();
		long requests = this.requestRepository.count();
		MockHttpSession owner = login("george", "george123");

		for (String action : List.of("consent", "decline", "confirm", "accept")) {
			this.mockMvc.perform(post("/my/requests/{id}/" + action, request.getId()).session(owner).with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/my/requests/" + request.getId()))
				.andExpect(flash().attribute(OwnerRequestController.ACTION_NOT_ALLOWED, true));
			this.entityManager.clear();
			assertThat(this.requestRepository.findById(request.getId()).orElseThrow().getState())
				.isEqualTo(RequestState.ABANDONED);
			assertThat(this.eventRepository.count()).isEqualTo(events);
			assertThat(this.appointmentRepository.count()).isEqualTo(appointments);
			assertThat(this.requestRepository.count()).isEqualTo(requests);
		}
	}

	private SchedulingRequest interpretedRequest() {
		SchedulingRequest request = createRequest(1);
		this.lifecycleService.consent(request, "george");
		this.interpretationService.interpret(request, "george");
		return request;
	}

	private SchedulingRequest createRequest(int ownerId) {
		Owner owner = this.ownerRepository.findById(ownerId).orElseThrow();
		Pet pet = owner.getPets().stream().findFirst().orElseThrow();
		return this.lifecycleService.createRequest(owner, pet, "Routine check", "Tuesday afternoon",
				owner.getFirstName());
	}

	private MockHttpSession login(String username, String password) throws Exception {
		MvcResult result = this.mockMvc.perform(formLogin().user(username).password(password))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

}
