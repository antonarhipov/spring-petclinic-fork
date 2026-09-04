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

package org.springframework.samples.petclinic.scheduling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.interpretation.Interpretation;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP-level primary UC-1 lifecycle through the real filter chain (RULE-44, AC-138). */
@SpringBootTest
@Import(TestClockConfig.class)
@Transactional
class SchedulingLifecycleE2eTests {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private InterpretationRepository interpretationRepository;

	@Autowired
	private AppointmentRepository appointmentRepository;

	private MockMvc mockMvc;

	private Pet pet;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		this.pet = owner.getPets()
			.stream()
			.filter(candidate -> this.requestRepository.findByActivePetId(candidate.getId()).isEmpty())
			.findFirst()
			.orElseThrow();
	}

	@Test
	void ownerGuidedFlowMainScenario() throws Exception {
		MvcResult login = this.mockMvc.perform(formLogin().user("george").password("george123"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/my/appointments"))
			.andReturn();
		MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
		assertThat(session).isNotNull();

		this.mockMvc.perform(get("/my/requests/new").session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(this.pet.getName())))
			.andExpect(content().string(containsString("Call 555-0199.")));

		MvcResult created = this.mockMvc
			.perform(post("/my/requests").session(session)
				.with(csrf())
				.param("petId", this.pet.getId().toString())
				.param("reasonText", "Annual wellness checkup and vaccinations")
				.param("availabilityText", "Monday morning preferred"))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		String requestLocation = created.getResponse().getRedirectedUrl();
		assertThat(requestLocation).startsWith("/my/requests/");
		Integer requestId = Integer.valueOf(requestLocation.substring(requestLocation.lastIndexOf('/') + 1));
		SchedulingRequest request = this.requestRepository.findById(requestId).orElseThrow();
		assertThat(request.getState()).isEqualTo(RequestState.AWAITING_CONSENT);
		assertThat(request.getActivePetId()).isEqualTo(this.pet.getId());

		this.mockMvc.perform(get(requestLocation).session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Annual wellness checkup")))
			.andExpect(content().string(containsString("Grant consent")));

		this.mockMvc.perform(post(requestLocation + "/consent").session(session).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl(requestLocation));
		request = this.requestRepository.findById(requestId).orElseThrow();
		assertThat(request.getState()).isEqualTo(RequestState.INTERPRETED);
		Interpretation interpretation = this.interpretationRepository.findTopByRequestIdOrderByVersionDesc(requestId)
			.orElseThrow();
		assertThat(interpretation.getReasonSummary()).isEqualTo("Annual wellness checkup and vaccinations");

		this.mockMvc.perform(get(requestLocation).session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Interpretation")))
			.andExpect(content().string(containsString("Confirm interpretation")));

		this.mockMvc.perform(post(requestLocation + "/confirm").session(session).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl(requestLocation));
		request = this.requestRepository.findById(requestId).orElseThrow();
		assertThat(request.getState()).isEqualTo(RequestState.SUGGESTION_OFFERED);
		assertThat(request.hasHold()).isTrue();
		assertThat(request.getHeldVet()).isNotNull();
		assertThat(request.getHeldStart()).isNotNull();

		this.mockMvc.perform(get(requestLocation).session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Suggested appointment")))
			.andExpect(content().string(containsString("Accept suggestion")));

		this.mockMvc.perform(post(requestLocation + "/accept").session(session).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/my/appointments"));

		request = this.requestRepository.findById(requestId).orElseThrow();
		assertThat(request.getState()).isEqualTo(RequestState.ACCEPTED);
		assertThat(request.getActivePetId()).isNull();
		Appointment appointment = this.appointmentRepository.findByPetId(this.pet.getId())
			.stream()
			.filter(candidate -> candidate.getStatus() == AppointmentStatus.CONFIRMED)
			.findFirst()
			.orElseThrow();
		this.mockMvc.perform(get("/my/appointments").session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(this.pet.getName())))
			.andExpect(content().string(containsString(appointment.getVet().getLastName())))
			.andExpect(content().string(containsString("CONFIRMED")));
	}

}
