/* Copyright 2012-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package org.springframework.samples.petclinic.scheduling.web;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.TestClockConfig;
import org.springframework.samples.petclinic.scheduling.interpretation.Interpretation;
import org.springframework.samples.petclinic.scheduling.interpretation.RequestInterpretationService;
import org.springframework.samples.petclinic.scheduling.request.RejectionScope;
import org.springframework.samples.petclinic.scheduling.request.RequestLifecycleService;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEvent;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEventRepository;
import org.springframework.samples.petclinic.scheduling.request.SuggestionRejection;
import org.springframework.samples.petclinic.scheduling.request.SuggestionService;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TestClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RuledOutPresentationTests {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private VetRepository vetRepository;

	@Autowired
	private RequestLifecycleService lifecycleService;

	@Autowired
	private RequestInterpretationService interpretationService;

	@Autowired
	private SchedulingRequestEventRepository eventRepository;

	@Autowired
	private SuggestionService suggestionService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
	}

	@Test
	@Tag("AC-71")
	void requestShowsCompactRuledOutListWithoutUndo_AC71() throws Exception {
		RequestFixture fixture = offeredRequest();
		SuggestionRejection time = new SuggestionRejection(fixture.interpretation.getVersion(), fixture.vet.getId(),
				fixture.start, RejectionScope.NOT_THIS_TIME);
		SuggestionRejection day = new SuggestionRejection(fixture.interpretation.getVersion(), fixture.vet.getId(),
				fixture.start.plusDays(1), RejectionScope.NOT_THIS_DAY);
		this.eventRepository.save(rejectionEvent(fixture.request, time));
		this.eventRepository.save(rejectionEvent(fixture.request, day));

		this.mockMvc.perform(get("/my/requests/{id}", fixture.request.getId()).session(login()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("You have ruled out:")))
			.andExpect(content().string(containsString("NOT_THIS_TIME: vet " + fixture.vet.getId())))
			.andExpect(content().string(containsString("NOT_THIS_DAY: vet " + fixture.vet.getId())))
			.andExpect(content().string(not(containsString("Undo"))))
			.andExpect(content().string(not(containsString("undo-rejection"))));
	}

	@Test
	@Tag("AC-72")
	void rejectionEventContainsVetTimeAndScope_AC72() {
		RequestFixture fixture = offeredRequest();

		this.suggestionService.requestAnotherOption(fixture.request, "george", RejectionScope.NOT_THIS_VET.name());

		List<SchedulingRequestEvent> rejectionEvents = this.eventRepository
			.findByRequestIdOrderByTimestampAsc(fixture.request.getId())
			.stream()
			.filter(event -> SuggestionRejection.EVENT_ACTION.equals(event.getAction()))
			.toList();
		assertThat(rejectionEvents).singleElement().satisfies(event -> {
			assertThat(event.getFromState()).isEqualTo(RequestState.SUGGESTION_OFFERED);
			assertThat(event.getToState()).isEqualTo(RequestState.SUGGESTION_OFFERED);
			assertThat(event.getActor()).isEqualTo("george");
			assertThat(event.getReason()).isEqualTo(RejectionScope.NOT_THIS_VET.name());
			assertThat(SuggestionRejection.fromEvent(event))
				.contains(new SuggestionRejection(fixture.interpretation.getVersion(), fixture.vet.getId(),
						fixture.start, RejectionScope.NOT_THIS_VET));
		});
	}

	private RequestFixture offeredRequest() {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().stream().findFirst().orElseThrow();
		SchedulingRequest request = this.lifecycleService.createRequest(owner, pet, "Annual check", "any weekday",
				"george");
		this.lifecycleService.consent(request, "george");
		Interpretation interpretation = this.interpretationService.interpret(request, "system");
		Vet vet = this.vetRepository.findById(1).orElseThrow();
		ZonedDateTime start = ZonedDateTime.parse("2026-09-08T10:00:00+02:00[Europe/Amsterdam]");
		this.lifecycleService.confirmFeasible(request, "george", vet, start, 30);
		return new RequestFixture(request, interpretation, vet, start);
	}

	private static SchedulingRequestEvent rejectionEvent(SchedulingRequest request, SuggestionRejection rejection) {
		SchedulingRequestEvent event = new SchedulingRequestEvent();
		event.setRequest(request);
		event.setFromState(RequestState.SUGGESTION_OFFERED);
		event.setToState(RequestState.SUGGESTION_OFFERED);
		event.setActor("george");
		event.setAction(SuggestionRejection.EVENT_ACTION);
		event.setReason(rejection.scope().name());
		event.setPayload(rejection.payload());
		event.setTimestamp(TestClockConfig.PINNED_DATE_TIME);
		return event;
	}

	private MockHttpSession login() throws Exception {
		MvcResult result = this.mockMvc.perform(formLogin().user("george").password("george123"))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private record RequestFixture(SchedulingRequest request, Interpretation interpretation, Vet vet,
			ZonedDateTime start) {
	}

}
