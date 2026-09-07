package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.samples.petclinic.scheduling.interpretation.Interpreter.InterpretationException;
import org.springframework.samples.petclinic.scheduling.request.Interpretation;
import org.springframework.samples.petclinic.scheduling.request.InterpretationFailure;
import org.springframework.samples.petclinic.scheduling.request.InterpretationFailureKind;
import org.springframework.samples.petclinic.scheduling.request.InterpretationLauncher;
import org.springframework.samples.petclinic.scheduling.request.InterpretationOrigin;
import org.springframework.samples.petclinic.scheduling.request.InterpretationWindow;
import org.springframework.samples.petclinic.scheduling.request.RequestService;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;

@Component
public class InterpretationJobRunner implements InterpretationLauncher {

	static final Duration DEFAULT_DEADLINE = Duration.ofSeconds(120);

	private final SchedulingRequestRepository requests;

	private final PromptBuilder prompts;

	private final Interpreter interpreter;

	private final RequestService requestService;

	private final Duration deadline;

	private final EntityManager entityManager;

	private final Clock clock;

	private final JdbcTemplate jdbc;

	private final String modelTag;

	private final String promptVersion;

	private final Executor executor;

	@Autowired
	public InterpretationJobRunner(SchedulingRequestRepository requests, PromptBuilder prompts, Interpreter interpreter,
			@Lazy RequestService requestService, EntityManager entityManager, Clock clock, JdbcTemplate jdbc,
			@Qualifier("interpretationExecutor") Executor executor,
			@Value("${spring.ai.ollama.chat.model:ministral-3:14b}") String modelTag,
			@Value("${scheduling.prompt.version:v1}") String promptVersion,
			@Value("${scheduling.interpretation.deadline:120s}") Duration deadline) {
		this(requests, prompts, interpreter, requestService, deadline, entityManager, clock, jdbc, executor, modelTag,
				promptVersion);
	}

	InterpretationJobRunner(SchedulingRequestRepository requests, PromptBuilder prompts, Interpreter interpreter,
			RequestService requestService, Duration deadline) {
		this(requests, prompts, interpreter, requestService, deadline, null, Clock.systemDefaultZone(), null,
				ForkJoinPool.commonPool(), "ministral-3:14b", "v1");
	}

	InterpretationJobRunner(SchedulingRequestRepository requests, PromptBuilder prompts, Interpreter interpreter,
			RequestService requestService, Duration deadline, EntityManager entityManager, Clock clock,
			JdbcTemplate jdbc, Executor executor, String modelTag, String promptVersion) {
		this.requests = requests;
		this.prompts = prompts;
		this.interpreter = interpreter;
		this.requestService = requestService;
		this.deadline = deadline;
		this.entityManager = entityManager;
		this.clock = clock != null ? clock : Clock.systemDefaultZone();
		this.jdbc = jdbc;
		this.executor = executor;
		this.modelTag = modelTag != null ? modelTag : "ministral-3:14b";
		this.promptVersion = promptVersion != null ? promptVersion : "v1";
	}

	@Override
	public void launch(int requestId) {
		CompletableFuture.supplyAsync(() -> interpret(requestId), this.executor)
			.orTimeout(this.deadline.toMillis(), TimeUnit.MILLISECONDS)
			.whenComplete((result, failure) -> complete(requestId, result, failure));
	}

	private InterpretationResult interpret(int requestId) {
		SchedulingRequest request = this.requests.findById(requestId).orElse(null);
		if (request == null) {
			return null;
		}
		String prompt = this.prompts.build(request.getRequestText(), request.getCreatedDate());
		return this.interpreter.interpret(prompt).toCompletableFuture().join();
	}

