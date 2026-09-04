/* Copyright 2012-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package org.springframework.samples.petclinic.scheduling;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.interpretation.CareType;
import org.springframework.samples.petclinic.scheduling.interpretation.Interpretation;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationRepository;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationResult;
import org.springframework.samples.petclinic.scheduling.interpretation.ModelUnavailableException;
import org.springframework.samples.petclinic.scheduling.interpretation.RequestInterpreter;
import org.springframework.samples.petclinic.scheduling.request.RejectionScope;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEvent;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEventRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.SuggestionRejection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
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

/** Non-transactional UC-3 HTTP scenarios through the real security filter chain. */
@SpringBootTest(properties = "scheduling.interpretation.executor=synchronous")
@Import({ TestClockConfig.class, OwnerRephrasesE2eTests.ScenarioInterpreterConfiguration.class })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OwnerRephrasesE2eTests {

	private static final String ROUTING_RECOMMENDATION = "We recommend routing this request to clinic staff.";

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
	private ScenarioRequestInterpreter interpreter;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
		this.interpreter.clear();
	}

	@Test
	@Tag("AC-138")
	void ownerRephrasesSteps1And2() throws Exception {
		StartedRequest started = startRequest("george", "george123", 1, "Initial checkup", "Tuesday afternoon");
		consent(started, RequestState.INTERPRETED);
		this.mockMvc.perform(post(started.location() + "/confirm").session(started.session()).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl(started.location()));
		assertState(started.id(), RequestState.SUGGESTION_OFFERED);
		this.mockMvc
			.perform(post(started.location() + "/another").session(started.session())
				.with(csrf())
				.param("scope", RejectionScope.NOT_THIS_TIME.name()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl(started.location()));
		assertState(started.id(), RequestState.SUGGESTION_OFFERED);
		assertThat(rejectionEvents(started.id())).hasSize(1);

		this.mockMvc.perform(get(started.location() + "/edit").session(started.session()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Initial checkup")))
			.andExpect(content().string(containsString("Tuesday afternoon")));
		this.mockMvc
			.perform(post(started.location() + "/edit").session(started.session())
				.with(csrf())
				.param("reasonText", "New skin concern")
				.param("availabilityText", "Friday"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl(started.location()))
			.andExpect(flash().attribute("requestEdited", true));
		SchedulingRequest edited = reload(started.id());
		assertThat(edited.getState()).isEqualTo(RequestState.AWAITING_CONSENT);
		assertThat(edited.getReasonText()).isEqualTo("New skin concern");
		assertThat(edited.getAvailabilityText()).isEqualTo("Friday");
		assertThat(edited.hasHold()).isFalse();
		assertThat(rejectionEvents(started.id())).hasSize(1);
		this.mockMvc.perform(get(started.location()).session(started.session()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Grant consent")))
			.andExpect(content().string(not(containsString("Review: Initial checkup"))));

		consent(started, RequestState.INTERPRETED);
		assertThat(this.interpretationRepository.findByRequestIdOrderByVersionDesc(started.id()))
			.extracting(Interpretation::getVersion, Interpretation::getReasonSummary)
			.containsExactly(tuple(2, "Review: New skin concern"), tuple(1, "Review: Initial checkup"));
		this.mockMvc.perform(get(started.location()).session(started.session()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Review: New skin concern")))
			.andExpect(content().string(containsString("Confirm interpretation")))
			.andExpect(content().string(not(containsString("You have ruled out:"))));
	}

	@Test
	void unavailableAndUnusableOutcomes() throws Exception {
		this.interpreter.failWhen("unavailable",
				new ModelUnavailableException("model timeout", new IllegalStateException("test timeout")));
		StartedRequest unavailable = startRequest("george", "george123", 1, "Unavailable model", "Tuesday");
		consent(unavailable, RequestState.WITH_STAFF);
		assertThat(this.interpretationRepository.findByRequestIdOrderByVersionDesc(unavailable.id())).isEmpty();
		assertThat(events(unavailable.id()).getLast()).satisfies(event -> {
			assertThat(event.getAction()).isEqualTo("model unavailable");
			assertThat(event.getReason()).isEqualTo("model timeout");
		});
		this.mockMvc.perform(get(unavailable.location()).session(unavailable.session()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("WITH_STAFF")));

		this.interpreter.resultWhen("unusable",
				InterpretationResult.cannotInterpret("{\"cannotInterpret\":true}", "test-model", "v1"));
		StartedRequest unusable = startRequest("betty", "betty123", 2, "Unusable request", "Tuesday");
		consent(unusable, RequestState.INTERPRETATION_FAILED);
		SchedulingRequest failed = reload(unusable.id());
		assertThat(failed.getFailedAttempts()).isEqualTo(1);
		assertThat(this.interpretationRepository.findByRequestIdOrderByVersionDesc(unusable.id())).singleElement()
			.extracting(Interpretation::isCannotInterpret)
			.isEqualTo(true);
		this.mockMvc.perform(get(unusable.location()).session(unusable.session()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("INTERPRETATION_FAILED")))
			.andExpect(content().string(containsString("Edit request")));
	}

	@Test
	@Tag("AC-37")
	@Tag("AC-38")
	void thirdAttemptRecommendationPersists() throws Exception {
		this.interpreter.resultWhen("fail",
				InterpretationResult.cannotInterpret("{\"cannotInterpret\":true}", "test-model", "v1"));
		StartedRequest started = startRequest("george", "george123", 1, "Fail attempt one", "Tuesday");
		consent(started, RequestState.INTERPRETATION_FAILED);
		assertFailedAttempt(started, 1, false);

		editAndConsentFailed(started, "Fail attempt two");
		assertFailedAttempt(started, 2, false);
		editAndConsentFailed(started, "Fail attempt three");
		assertFailedAttempt(started, 3, true);
		editAndConsentFailed(started, "Fail attempt four");
		assertFailedAttempt(started, 4, true);
	}

	@Test
	@Tag("AC-122")
	void ownerRoutesToStaff() throws Exception {
		StartedRequest started = startRequest("george", "george123", 1, "Please review", "Friday");
		consent(started, RequestState.INTERPRETED);
		int eventsBefore = events(started.id()).size();

		this.mockMvc
			.perform(post(started.location() + "/route-to-staff").session(started.session())
				.with(csrf())
				.param("reason", "Owner wants clinic help"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl(started.location()))
			.andExpect(flash().attribute("requestRoutedToStaff", true));

		SchedulingRequest routed = reload(started.id());
		assertThat(routed.getState()).isEqualTo(RequestState.WITH_STAFF);
		assertThat(routed.hasHold()).isFalse();
		assertThat(events(started.id())).hasSize(eventsBefore + 1).last().satisfies(event -> {
			assertThat(event.getFromState()).isEqualTo(RequestState.INTERPRETED);
			assertThat(event.getToState()).isEqualTo(RequestState.WITH_STAFF);
			assertThat(event.getActor()).isEqualTo("george");
			assertThat(event.getAction()).isEqualTo("route to staff");
			assertThat(event.getReason()).isEqualTo("Owner wants clinic help");
		});
		this.mockMvc.perform(get(started.location()).session(started.session()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("WITH_STAFF")))
			.andExpect(content().string(containsString("Please review")));
	}

	private void editAndConsentFailed(StartedRequest started, String reason) throws Exception {
		this.mockMvc
			.perform(post(started.location() + "/edit").session(started.session())
				.with(csrf())
				.param("reasonText", reason)
				.param("availabilityText", "Tuesday"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl(started.location()));
		assertState(started.id(), RequestState.AWAITING_CONSENT);
		consent(started, RequestState.INTERPRETATION_FAILED);
	}

	private void assertFailedAttempt(StartedRequest started, int expectedAttempts, boolean recommendation)
			throws Exception {
		SchedulingRequest request = reload(started.id());
		assertThat(request.getState()).isEqualTo(RequestState.INTERPRETATION_FAILED);
		assertThat(request.getFailedAttempts()).isEqualTo(expectedAttempts);
		var page = this.mockMvc.perform(get(started.location()).session(started.session()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Edit request")));
		if (recommendation) {
			page.andExpect(content().string(containsString(ROUTING_RECOMMENDATION)));
		}
		else {
			page.andExpect(content().string(not(containsString(ROUTING_RECOMMENDATION))));
		}
	}

	private void consent(StartedRequest started, RequestState expected) throws Exception {
		this.mockMvc.perform(post(started.location() + "/consent").session(started.session()).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl(started.location()));
		assertState(started.id(), expected);
	}

	private StartedRequest startRequest(String username, String password, int ownerId, String reason,
			String availability) throws Exception {
		MockHttpSession session = login(username, password);
		Pet pet = availablePet(ownerId);
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
		Integer requestId = Integer.valueOf(location.substring(location.lastIndexOf('/') + 1));
		assertState(requestId, RequestState.AWAITING_CONSENT);
		return new StartedRequest(session, requestId, location);
	}

	private MockHttpSession login(String username, String password) throws Exception {
		MvcResult result = this.mockMvc.perform(formLogin().user(username).password(password))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/my/appointments"))
			.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private Pet availablePet(int ownerId) {
		return this.ownerRepository.findById(ownerId)
			.orElseThrow()
			.getPets()
			.stream()
			.filter(pet -> this.requestRepository.findByActivePetId(pet.getId()).isEmpty())
			.findFirst()
			.orElseThrow();
	}

	private List<SchedulingRequestEvent> events(Integer requestId) {
		return this.eventRepository.findByRequestIdOrderByTimestampAsc(requestId);
	}

	private List<SchedulingRequestEvent> rejectionEvents(Integer requestId) {
		return events(requestId).stream()
			.filter(event -> SuggestionRejection.EVENT_ACTION.equals(event.getAction()))
			.toList();
	}

	private SchedulingRequest reload(Integer requestId) {
		return this.requestRepository.findById(requestId).orElseThrow();
	}

	private void assertState(Integer requestId, RequestState expected) {
		assertThat(reload(requestId).getState()).isEqualTo(expected);
	}

	private record StartedRequest(MockHttpSession session, Integer id, String location) {
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class ScenarioInterpreterConfiguration {

		@Bean
		@Primary
		ScenarioRequestInterpreter scenarioRequestInterpreter() {
			return new ScenarioRequestInterpreter();
		}

	}

	static final class ScenarioRequestInterpreter implements RequestInterpreter {

		private final Map<String, Object> outcomes = new LinkedHashMap<>();

		void resultWhen(String keyword, InterpretationResult result) {
			this.outcomes.put(keyword.toLowerCase(), result);
		}

		void failWhen(String keyword, ModelUnavailableException failure) {
			this.outcomes.put(keyword.toLowerCase(), failure);
		}

		void clear() {
			this.outcomes.clear();
		}

		@Override
		public InterpretationResult interpret(String reasonText, String availabilityText) {
			String input = ((reasonText == null ? "" : reasonText) + " "
					+ (availabilityText == null ? "" : availabilityText))
				.toLowerCase();
			for (Map.Entry<String, Object> entry : this.outcomes.entrySet()) {
				if (input.contains(entry.getKey())) {
					if (entry.getValue() instanceof ModelUnavailableException failure) {
						throw failure;
					}
					return (InterpretationResult) entry.getValue();
				}
			}
			return new InterpretationResult("Review: " + reasonText, 30, CareType.GENERAL, null, null, false, List.of(),
					"{\"scenario\":\"usable\"}", "test-model", "v1");
		}

	}

}
