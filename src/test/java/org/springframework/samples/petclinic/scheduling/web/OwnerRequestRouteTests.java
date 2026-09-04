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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.interpretation.Interpretation;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationRepository;
import org.springframework.samples.petclinic.scheduling.interpretation.RequestInterpretationService;
import org.springframework.samples.petclinic.scheduling.request.RequestLifecycleService;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEvent;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEventRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
class OwnerRequestRouteTests {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private RequestLifecycleService lifecycleService;

	@Autowired
	private RequestInterpretationService interpretationService;

	@Autowired
	private InterpretationRepository interpretationRepository;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private SchedulingRequestEventRepository eventRepository;

	@Autowired
	private VetRepository vetRepository;

	@Autowired
	private EntityManager entityManager;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
	}

	@Test
	@Tag("AC-10")
	void everyOwnerActionRouteResolvesAndUsesSessionOwner_AC10() throws Exception {
		SchedulingRequest own = createRequest(1, "George route check", "Tuesday morning");
		SchedulingRequest otherOwner = createRequest(2, "Betty protected reason", "Wednesday afternoon");
		MockHttpSession george = login("george", "george123");

		this.mockMvc.perform(get("/my/requests/{id}/edit", own.getId()).session(george))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("George route check")));

		for (String action : List.of("edit", "abandon", "route-to-staff", "another")) {
			this.mockMvc.perform(post("/my/requests/{id}/" + action, own.getId()).session(george))
				.andExpect(status().isForbidden());
		}

		this.mockMvc
			.perform(post("/my/requests/{id}/edit", own.getId()).session(george)
				.with(csrf())
				.param("reasonText", "George edited reason")
				.param("availabilityText", "Friday morning"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/my/requests/" + own.getId()));
		this.mockMvc.perform(post("/my/requests/{id}/route-to-staff", own.getId()).session(george).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute(OwnerRequestController.ACTION_NOT_ALLOWED, true));
		this.mockMvc.perform(post("/my/requests/{id}/another", own.getId()).session(george).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute(OwnerRequestController.ACTION_NOT_ALLOWED, true));
		this.mockMvc.perform(post("/my/requests/{id}/abandon", own.getId()).session(george).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute(OwnerRequestActionController.REQUEST_ABANDONED, true));

		long protectedEvents = this.eventRepository.findByRequestIdOrderByTimestampAsc(otherOwner.getId()).size();
		this.mockMvc.perform(get("/my/requests/{id}/edit", otherOwner.getId()).session(george))
			.andExpect(status().isNotFound())
			.andExpect(content().string(not(containsString("Betty protected reason"))));
		for (String action : List.of("edit", "abandon", "route-to-staff", "another")) {
			this.mockMvc.perform(post("/my/requests/{id}/" + action, otherOwner.getId()).session(george).with(csrf()))
				.andExpect(status().isNotFound())
				.andExpect(content().string(not(containsString("Betty protected reason"))));
		}
		this.entityManager.clear();
		SchedulingRequest protectedRequest = this.requestRepository.findById(otherOwner.getId()).orElseThrow();
		assertThat(protectedRequest.getState()).isEqualTo(RequestState.AWAITING_CONSENT);
		assertThat(protectedRequest.getReasonText()).isEqualTo("Betty protected reason");
		assertThat(this.eventRepository.findByRequestIdOrderByTimestampAsc(otherOwner.getId()))
			.hasSize((int) protectedEvents);
	}

	@Test
	@Tag("AC-54")
	void editRequiresFreshConsent_AC54() throws Exception {
		SchedulingRequest request = createRequest(1, "Annual examination", "Tuesday afternoon");
		this.lifecycleService.consent(request, "george");
		Interpretation first = this.interpretationService.interpret(request, "george");
		String persistedReview = first.getReasonSummary();
		MockHttpSession george = login("george", "george123");

		this.mockMvc.perform(get("/my/requests/{id}", request.getId()).session(george))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(persistedReview)));

		this.mockMvc
			.perform(post("/my/requests/{id}/edit", request.getId()).session(george)
				.with(csrf())
				.param("reasonText", "New skin concern")
				.param("availabilityText", "Friday morning"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/my/requests/" + request.getId()))
			.andExpect(flash().attribute(OwnerRequestActionController.REQUEST_EDITED, true));

		this.entityManager.flush();
		this.entityManager.clear();
		SchedulingRequest edited = this.requestRepository.findById(request.getId()).orElseThrow();
		assertThat(edited.getState()).isEqualTo(RequestState.AWAITING_CONSENT);
		assertThat(edited.getReasonText()).isEqualTo("New skin concern");
		assertThat(edited.getAvailabilityText()).isEqualTo("Friday morning");
		assertThat(this.interpretationRepository.findByRequestIdOrderByVersionDesc(request.getId())).singleElement()
			.satisfies(saved -> {
				assertThat(saved.getVersion()).isEqualTo(1);
				assertThat(saved.getReasonSummary()).isEqualTo(persistedReview);
			});
		this.mockMvc.perform(get("/my/requests/{id}", request.getId()).session(george))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString(persistedReview))))
			.andExpect(content().string(containsString("Grant consent")));

		this.mockMvc.perform(post("/my/requests/{id}/consent", request.getId()).session(george).with(csrf()))
			.andExpect(status().is3xxRedirection());
		this.entityManager.flush();
		this.entityManager.clear();
		assertThat(this.requestRepository.findById(request.getId()).orElseThrow().getState())
			.isEqualTo(RequestState.INTERPRETED);
		assertThat(this.interpretationRepository.findByRequestIdOrderByVersionDesc(request.getId()))
			.extracting(Interpretation::getVersion)
			.containsExactly(2, 1);
	}

	@Test
	@Tag("AC-122")
	void routeToStaffTransitionsAndRecordsEvent_AC122() throws Exception {
		MockHttpSession george = login("george", "george123");
		Vet vet = this.vetRepository.findById(1).orElseThrow();
		for (RequestState origin : List.of(RequestState.INTERPRETATION_FAILED, RequestState.INTERPRETED,
				RequestState.SUGGESTION_OFFERED)) {
			SchedulingRequest request = requestInState(origin, vet);
			List<SchedulingRequestEvent> before = this.eventRepository
				.findByRequestIdOrderByTimestampAsc(request.getId());

			this.mockMvc
				.perform(post("/my/requests/{id}/route-to-staff", request.getId()).session(george)
					.with(csrf())
					.param("reason", "Please ask clinic staff"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/my/requests/" + request.getId()))
				.andExpect(flash().attribute(OwnerRequestActionController.REQUEST_ROUTED_TO_STAFF, true));

			this.entityManager.flush();
			this.entityManager.clear();
			SchedulingRequest routed = this.requestRepository.findById(request.getId()).orElseThrow();
			assertThat(routed.getState()).isEqualTo(RequestState.WITH_STAFF);
			assertThat(routed.hasHold()).isFalse();
			assertThat(this.eventRepository.findByRequestIdOrderByTimestampAsc(request.getId()))
				.hasSize(before.size() + 1)
				.last()
				.satisfies(event -> {
					assertThat(event.getFromState()).isEqualTo(origin);
					assertThat(event.getToState()).isEqualTo(RequestState.WITH_STAFF);
					assertThat(event.getActor()).isEqualTo("george");
					assertThat(event.getAction()).isEqualTo("route to staff");
					assertThat(event.getReason()).isEqualTo("Please ask clinic staff");
				});

			this.lifecycleService.abandon(routed, "george", "test cleanup");
			this.entityManager.flush();
			this.entityManager.clear();
		}
	}

	private SchedulingRequest requestInState(RequestState state, Vet vet) {
		SchedulingRequest request = createRequest(1, "Route " + state, "Tuesday afternoon");
		this.lifecycleService.consent(request, "george");
		if (state == RequestState.INTERPRETATION_FAILED) {
			this.lifecycleService.interpretationFailed(request, "system", "test failure");
		}
		else {
			this.lifecycleService.interpretationUsable(request, "system");
			if (state == RequestState.SUGGESTION_OFFERED) {
				this.lifecycleService.confirmFeasible(request, "george", vet,
						ZonedDateTime.parse("2026-09-08T09:00:00+02:00[Europe/Amsterdam]"), 30);
			}
		}
		return request;
	}

	private SchedulingRequest createRequest(int ownerId, String reason, String availability) {
		Owner owner = this.ownerRepository.findById(ownerId).orElseThrow();
		Pet pet = owner.getPets().stream().findFirst().orElseThrow();
		return this.lifecycleService.createRequest(owner, pet, reason, availability,
				owner.getFirstName().toLowerCase());
	}

	private MockHttpSession login(String username, String password) throws Exception {
		MvcResult result = this.mockMvc.perform(formLogin().user(username).password(password))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

}