	void complete(int requestId, InterpretationResult result, Throwable failure) {
		if (failure != null) {
			Throwable cause = unwrap(failure);
			if (cause instanceof TimeoutException || (cause instanceof InterpretationException exception
					&& exception.getKind() == Interpreter.FailureKind.TRANSPORT)) {
				this.requestService.aiUnavailable(requestId);
			}
			else if (cause instanceof InterpretationException exception
					&& exception.getKind() == Interpreter.FailureKind.UNPARSEABLE) {
				this.requestService.interpretationFailed(requestId, new InterpretationFailure(
						InterpretationFailureKind.UNPARSEABLE, exception.getRawOutput(), today(), now()));
			}
			else {
				String raw = (cause instanceof InterpretationException exception) ? exception.getRawOutput() : null;
				this.requestService.interpretationFailed(requestId,
						new InterpretationFailure(InterpretationFailureKind.UNPARSEABLE, raw, today(), now()));
			}
			return;
		}

		if (result == null) {
			return;
		}

		InterpretationResult.ModelOutput output = result.output();
		if (!output.understood()) {
			this.requestService.interpretationFailed(requestId, new InterpretationFailure(
					InterpretationFailureKind.NOT_UNDERSTOOD, result.rawJson(), today(), now()));
			return;
		}

		if (output.preferredWindows().isEmpty() && output.allowedWindows().isEmpty()) {
			this.requestService.interpretationFailed(requestId, new InterpretationFailure(
					InterpretationFailureKind.ZERO_WINDOWS, result.rawJson(), today(), now()));
			return;
		}

		Interpretation interpretation = mapToInterpretation(output, result.rawJson());
		this.requestService.interpretationSucceeded(requestId, interpretation);
	}

	public Interpretation mapToInterpretation(InterpretationResult.ModelOutput output, String rawJson) {
		List<String> offeredSpecialties = offeredSpecialties();
		String specialty;
		String specialtyLabel;
		if (output.specialty() == null) {
			specialty = null;
			specialtyLabel = output.specialtyLabel();
		}
		else if (offeredSpecialties.contains(output.specialty())) {
			specialty = output.specialty();
			specialtyLabel = output.specialtyLabel();
		}
		else if ("OTHER".equalsIgnoreCase(output.specialty())) {
			specialty = "OTHER";
			specialtyLabel = output.specialtyLabel();
		}
		else {
			specialty = "OTHER";
			specialtyLabel = output.specialtyLabel() != null ? output.specialtyLabel() : output.specialty();
		}

		Vet preferredVet = resolveVet(output.preferredVetId());

		Interpretation interpretation = new Interpretation(true, output.careType(), specialty, specialtyLabel,
				output.durationMinutes(), preferredVet, InterpretationOrigin.AI, rawJson, this.modelTag,
				this.promptVersion, today(), now());

		if (output.preferredWindows() != null) {
			for (InterpretationResult.Window window : output.preferredWindows()) {
				interpretation.addWindow(new InterpretationWindow("PREFERRED", window.weekday(), window.date(),
						window.start(), window.end()));
			}
		}
		if (output.allowedWindows() != null) {
			for (InterpretationResult.Window window : output.allowedWindows()) {
				interpretation.addWindow(new InterpretationWindow("ALLOWED", window.weekday(), window.date(),
						window.start(), window.end()));
			}
		}
		if (output.excludedWindows() != null) {
			for (InterpretationResult.Window window : output.excludedWindows()) {
				interpretation.addWindow(new InterpretationWindow("EXCLUDED", window.weekday(), window.date(),
						window.start(), window.end()));
			}
		}

		return interpretation;
	}

	private List<String> offeredSpecialties() {
		if (this.jdbc != null) {
			try {
				return this.jdbc.queryForList("select name from specialties", String.class);
			}
			catch (Exception ignored) {
			}
		}
		return List.of("radiology", "surgery", "dentistry");
	}

	private Vet resolveVet(Integer preferredVetId) {
		if (preferredVetId == null) {
			return null;
		}
		if (this.entityManager != null) {
			try {
				return this.entityManager.find(Vet.class, preferredVetId);
			}
			catch (Exception ignored) {
			}
		}
		return null;
	}

	private LocalDate today() {
		return LocalDate.now(this.clock);
	}

	private LocalTime now() {
		return LocalTime.now(this.clock);
	}

	private Throwable unwrap(Throwable failure) {
		Throwable current = failure;
		while (current instanceof CompletionException && current.getCause() != null) {
			current = current.getCause();
		}
		return current;
	}

}
