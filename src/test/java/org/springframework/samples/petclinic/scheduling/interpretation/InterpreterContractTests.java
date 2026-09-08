package org.springframework.samples.petclinic.scheduling.interpretation;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatProperties;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.samples.petclinic.scheduling.interpretation.Interpreter.InterpretationException;
import org.springframework.samples.petclinic.scheduling.request.RequestService;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.support.DeterministicInterpreter;
import org.springframework.samples.petclinic.scheduling.support.DeterministicInterpreter.Mode;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.core.retry.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@ExtendWith(OutputCaptureExtension.class)
class InterpreterContractTests {

	private static final String MODEL_JSON = """
			{"understood":true,"careType":"SPECIALTY","specialty":"surgery",
			 "durationMinutes":30,"preferredVetId":3,
			 "preferredWindows":[{"day":"MONDAY","start":"09:00","end":"12:00"}],
			 "allowedWindows":[],"excludedWindows":[]}
			""";

	@Autowired
	private PromptBuilder prompts;

	@Autowired
	private SchedulingRequestRepository requests;

	@Autowired
	private RequestService requestService;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private OllamaChatProperties ollamaProperties;

	@Autowired
	private SpringAiRetryProperties retryProperties;

	@Autowired
	private RetryTemplate retryTemplate;

	@Test
	@Tag("AC-39")
	void ac39_prompt_contains_only_allowed_fields() {
		String prompt = this.prompts.build("Surgery follow-up, mornings this week");

		assertThat(prompt).isEqualTo(
				"""
						requestText=Surgery follow-up, mornings this week
						specialties=radiology,surgery,dentistry
						veterinarians=1:James Carter[];2:Helen Leary[radiology];3:Linda Douglas[surgery,dentistry];4:Rafael Ortega[surgery];5:Henry Stevens[radiology];6:Sharon Jenkins[]
						openingHours=MONDAY:09:00-17:00;TUESDAY:09:00-17:00;WEDNESDAY:09:00-18:00;THURSDAY:09:00-17:00;FRIDAY:10:00-16:00;SATURDAY:closed;SUNDAY:closed
						partsOfDay=MONDAY:morning=09:00-12:00,afternoon=12:00-17:00,evening=closed;TUESDAY:morning=09:00-12:00,afternoon=12:00-17:00,evening=closed;WEDNESDAY:morning=09:00-12:00,afternoon=12:00-17:00,evening=17:00-18:00;THURSDAY:morning=09:00-12:00,afternoon=12:00-17:00,evening=closed;FRIDAY:morning=10:00-12:00,afternoon=12:00-16:00,evening=closed;SATURDAY:morning=closed,afternoon=closed,evening=closed;SUNDAY:morning=closed,afternoon=closed,evening=closed
						today=2026-09-07
						upcomingWeekdayDates=MONDAY:2026-09-14;TUESDAY:2026-09-08;WEDNESDAY:2026-09-09;THURSDAY:2026-09-10;FRIDAY:2026-09-11;SATURDAY:2026-09-12;SUNDAY:2026-09-13
						zone=Europe/Amsterdam
						durationMinutes=min:15,default:30,max:60""");
		assertThat(prompt).doesNotContain("George Franklin", "Leo", "6085551023", "george123", "password_hash",
				"owner_id", "pet_id", "SchedulingRequest", "Owner", "Pet");
		assertThat(this.prompts.build("Tomorrow morning", LocalDate.of(2026, 9, 5))).contains(
				"requestText=Tomorrow morning", "today=2026-09-05",
				"upcomingWeekdayDates=MONDAY:2026-09-07;TUESDAY:2026-09-08;WEDNESDAY:2026-09-09;THURSDAY:2026-09-10;FRIDAY:2026-09-11;SATURDAY:2026-09-12;SUNDAY:2026-09-06")
			.doesNotContain("today=2026-09-07");
		assertThat(this.prompts.build("Next Thursday", LocalDate.of(2026, 9, 10))).contains("today=2026-09-10",
				"THURSDAY:2026-09-17");
		assertThat(OllamaInterpreter.SYSTEM_PROMPT).contains("relative dates against today", "copy",
				"upcomingWeekdayDates", "do not calculate it", "absolute clinic-local dates", "named parts of day",
				"any day except", "Wednesday", "application defaults", "apply", "set day to YYYY-MM-DD",
				"uppercase weekday", "every Thursday", "clinic-local HH:mm", "without seconds", "UTC offset",
				"Do not infer urgency");
	}

