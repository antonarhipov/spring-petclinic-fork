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

import java.lang.reflect.Method;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.config.OllamaChatConfiguration;
import org.springframework.samples.petclinic.scheduling.request.RequestLifecycleService;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEvent;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEventRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = { "scheduling.interpretation.executor=synchronous", "scheduling.ai.timeout=17s" })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OllamaAdapterContractTests {

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
	private RequestLifecycleService lifecycleService;

	@Autowired
	private RequestInterpretationService interpretationService;

	@Autowired
	private AsyncInterpretationService asyncInterpretationService;

	@Value("${scheduling.ai.timeout}")
	private Duration configuredTimeout;

	@MockitoBean
	private RequestInterpreter interpreter;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
		reset(this.interpreter);
	}

	@Test
	@Tag("AC-34")
	void malformedRetriesOnceThenRoutesWithReason_AC34() {
		AtomicInteger parseCalls = new AtomicInteger();
		OllamaRequestInterpreter adapter = new OllamaRequestInterpreter((reason, availability) -> {
			parseCalls.incrementAndGet();
			throw new IllegalArgumentException("malformed structured output");
		}, "configured-model");

		ModelUnavailableException failure = catchUnavailable(adapter);
		assertThat(parseCalls).hasValue(2);
		assertThat(failure).hasMessage("malformed model response after retry");

		SchedulingRequest request = createInterpretingRequest();
		when(this.interpreter.interpret(anyString(), anyString())).thenThrow(failure);
		assertThat(this.asyncInterpretationService.dispatch(request.getId(), "system")).isTrue();
		assertUnavailableRouting(request, "malformed model response after retry");
	}

	@Test
	@Tag("AC-34")
	void timeoutDoesNotRetryAndRoutesWithReason_AC34() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		OllamaRequestInterpreter adapter = new OllamaRequestInterpreter((reason, availability) -> {
			calls.incrementAndGet();
			throw new ResourceAccessException("read timed out", new HttpTimeoutException("read timed out"));
		}, "configured-model");

		ModelUnavailableException failure = catchUnavailable(adapter);
		assertThat(calls).hasValue(1);
		assertThat(failure).hasMessage("model timeout");
		assertTimeoutConfigurationContract();

		SchedulingRequest request = createInterpretingRequest();
		when(this.interpreter.interpret(anyString(), anyString())).thenThrow(failure);
		this.asyncInterpretationService.dispatch(request.getId(), "system");
		assertUnavailableRouting(request, "model timeout");
	}

	@Test
	@Tag("AC-35")
	void unmatchedOtherSpecialtyRoutesWithoutDowngrade_AC35() {
		SchedulingRequest request = createInterpretingRequest();
		InterpretationResult result = new InterpretationResult("Exotic care", 30, CareType.SPECIALTY,
				"OTHER:cardiology", null, false, Collections.emptyList(), "{}", "test-model", "v1");

		this.interpretationService.applyResult(request.getId(), result, "system");

		assertThat(reload(request).getState()).isEqualTo(RequestState.WITH_STAFF);
		Interpretation persisted = this.interpretationRepository.findByRequestIdOrderByVersionDesc(request.getId())
			.getFirst();
		assertThat(persisted.getCareType()).isEqualTo(CareType.SPECIALTY);
		assertThat(persisted.getSpecialty()).isEqualTo("OTHER:cardiology");
		assertThat(events(request).getLast().getReason()).isEqualTo("OTHER:cardiology");
	}

	@Test
	@Tag("AC-36")
	void recommendationAbsentBelowThree_AC36() throws Exception {
		SchedulingRequest request = createInterpretingRequest();
		MockHttpSession session = login();

		failInterpretation(request);
		assertRecommendation(request, session, false);
		rephraseAndConsent(request);
		failInterpretation(request);
		assertRecommendation(request, session, false);

		assertThat(reload(request).getFailedAttempts()).isEqualTo(2);
	}

	@Test
	@Tag("AC-37")
	@Tag("AC-38")
	void recommendationAppearsAtAndPersistsAboveThree_AC37_AC38() throws Exception {
		SchedulingRequest request = createInterpretingRequest();
		MockHttpSession session = login();

		failInterpretation(request);
		rephraseAndConsent(request);
		failInterpretation(request);
		rephraseAndConsent(request);
		failInterpretation(request);
		assertThat(reload(request).getFailedAttempts()).isEqualTo(3);
		assertRecommendation(request, session, true);

		rephraseAndConsent(request);
		failInterpretation(request);
		assertThat(reload(request).getFailedAttempts()).isEqualTo(4);
		assertRecommendation(request, session, true);
	}

	private ModelUnavailableException catchUnavailable(OllamaRequestInterpreter adapter) {
		return catchThrowableOfType(ModelUnavailableException.class, () -> adapter.interpret("reason", "availability"));
	}

	private void assertTimeoutConfigurationContract() throws Exception {
		String properties = Files.readString(Path.of("src/main/resources/application.properties"));
		assertThat(properties).contains("scheduling.ai.timeout=${SCHEDULING_AI_TIMEOUT:60s}");
		Method method = OllamaChatConfiguration.class.getDeclaredMethod("ollamaRestClientTimeoutCustomizer",
				Duration.class);
		Value binding = method.getParameters()[0].getAnnotation(Value.class);
		assertThat(binding.value()).isEqualTo("${scheduling.ai.timeout:60s}");
		assertThat(readTimeout(Duration.ofSeconds(60))).isEqualTo(Duration.ofSeconds(60));
		assertThat(this.configuredTimeout).isEqualTo(Duration.ofSeconds(17));
		assertThat(readTimeout(this.configuredTimeout)).isEqualTo(Duration.ofSeconds(17));
	}

	private static Duration readTimeout(Duration timeout) {
		RestClient.Builder builder = RestClient.builder();
		RestClientCustomizer customizer = new OllamaChatConfiguration().ollamaRestClientTimeoutCustomizer(timeout);
		customizer.customize(builder);
		Object requestFactory = ReflectionTestUtils.getField(builder, "requestFactory");
		assertThat(requestFactory).isInstanceOf(JdkClientHttpRequestFactory.class);
		return (Duration) ReflectionTestUtils.getField(requestFactory, "readTimeout");
	}

	private SchedulingRequest createInterpretingRequest() {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().stream().findFirst().orElseThrow();
		SchedulingRequest request = this.lifecycleService.createRequest(owner, pet, "Reason", "Availability", "george");
		return this.lifecycleService.consent(request, "george");
	}

	private void failInterpretation(SchedulingRequest request) {
		this.interpretationService.applyResult(request.getId(),
				InterpretationResult.cannotInterpret("{}", "test-model", "v1"), "system");
	}

	private void rephraseAndConsent(SchedulingRequest request) {
		SchedulingRequest current = reload(request);
		this.lifecycleService.editText(current, "george", current.getReasonText(), current.getAvailabilityText());
		this.lifecycleService.consent(reload(request), "george");
	}

	private void assertRecommendation(SchedulingRequest request, MockHttpSession session, boolean expected)
			throws Exception {
		var action = this.mockMvc.perform(get("/my/requests/{id}", request.getId()).session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Edit request")));
		if (expected) {
			action.andExpect(content().string(containsString(ROUTING_RECOMMENDATION)));
		}
		else {
			action.andExpect(content().string(not(containsString(ROUTING_RECOMMENDATION))));
		}
	}

	private void assertUnavailableRouting(SchedulingRequest request, String reason) {
		assertThat(reload(request).getState()).isEqualTo(RequestState.WITH_STAFF);
		assertThat(events(request).getLast().getReason()).isEqualTo(reason);
		assertThat(this.interpretationRepository.findByRequestIdOrderByVersionDesc(request.getId())).isEmpty();
	}

	private SchedulingRequest reload(SchedulingRequest request) {
		return this.requestRepository.findById(request.getId()).orElseThrow();
	}

	private java.util.List<SchedulingRequestEvent> events(SchedulingRequest request) {
		return this.eventRepository.findByRequestIdOrderByTimestampAsc(request.getId());
	}

	private MockHttpSession login() throws Exception {
		MvcResult result = this.mockMvc.perform(formLogin().user("george").password("george123"))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

}
