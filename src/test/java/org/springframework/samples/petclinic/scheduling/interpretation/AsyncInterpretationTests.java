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

package org.springframework.samples.petclinic.scheduling.interpretation;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEventRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
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

@SpringBootTest(properties = "scheduling.interpretation.executor=threaded")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AsyncInterpretationTests {

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

	@MockitoBean
	private RequestInterpreter interpreter;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
		reset(this.interpreter);
		when(this.interpreter.interpret(anyString(), anyString())).thenReturn(result("Default review"));
	}

	@Test
	@Tag("AC-24")
	void consentSendsTextOnlyAfterStateCommit_AC24() throws Exception {
		SchedulingRequest request = createRequest("Skin rash", "Tuesday afternoon");
		AtomicReference<RequestState> stateWhenSent = new AtomicReference<>();
		AtomicReference<String> sentReason = new AtomicReference<>();
		AtomicReference<String> sentAvailability = new AtomicReference<>();
		CountDownLatch sent = new CountDownLatch(1);
		when(this.interpreter.interpret(anyString(), anyString())).thenAnswer(invocation -> {
			stateWhenSent.set(this.requestRepository.findById(request.getId()).orElseThrow().getState());
			sentReason.set(invocation.getArgument(0));
			sentAvailability.set(invocation.getArgument(1));
			sent.countDown();
			return result("Committed review");
		});
		verify(this.interpreter, never()).interpret(anyString(), anyString());

		this.mockMvc.perform(post("/my/requests/{id}/consent", request.getId()).session(login()).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/my/requests/" + request.getId()));

		assertThat(sent.await(2, TimeUnit.SECONDS)).isTrue();
		assertThat(stateWhenSent.get()).isEqualTo(RequestState.INTERPRETING);
		assertThat(sentReason.get()).isEqualTo("Skin rash");
		assertThat(sentAvailability.get()).isEqualTo("Tuesday afternoon");
		awaitState(request.getId(), RequestState.INTERPRETED);
		verify(this.interpreter).interpret("Skin rash", "Tuesday afternoon");
	}

	@Test
	@Tag("AC-26")
	void exactlyOneJobUsesNamedSingleThreadExecutor_AC26() throws Exception {
		SchedulingRequest request = createRequest("Routine check", "Monday morning");
		this.lifecycleService.consent(request, "george");
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		AtomicReference<String> workerName = new AtomicReference<>();
		when(this.interpreter.interpret(anyString(), anyString())).thenAnswer(invocation -> {
			workerName.set(Thread.currentThread().getName());
			entered.countDown();
			assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
			return result("Only review");
		});
		CyclicBarrier barrier = new CyclicBarrier(2);
		try (var callers = Executors.newFixedThreadPool(2)) {
			Future<Boolean> first = callers.submit(() -> {
				barrier.await();
				return this.asyncInterpretationService.dispatch(request.getId(), "george");
			});
			Future<Boolean> second = callers.submit(() -> {
				barrier.await();
				return this.asyncInterpretationService.dispatch(request.getId(), "george");
			});

			assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
			assertThat(List.of(first.get(2, TimeUnit.SECONDS), second.get(2, TimeUnit.SECONDS)))
				.containsExactlyInAnyOrder(true, false);
			assertThat(this.asyncInterpretationService.isInFlight(request.getId())).isTrue();
			release.countDown();
		}
		awaitState(request.getId(), RequestState.INTERPRETED);
		assertThat(workerName.get()).startsWith("request-interpretation-");
		verify(this.interpreter).interpret("Routine check", "Monday morning");
	}

	@Test
	@Tag("AC-27")
	void interpretingAllowsOnlyAbandon_AC27() throws Exception {
		SchedulingRequest request = createRequest("Needs interpretation", "Friday");
		this.lifecycleService.consent(request, "george");
		MockHttpSession session = login();
		long eventCount = this.eventRepository.findByRequestIdOrderByTimestampAsc(request.getId()).size();

		this.mockMvc.perform(get("/my/requests/{id}/edit", request.getId()).session(session))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/my/requests/" + request.getId()));
		this.mockMvc
			.perform(post("/my/requests/{id}/edit", request.getId()).session(session)
				.with(csrf())
				.param("reasonText", "Deferred edit")
				.param("availabilityText", "Deferred availability"))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute("actionNotAllowed", true));
		this.mockMvc.perform(post("/my/requests/{id}/route-to-staff", request.getId()).session(session).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute("actionNotAllowed", true));

		SchedulingRequest unchanged = this.requestRepository.findById(request.getId()).orElseThrow();
		assertThat(unchanged.getState()).isEqualTo(RequestState.INTERPRETING);
		assertThat(unchanged.getReasonText()).isEqualTo("Needs interpretation");
		assertThat(this.eventRepository.findByRequestIdOrderByTimestampAsc(request.getId())).hasSize((int) eventCount);
		assertThat(this.interpretationRepository.findByRequestIdOrderByVersionDesc(request.getId())).isEmpty();
		verify(this.interpreter, never()).interpret(anyString(), anyString());

		this.mockMvc.perform(post("/my/requests/{id}/abandon", request.getId()).session(session).with(csrf()))
			.andExpect(status().is3xxRedirection());
		assertThat(this.requestRepository.findById(request.getId()).orElseThrow().getState())
			.isEqualTo(RequestState.ABANDONED);
	}

	@Test
	@Tag("AC-28")
	void detailUsesMetaRefreshWithoutJavascriptOrJson_AC28() throws Exception {
		SchedulingRequest request = createRequest("Needs interpretation", "Friday");
		this.lifecycleService.consent(request, "george");
		String detailTemplate = Files.readString(Path.of("src/main/resources/templates/my/requestDetail.html"));

		this.mockMvc.perform(get("/my/requests/{id}", request.getId()).session(login()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("<meta http-equiv=\"refresh\" content=\"3\">")))
			.andExpect(content().string(not(containsString("application/json"))))
			.andExpect(content().string(containsString("Interpreting your request")));
		assertThat(detailTemplate).contains(
				"<meta th:if=\"${request.state.name() == 'INTERPRETING'}\" " + "http-equiv=\"refresh\" content=\"3\">");
		assertThat(detailTemplate).doesNotContain("<script", "fetch(", "application/json");
	}

	@Test
	@Tag("AC-31")
	void applyRunsInFreshStateCheckedTransaction_AC31() throws Exception {
		SchedulingRequest request = createRequest("Late result", "Thursday");
		this.lifecycleService.consent(request, "george");
		RequestInterpretationService.InterpretationInput input = this.interpretationService.inputFor(request.getId())
			.orElseThrow();
		InterpretationResult result = this.interpretationService.interpret(input);
		this.lifecycleService.abandon(this.requestRepository.findById(request.getId()).orElseThrow(), "george",
				"Owner stopped waiting");
		long eventCount = this.eventRepository.findByRequestIdOrderByTimestampAsc(request.getId()).size();

		assertThat(this.interpretationService.applyResult(request.getId(), result, "system")).isEmpty();
		assertThat(this.requestRepository.findById(request.getId()).orElseThrow().getState())
			.isEqualTo(RequestState.ABANDONED);
		assertThat(this.interpretationRepository.findByRequestIdOrderByVersionDesc(request.getId())).isEmpty();
		assertThat(this.eventRepository.findByRequestIdOrderByTimestampAsc(request.getId())).hasSize((int) eventCount);
		Method apply = RequestInterpretationService.class.getMethod("applyResult", Integer.class,
				InterpretationResult.class, String.class);
		assertThat(apply.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.REQUIRES_NEW);
		verify(this.interpreter, atLeastOnce()).interpret("Late result", "Thursday");
	}

	private SchedulingRequest createRequest(String reason, String availability) {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().stream().findFirst().orElseThrow();
		return this.lifecycleService.createRequest(owner, pet, reason, availability, "george");
	}

	private MockHttpSession login() throws Exception {
		MvcResult result = this.mockMvc.perform(formLogin().user("george").password("george123"))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private void awaitState(Integer requestId, RequestState expected) throws InterruptedException {
		long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
		while (System.nanoTime() < deadline) {
			if (this.requestRepository.findById(requestId).map(SchedulingRequest::getState).orElse(null) == expected) {
				return;
			}
			Thread.sleep(20);
		}
		assertThat(this.requestRepository.findById(requestId).map(SchedulingRequest::getState)).contains(expected);
	}

	private static InterpretationResult result(String summary) {
		return new InterpretationResult(summary, 30, CareType.GENERAL, null, null, false, Collections.emptyList(), "{}",
				"test-model", "v1");
	}

}