	@Test
	@Tag("AC-40")
	void ac40_schema_temperature_timeouts_and_single_call() {
		RecordingChatModel model = new RecordingChatModel();
		OllamaInterpreter interpreter = new OllamaInterpreter(ChatClient.create(model), "ministral-3:14b");

		InterpretationResult result = interpreter.interpret("allowed prompt").toCompletableFuture().join();

		assertThat(result.rawJson()).isEqualTo(MODEL_JSON);
		assertThat(model.calls()).isOne();
		assertThat(model.prompt().getInstructions().stream().map(message -> message.getText()).toList())
			.containsExactly(OllamaInterpreter.SYSTEM_PROMPT, "allowed prompt");
		assertThat(model.prompt().getOptions()).isInstanceOfSatisfying(OllamaChatOptions.class, options -> {
			assertThat(options.getModel()).isEqualTo("ministral-3:14b");
			assertThat(options.getTemperature()).isZero();
			assertThat(options.getOutputSchema()).contains("preferredWindows", "allowedWindows", "excludedWindows",
					OllamaInterpretationResponse.DAY_PATTERN, OllamaInterpretationResponse.TIME_PATTERN,
					"Clinic-local start time in exact HH:mm format, without seconds or an offset");
		});
		RecordingChatModel invalidModel = new RecordingChatModel("not-json");
		OllamaInterpreter invalidInterpreter = new OllamaInterpreter(ChatClient.create(invalidModel),
				"ministral-3:14b");
		assertThatThrownBy(() -> invalidInterpreter.interpret("allowed prompt").toCompletableFuture().join())
			.isInstanceOf(CompletionException.class)
			.hasCauseInstanceOf(InterpretationException.class)
			.satisfies(failure -> assertThat(((InterpretationException) failure.getCause()).getKind())
				.isEqualTo(Interpreter.FailureKind.UNPARSEABLE));
		assertThat(invalidModel.calls()).isOne();
		assertThatThrownBy(() -> new OllamaInterpretationResponse.WindowValue("2026-09-10", "09:00:00+02:00", "17:00"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("exact clinic-local HH:mm");
		assertThatThrownBy(() -> new OllamaInterpretationResponse.WindowValue("THURSDAY", "17:00", "09:00"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("increasing");
		assertThat(new OllamaInterpretationResponse.WindowValue("2026-09-10", "09:00", "17:00").toWindow())
			.isEqualTo(new InterpretationResult.Window(null, LocalDate.of(2026, 9, 10), LocalTime.of(9, 0),
					LocalTime.of(17, 0)));
		assertThatThrownBy(() -> new InterpretationResult.Window(DayOfWeek.THURSDAY, LocalDate.of(2026, 9, 11),
				LocalTime.of(9, 0), LocalTime.of(17, 0)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("exactly one");
		assertThatThrownBy(() -> new InterpretationResult.Window(null, LocalDate.of(2026, 9, 10), LocalTime.of(17, 0),
				LocalTime.of(9, 0)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("increasing");

		InterpretationExecutorConfiguration configuration = new InterpretationExecutorConfiguration();
		RestClient.Builder builder = RestClient.builder();
		configuration.ollamaRestClientTimeoutCustomizer().customize(builder);
		Object requestFactory = ReflectionTestUtils.getField(builder, "requestFactory");
		assertThat(requestFactory).isInstanceOf(JdkClientHttpRequestFactory.class);
		assertThat(ReflectionTestUtils.getField(requestFactory, "readTimeout")).isEqualTo(Duration.ofSeconds(120));
		HttpClient client = (HttpClient) ReflectionTestUtils.getField(requestFactory, "httpClient");
		assertThat(client.connectTimeout()).contains(Duration.ofSeconds(5));
		assertThat(InterpretationJobRunner.DEFAULT_DEADLINE).isEqualTo(Duration.ofSeconds(120));
		assertThat(this.ollamaProperties.getModel()).isEqualTo("ministral-3:14b");
		assertThat(this.ollamaProperties.getTemperature()).isZero();
		assertThat(this.retryProperties.getMaxAttempts()).isZero();
		AtomicInteger attempts = new AtomicInteger();
		assertThatThrownBy(() -> this.retryTemplate.invoke(() -> {
			attempts.incrementAndGet();
			throw new ResourceAccessException("offline");
		})).isInstanceOf(RuntimeException.class);
		assertThat(attempts).hasValue(1);

		Executor executor = configuration.interpretationExecutor();
		assertThat(executor).isInstanceOfSatisfying(ThreadPoolTaskExecutor.class, pool -> {
			assertThat(pool.getCorePoolSize()).isEqualTo(2);
			assertThat(pool.getMaxPoolSize()).isEqualTo(2);
			assertThat(pool.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(Integer.MAX_VALUE);
			pool.shutdown();
		});
	}

	@Test
	void llmInteractionLogsRequestPayloadAndStructuredResponseWithoutRawProviderJson(CapturedOutput output) {
		RecordingChatModel model = new RecordingChatModel();
		OllamaInterpreter interpreter = new OllamaInterpreter(ChatClient.create(model), "ministral-3:14b");

		interpreter.interpret("allowed prompt").toCompletableFuture().join();

		assertThat(output)
			.contains("LLM request payload", "model=ministral-3:14b", "temperature=0.0",
					"systemPrompt=" + OllamaInterpreter.SYSTEM_PROMPT, "userPrompt=allowed prompt",
					"responseType=" + OllamaInterpretationResponse.class.getName(), "LLM structured response",
					"response=ModelOutput[understood=true")
			.doesNotContain(MODEL_JSON);
	}

	@Test
	void llmInteractionLogsSafeFailureCategoryWithoutRawProviderJson(CapturedOutput output) {
		String invalidTemporalJson = MODEL_JSON.replace("\"09:00\"", "\"09:00:00+02:00\"");
		RecordingChatModel model = new RecordingChatModel(invalidTemporalJson);
		OllamaInterpreter interpreter = new OllamaInterpreter(ChatClient.create(model), "ministral-3:14b");

		assertThatThrownBy(() -> interpreter.interpret("allowed prompt").toCompletableFuture().join())
			.isInstanceOf(CompletionException.class)
			.hasCauseInstanceOf(InterpretationException.class);

		assertThat(output)
			.contains("LLM interaction failed", "model=ministral-3:14b", "category=UNPARSEABLE", "exception=")
			.doesNotContain(invalidTemporalJson, "09:00:00+02:00");
		assertThat(model.calls()).isOne();
	}

	@Test
	@Tag("AC-46")
	void ac46_transport_and_deadline_no_retry() {
		for (Mode mode : List.of(Mode.TRANSPORT, Mode.NEVER_COMPLETING)) {
			int requestId = interpretingRequest(mode.ordinal() + 1);
			DeterministicInterpreter interpreter = new DeterministicInterpreter();
			interpreter.setMode(mode);
			new InterpretationJobRunner(this.requests, this.prompts, interpreter, this.requestService,
					Duration.ofMillis(20))
				.launch(requestId);

			awaitWithStaff(requestId);
			assertThat(interpreter.getCallCount()).isOne();
			assertThat(this.jdbc.queryForObject("select with_staff_reason from scheduling_requests where id = ?",
					String.class, requestId))
				.isEqualTo("AI_UNAVAILABLE");
		}
	}

	@Test
	@Tag("AC-47")
	void ac47_startup_sweep_routes_all_interpreting() {
		int first = interpretingRequest(1);
		int second = interpretingRequest(2);
		int untouched = this.requestService.startForOwner(3, "Keep awaiting consent").request().getId();

		new InterpretingRequestRecovery(this.requests, this.requestService).recover();

		assertThat(state(first)).isEqualTo("WITH_STAFF:AI_UNAVAILABLE");
		assertThat(state(second)).isEqualTo("WITH_STAFF:AI_UNAVAILABLE");
		assertThat(state(untouched)).isEqualTo("AWAITING_CONSENT:null");
	}

	@Test
	@Tag("AC-136")
	void ac136_double_matches_contract_and_modes() {
		DeterministicInterpreter interpreter = new DeterministicInterpreter();
		for (Mode mode : Mode.values()) {
			interpreter.setMode(mode);
			if (mode == Mode.UNPARSEABLE || mode == Mode.TRANSPORT) {
				assertThatThrownBy(() -> interpreter.interpret("prompt-" + mode).toCompletableFuture().join())
					.isInstanceOf(CompletionException.class)
					.hasCauseInstanceOf(InterpretationException.class)
					.satisfies(failure -> {
						InterpretationException cause = (InterpretationException) failure.getCause();
						assertThat(cause.getKind()).isEqualTo(mode == Mode.UNPARSEABLE
								? Interpreter.FailureKind.UNPARSEABLE : Interpreter.FailureKind.TRANSPORT);
						assertThat(cause.getRawOutput()).isEqualTo(mode == Mode.UNPARSEABLE ? "not-json" : null);
					});
			}
			else if (mode == Mode.NEVER_COMPLETING) {
				assertThat(interpreter.interpret("prompt-" + mode).toCompletableFuture()).isNotDone();
			}
			else {
				InterpretationResult result = interpreter.interpret("prompt-" + mode).toCompletableFuture().join();
				assertThat(result).isNotNull();
				assertThat(result.output().careType())
					.isEqualTo(org.springframework.samples.petclinic.scheduling.request.CareType.SPECIALTY);
				assertThat(result.output().specialty()).isEqualTo("surgery");
				assertThat(result.output().specialtyLabel()).isNull();
				assertThat(result.output().durationMinutes()).isEqualTo(30);
				assertThat(result.output().preferredVetId()).isEqualTo(3);
				assertThat(result.output().allowedWindows()).isEmpty();
				assertThat(result.output().excludedWindows()).isEmpty();
				if (mode == Mode.SUCCESS || mode == Mode.UNDERSTOOD_FALSE) {
					assertThat(result.output().preferredWindows()).hasSize(1);
				}
				else {
					assertThat(result.output().preferredWindows()).isEmpty();
				}
			}
		}
		assertThat(interpreter.getCallCount()).isEqualTo(Mode.values().length);
		assertThat(interpreter.getPrompts())
			.containsExactlyElementsOf(java.util.Arrays.stream(Mode.values()).map(mode -> "prompt-" + mode).toList());
	}

	private int interpretingRequest(int petId) {
		int requestId = this.requestService.startForOwner(petId, "Interpret pet " + petId).request().getId();
		this.jdbc.update("update scheduling_requests set state = 'INTERPRETING' where id = ?", requestId);
		return requestId;
	}

	private void awaitWithStaff(int requestId) {
		long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
		while (!state(requestId).startsWith("WITH_STAFF") && System.nanoTime() < deadline) {
			Thread.onSpinWait();
		}
		assertThat(state(requestId)).startsWith("WITH_STAFF");
	}

	private String state(int requestId) {
		return this.jdbc.queryForObject(
				"select state || ':' || coalesce(with_staff_reason, 'null') from scheduling_requests where id = ?",
				String.class, requestId);
	}

	private static final class RecordingChatModel implements ChatModel {

		private final AtomicInteger calls = new AtomicInteger();

		private final AtomicReference<Prompt> prompt = new AtomicReference<>();

		private final String response;

		private RecordingChatModel() {
			this(MODEL_JSON);
		}

		private RecordingChatModel(String response) {
			this.response = response;
		}

		@Override
		public ChatResponse call(Prompt prompt) {
			this.calls.incrementAndGet();
			this.prompt.set(prompt);
			return new ChatResponse(List.of(new Generation(new AssistantMessage(this.response))));
		}

		@Override
		public OllamaChatOptions getOptions() {
			return OllamaChatOptions.builder().build();
		}

		int calls() {
			return this.calls.get();
		}

		Prompt prompt() {
			return this.prompt.get();
		}

	}

}
