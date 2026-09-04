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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.interpretation.RequestInterpretationService;
import org.springframework.samples.petclinic.scheduling.request.IllegalRequestTransitionException;
import org.springframework.samples.petclinic.scheduling.request.RequestLifecycleService;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class SchedulingErrorHandlingTests {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockitoBean
	private RequestLifecycleService lifecycleService;

	@MockitoBean
	private RequestInterpretationService interpretationService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
	}

	@Test
	void oupsResolvesForStaffOnly_AC8() throws Exception {
		MockHttpSession staff = login("staff", "staff123");
		this.mockMvc.perform(get("/oups").session(staff))
			.andExpect(status().isInternalServerError())
			.andExpect(content().string(containsString("An internal server error occurred.")));

		MockHttpSession owner = login("george", "george123");
		this.mockMvc.perform(get("/oups").session(owner))
			.andExpect(status().isForbidden())
			.andExpect(forwardedUrl("/403"));
	}

	@Test
	void illegalLifecycleActionRedirectsWithKeyedNotice_AC123() throws Exception {
		SchedulingRequest request = ownedRequest();
		long rowsBefore = this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scheduling_request", Long.class);
		long eventsBefore = this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scheduling_request_event",
				Long.class);
		doThrow(new IllegalRequestTransitionException(RequestState.ACCEPTED, "consent")).when(this.lifecycleService)
			.consent(any(SchedulingRequest.class), anyString());

		this.mockMvc
			.perform(post("/my/requests/{id}/consent", request.getId()).session(login("george", "george123"))
				.with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/my/requests/" + request.getId()))
			.andExpect(flash().attribute(OwnerRequestController.ACTION_NOT_ALLOWED, true));

		assertThat(this.requestRepository.findById(request.getId()).orElseThrow().getState())
			.isEqualTo(RequestState.ACCEPTED);
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scheduling_request", Long.class))
			.isEqualTo(rowsBefore);
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scheduling_request_event", Long.class))
			.isEqualTo(eventsBefore);
		verify(this.interpretationService, never()).interpret(any(), anyString());
	}

	@Test
	void unexpectedIllegalStateIsNotSwallowed() throws Exception {
		SchedulingRequest request = ownedRequest();
		doThrow(new IllegalStateException("unexpected failure")).when(this.lifecycleService)
			.consent(any(SchedulingRequest.class), anyString());

		MockHttpSession owner = login("george", "george123");
		assertThatThrownBy(() -> this.mockMvc
			.perform(post("/my/requests/{id}/consent", request.getId()).session(owner).with(csrf())))
			.hasRootCauseInstanceOf(IllegalStateException.class)
			.hasRootCauseMessage("unexpected failure");
		verify(this.interpretationService, never()).interpret(any(), anyString());
	}

	private SchedulingRequest ownedRequest() {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().stream().findFirst().orElseThrow();
		ZonedDateTime now = ZonedDateTime.parse("2026-09-07T09:00:00+02:00[Europe/Amsterdam]");
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(owner);
		request.setPet(pet);
		request.setState(RequestState.ACCEPTED);
		request.setReasonText("Routine check");
		request.setAvailabilityText("Tuesday afternoon");
		request.setCreatedAt(now);
		request.setUpdatedAt(now);
		return this.requestRepository.saveAndFlush(request);
	}

	private MockHttpSession login(String username, String password) throws Exception {
		MvcResult result = this.mockMvc.perform(formLogin().user(username).password(password))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

}
