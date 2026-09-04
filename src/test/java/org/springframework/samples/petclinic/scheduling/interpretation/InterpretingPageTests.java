/* Copyright 2012-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.TestClockConfig;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEventRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
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

@SpringBootTest(properties = "scheduling.interpretation.executor=threaded")
@Import({ TestClockConfig.class, InterpretingPageTests.LatchedInterpreterConfiguration.class })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InterpretingPageTests {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private SchedulingRequestEventRepository eventRepository;

	@Autowired
	private InterpretationRepository interpretationRepository;

	@Autowired
	private LatchedRequestInterpreter interpreter;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
	}

	@Test
	@Tag("AC-27")
	@Tag("AC-28")
	void interpretingPageRendersMetaRefreshWhileJobIsBlocked_AC27_AC28() throws Exception {
		StartedRequest started = startRequest("Waiting request", "Friday");
		try {
			this.mockMvc.perform(post(started.location() + "/consent").session(started.session()).with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl(started.location()));
			assertThat(this.interpreter.awaitEntered()).isTrue();
			assertThat(reload(started.id()).getState()).isEqualTo(RequestState.INTERPRETING);

			this.mockMvc.perform(get(started.location()).session(started.session()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("<meta http-equiv=\"refresh\" content=\"3\">")))
				.andExpect(content().string(containsString("Interpreting your request")))
				.andExpect(content().string(not(containsString("application/json"))));

			long eventsBefore = this.eventRepository.findByRequestIdOrderByTimestampAsc(started.id()).size();
			this.mockMvc.perform(get(started.location() + "/edit").session(started.session()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl(started.location()));
			this.mockMvc
				.perform(post(started.location() + "/edit").session(started.session())
					.with(csrf())
					.param("reasonText", "Forbidden edit")
					.param("availabilityText", "Forbidden availability"))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("actionNotAllowed", true));
			this.mockMvc.perform(post(started.location() + "/route-to-staff").session(started.session()).with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("actionNotAllowed", true));
			assertThat(reload(started.id()).getState()).isEqualTo(RequestState.INTERPRETING);
			assertThat(reload(started.id()).getReasonText()).isEqualTo("Waiting request");
			assertThat(this.eventRepository.findByRequestIdOrderByTimestampAsc(started.id()))
				.hasSize((int) eventsBefore);

			this.mockMvc.perform(post(started.location() + "/abandon").session(started.session()).with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl(started.location()));
			assertThat(reload(started.id()).getState()).isEqualTo(RequestState.ABANDONED);
		}
		finally {
			this.interpreter.release();
		}
		awaitState(started.id(), RequestState.ABANDONED);
		assertThat(this.interpretationRepository.findByRequestIdOrderByVersionDesc(started.id())).isEmpty();
	}

	@Test
	@Tag("AC-32")
	void releasedJobMovesRequestToInterpreted_AC32() throws Exception {
		StartedRequest started = startRequest("Release request", "Tuesday afternoon");
		try {
			this.mockMvc.perform(post(started.location() + "/consent").session(started.session()).with(csrf()))
				.andExpect(status().is3xxRedirection());
			assertThat(this.interpreter.awaitEntered()).isTrue();
			assertThat(reload(started.id()).getState()).isEqualTo(RequestState.INTERPRETING);
		}
		finally {
			this.interpreter.release();
		}

		awaitState(started.id(), RequestState.INTERPRETED);
		this.mockMvc.perform(get(started.location()).session(started.session()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Latched review: Release request")))
			.andExpect(content().string(containsString("Confirm interpretation")));
		assertThat(this.interpretationRepository.findByRequestIdOrderByVersionDesc(started.id())).singleElement();
	}

	private StartedRequest startRequest(String reason, String availability) throws Exception {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().stream().findFirst().orElseThrow();
		MockHttpSession session = login();
		MvcResult created = this.mockMvc
			.perform(post("/my/requests").session(session)
				.with(csrf())
				.param("petId", pet.getId().toString())
				.param("reasonText", reason)
				.param("availabilityText", availability))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		String location = created.getResponse().getRedirectedUrl();
		assertThat(location).startsWith("/my/requests/");
		return new StartedRequest(session, Integer.valueOf(location.substring(location.lastIndexOf('/') + 1)),
				location);
	}

	private MockHttpSession login() throws Exception {
		MvcResult result = this.mockMvc.perform(formLogin().user("george").password("george123"))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private SchedulingRequest reload(Integer requestId) {
		return this.requestRepository.findById(requestId).orElseThrow();
	}

	private void awaitState(Integer requestId, RequestState expected) throws InterruptedException {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (System.nanoTime() < deadline) {
			if (reload(requestId).getState() == expected) {
				return;
			}
			Thread.sleep(20);
		}
		assertThat(reload(requestId).getState()).isEqualTo(expected);
	}

	private record StartedRequest(MockHttpSession session, Integer id, String location) {
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class LatchedInterpreterConfiguration {

		@Bean
		@Primary
		LatchedRequestInterpreter latchedRequestInterpreter() {
			return new LatchedRequestInterpreter();
		}

	}

}
