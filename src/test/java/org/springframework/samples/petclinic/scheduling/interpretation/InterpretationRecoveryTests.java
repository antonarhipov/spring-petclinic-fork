/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 */

package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.request.RequestLifecycleService;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEvent;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEventRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "scheduling.interpretation.executor=synchronous")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InterpretationRecoveryTests {

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
	private RequestLifecycleService lifecycleService;

	@Autowired
	private RequestInterpretationService interpretationService;

	@Autowired
	private Clock clock;

	@Autowired
	private InterpretationRecoveryRunner recoveryRunner;

	@MockitoBean
	private RequestInterpreter interpreter;

	@MockitoSpyBean
	private AsyncInterpretationService asyncInterpretationService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
		reset(this.interpreter, this.asyncInterpretationService);
	}

	@Test
	@Tag("AC-25")
	void declineRoutesToStaffWithoutDispatch_AC25() throws Exception {
		SchedulingRequest request = createRequest(1, 0, "No AI", "Anytime");

		this.mockMvc.perform(post("/my/requests/{id}/decline", request.getId()).session(login()).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/my/requests/" + request.getId()));

		assertThat(reload(request).getState()).isEqualTo(RequestState.WITH_STAFF);
		verify(this.interpreter, never()).interpret(anyString(), anyString());
		verify(this.asyncInterpretationService, never()).dispatch(anyInt(), anyString());
	}

	@Test
	@Tag("AC-29")
	void lateResultForAbandonedRequestIsDiscarded_AC29() {
		SchedulingRequest request = interpretingRequest(1, 0, "Late", "Friday");
		this.lifecycleService.abandon(reload(request), "george", "owner abandoned");
		SchedulingRequest abandoned = reload(request);
		var updatedAt = abandoned.getUpdatedAt();
		int failedAttempts = abandoned.getFailedAttempts();
		long interpretationCount = this.interpretationRepository.count();
		List<SchedulingRequestEvent> events = events(request);

		assertThat(this.interpretationService.applyResult(request.getId(), usableResult(), "system")).isEmpty();

		SchedulingRequest unchanged = reload(request);
		assertThat(unchanged.getState()).isEqualTo(RequestState.ABANDONED);
		assertThat(unchanged.getUpdatedAt()).isEqualTo(updatedAt);
		assertThat(unchanged.getFailedAttempts()).isEqualTo(failedAttempts);
		assertThat(this.interpretationRepository.count()).isEqualTo(interpretationCount);
		assertThat(events(request)).usingRecursiveComparison().isEqualTo(events);
	}

	@Test
	@Tag("AC-30")
	void startupMarksEveryInFlightRequestInterrupted_AC30() throws Exception {
		SchedulingRequest first = interpretingRequest(1, 0, "First", "Monday");
		SchedulingRequest second = interpretingRequest(2, 0, "Second", "Tuesday");

		this.recoveryRunner.run(null);

		for (SchedulingRequest request : List.of(first, second)) {
			assertThat(reload(request).getState()).isEqualTo(RequestState.INTERPRETATION_FAILED);
			SchedulingRequestEvent lastEvent = events(request).getLast();
			assertThat(lastEvent.getReason()).isEqualTo("interrupted");
			assertThat(lastEvent.getActor()).isEqualTo("system");
			assertThat(lastEvent.getFromState()).isEqualTo(RequestState.INTERPRETING);
			assertThat(lastEvent.getToState()).isEqualTo(RequestState.INTERPRETATION_FAILED);
		}
		assertThat(this.requestRepository.findByState(RequestState.INTERPRETING)).isEmpty();
	}

	@Test
	@Tag("AC-32")
	void usableResultMovesToInterpreted_AC32() {
		SchedulingRequest request = interpretingRequest(1, 0, "Usable", "Anytime");

		assertThat(this.interpretationService.applyResult(request.getId(), usableResult(), "system")).isPresent();

		assertThat(reload(request).getState()).isEqualTo(RequestState.INTERPRETED);
		assertThat(this.interpretationRepository.findByRequestIdOrderByVersionDesc(request.getId())).singleElement()
			.extracting(Interpretation::getReasonSummary)
			.isEqualTo("Usable interpretation");
	}

	@Test
	@Tag("AC-33")
	void contradictionAndCannotInterpretCountFailure_AC33() {
		SchedulingRequest contradictory = interpretingRequest(1, 0, "Contradiction", "Monday but not Monday");
		SchedulingRequest cannotInterpret = interpretingRequest(2, 0, "Unclear", "Unclear");
		LocalDate date = LocalDate.now(this.clock).plusDays(1);
		AvailabilityWindow allowed = AvailabilityWindow.preferred(date, LocalTime.of(9, 0), LocalTime.of(10, 0), null);
		AvailabilityWindow excluded = AvailabilityWindow.excluded(date, LocalTime.of(9, 0), LocalTime.of(10, 0), null);
		InterpretationResult contradiction = new InterpretationResult("Contradictory", 30, CareType.GENERAL, null, null,
				false, List.of(allowed, excluded), "{}", "test-model", "v1");

		this.interpretationService.applyResult(contradictory.getId(), contradiction, "system");
		this.interpretationService.applyResult(cannotInterpret.getId(),
				InterpretationResult.cannotInterpret("{}", "test-model", "v1"), "system");

		for (SchedulingRequest request : List.of(contradictory, cannotInterpret)) {
			SchedulingRequest failed = reload(request);
			assertThat(failed.getState()).isEqualTo(RequestState.INTERPRETATION_FAILED);
			assertThat(failed.getFailedAttempts()).isEqualTo(1);
			assertThat(this.interpretationRepository.findByRequestIdOrderByVersionDesc(request.getId())).hasSize(1);
		}
	}

	private SchedulingRequest interpretingRequest(int ownerId, int petIndex, String reason, String availability) {
		SchedulingRequest request = createRequest(ownerId, petIndex, reason, availability);
		return this.lifecycleService.consent(request, ownerId == 1 ? "george" : "betty");
	}

	private SchedulingRequest createRequest(int ownerId, int petIndex, String reason, String availability) {
		Owner owner = this.ownerRepository.findById(ownerId).orElseThrow();
		Pet pet = owner.getPets().stream().toList().get(petIndex);
		String actor = ownerId == 1 ? "george" : "betty";
		return this.lifecycleService.createRequest(owner, pet, reason, availability, actor);
	}

	private SchedulingRequest reload(SchedulingRequest request) {
		return this.requestRepository.findById(request.getId()).orElseThrow();
	}

	private List<SchedulingRequestEvent> events(SchedulingRequest request) {
		return this.eventRepository.findByRequestIdOrderByTimestampAsc(request.getId());
	}

	private MockHttpSession login() throws Exception {
		MvcResult result = this.mockMvc.perform(formLogin().user("george").password("george123"))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private static InterpretationResult usableResult() {
		return new InterpretationResult("Usable interpretation", 30, CareType.GENERAL, null, null, false,
				Collections.emptyList(), "{}", "test-model", "v1");
	}

}
