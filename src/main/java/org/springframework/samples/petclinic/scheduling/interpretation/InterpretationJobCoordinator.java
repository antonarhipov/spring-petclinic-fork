package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.samples.petclinic.audit.ProtectedPayload;
import org.springframework.samples.petclinic.audit.ProtectedPayloadService;
import org.springframework.samples.petclinic.audit.AuditService;
import org.springframework.samples.petclinic.availability.ClinicPolicy;
import org.springframework.samples.petclinic.availability.ClinicPolicyRepository;
import org.springframework.samples.petclinic.config.OllamaConfiguration.OllamaProperties;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.job.BackgroundJob;
import org.springframework.samples.petclinic.scheduling.job.BackgroundJobRepository;
import org.springframework.samples.petclinic.scheduling.job.JobState;
import org.springframework.samples.petclinic.scheduling.job.OutcomeCategory;
import org.springframework.samples.petclinic.scheduling.queue.StaffFallbackPort;
import org.springframework.samples.petclinic.scheduling.request.AvailabilityWindow;
import org.springframework.samples.petclinic.scheduling.request.Interpretation;
import org.springframework.samples.petclinic.scheduling.request.InterpretationRepository;
import org.springframework.samples.petclinic.scheduling.request.InterpretationSource;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.TextRevision;
import org.springframework.samples.petclinic.scheduling.request.ValidationState;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevision;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevisionRepository;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevisionState;
import org.springframework.samples.petclinic.vet.Specialty;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class InterpretationJobCoordinator {

	private static final Logger log = LoggerFactory.getLogger(InterpretationJobCoordinator.class);

	private final InterpretationClient interpretationClient;

	private final InterpretationOutputValidator validator;

	private final EmergencyKeywordScreen emergencyScreen;

	private final ProtectedPayloadService payloadService;

	private final AuditService auditService;

	private final StaffFallbackPort staffFallbackPort;

	private final BackgroundJobRepository jobRepository;

	private final SchedulingRequestRepository requestRepository;

	private final InterpretationRepository interpretationRepository;

	private final WorkflowRevisionRepository workflowRevisionRepository;

	private final ClinicPolicyRepository clinicPolicyRepository;

	private final OwnerRepository ownerRepository;

	private final VetRepository vetRepository;

	private final ObjectMapper objectMapper;

	private final Clock clock;

	private final TransactionTemplate transactionTemplate;

	private final Duration interpretationTimeout;

	public InterpretationJobCoordinator(InterpretationClient interpretationClient,
			InterpretationOutputValidator validator, EmergencyKeywordScreen emergencyScreen,
			ProtectedPayloadService payloadService, AuditService auditService, StaffFallbackPort staffFallbackPort,
			BackgroundJobRepository jobRepository, SchedulingRequestRepository requestRepository,
			InterpretationRepository interpretationRepository, WorkflowRevisionRepository workflowRevisionRepository,
			ClinicPolicyRepository clinicPolicyRepository, OwnerRepository ownerRepository, VetRepository vetRepository,
			ObjectMapper objectMapper, Clock clock) {
		this(interpretationClient, validator, emergencyScreen, payloadService, auditService, staffFallbackPort,
				jobRepository, requestRepository, interpretationRepository, workflowRevisionRepository,
				clinicPolicyRepository, ownerRepository, vetRepository, objectMapper, clock, Duration.ofSeconds(10),
				null);
	}

	InterpretationJobCoordinator(InterpretationClient interpretationClient, InterpretationOutputValidator validator,
			EmergencyKeywordScreen emergencyScreen, ProtectedPayloadService payloadService, AuditService auditService,
			StaffFallbackPort staffFallbackPort, BackgroundJobRepository jobRepository,
			SchedulingRequestRepository requestRepository, InterpretationRepository interpretationRepository,
			WorkflowRevisionRepository workflowRevisionRepository, ClinicPolicyRepository clinicPolicyRepository,
			OwnerRepository ownerRepository, VetRepository vetRepository, ObjectMapper objectMapper, Clock clock,
			Duration interpretationTimeout) {
		this(interpretationClient, validator, emergencyScreen, payloadService, auditService, staffFallbackPort,
				jobRepository, requestRepository, interpretationRepository, workflowRevisionRepository,
				clinicPolicyRepository, ownerRepository, vetRepository, objectMapper, clock, interpretationTimeout,
				null);
	}

	@Autowired
	public InterpretationJobCoordinator(InterpretationClient interpretationClient,
			InterpretationOutputValidator validator, EmergencyKeywordScreen emergencyScreen,
			ProtectedPayloadService payloadService, AuditService auditService, StaffFallbackPort staffFallbackPort,
			BackgroundJobRepository jobRepository, SchedulingRequestRepository requestRepository,
			InterpretationRepository interpretationRepository, WorkflowRevisionRepository workflowRevisionRepository,
			ClinicPolicyRepository clinicPolicyRepository, OwnerRepository ownerRepository, VetRepository vetRepository,
			ObjectMapper objectMapper, Clock clock, OllamaProperties ollamaProperties,
			PlatformTransactionManager transactionManager) {
		this(interpretationClient, validator, emergencyScreen, payloadService, auditService, staffFallbackPort,
				jobRepository, requestRepository, interpretationRepository, workflowRevisionRepository,
				clinicPolicyRepository, ownerRepository, vetRepository, objectMapper, clock,
				Duration.ofSeconds(ollamaProperties.getTimeoutSeconds()), new TransactionTemplate(transactionManager));
	}

	private InterpretationJobCoordinator(InterpretationClient interpretationClient,
			InterpretationOutputValidator validator, EmergencyKeywordScreen emergencyScreen,
			ProtectedPayloadService payloadService, AuditService auditService, StaffFallbackPort staffFallbackPort,
			BackgroundJobRepository jobRepository, SchedulingRequestRepository requestRepository,
			InterpretationRepository interpretationRepository, WorkflowRevisionRepository workflowRevisionRepository,
			ClinicPolicyRepository clinicPolicyRepository, OwnerRepository ownerRepository, VetRepository vetRepository,
			ObjectMapper objectMapper, Clock clock, Duration interpretationTimeout,
			TransactionTemplate transactionTemplate) {
		this.interpretationClient = interpretationClient;
		this.validator = validator;
		this.emergencyScreen = emergencyScreen;
		this.payloadService = payloadService;
		this.auditService = auditService;
		this.staffFallbackPort = staffFallbackPort;
		this.jobRepository = jobRepository;
		this.requestRepository = requestRepository;
		this.interpretationRepository = interpretationRepository;
		this.workflowRevisionRepository = workflowRevisionRepository;
		this.clinicPolicyRepository = clinicPolicyRepository;
		this.ownerRepository = ownerRepository;
		this.vetRepository = vetRepository;
		this.objectMapper = objectMapper;
		this.clock = clock;
		this.interpretationTimeout = interpretationTimeout;
		this.transactionTemplate = transactionTemplate;
	}

	public void executeInterpretationJob(Long jobId, String leaseToken) {
		BackgroundJob job = inTransaction(() -> this.jobRepository.findById(jobId)
			.orElseThrow(() -> new IllegalArgumentException("Background job not found: " + jobId)));
		if (job.getState() != JobState.RUNNING || !Objects.equals(job.getLeaseToken(), leaseToken)) {
			return;
		}
		executeInterpretationJob(job);
	}

	public void executeInterpretationJob(BackgroundJob job) {
		PreparedInterpretation prepared = inTransaction(() -> prepare(job));
		if (prepared == null) {
			return;
		}

		int maxAttempts = 2;
		InterpretationCandidate candidate = null;
		int attempts = 0;
		long deadlineNanos = System.nanoTime() + this.interpretationTimeout.toNanos();
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			attempts = attempt;
			try {
				candidate = interpretWithinBudget(prepared.prompt(), deadlineNanos);
				InterpretationOutputValidator.ValidationResult validationResult = this.validator.validate(candidate,
						prepared.policy());
				if (validationResult.valid()) {
					log.info("Interpretation attempt {} passed schema validation: {}", attempt, candidate);
					break;
				}
				log.warn("Interpretation attempt {} failed schema validation; errorCount={}, errors={}", attempt,
						validationResult.errors().size(), validationResult.errors());
				candidate = null;
			}
			catch (Exception ex) {
				log.warn("Interpretation attempt {} failed; correlationId={}, category={}", attempt, UUID.randomUUID(),
						ex.getClass().getSimpleName());
				candidate = null;
				if (System.nanoTime() >= deadlineNanos || ex instanceof TimeoutException) {
					break;
				}
			}
		}

		InterpretationCandidate finalCandidate = candidate;
		int finalAttempts = attempts;
		inTransaction(() -> {
			commit(prepared, finalCandidate, finalAttempts);
			return null;
		});
	}

	private PreparedInterpretation prepare(BackgroundJob suppliedJob) {
		BackgroundJob job = reload(suppliedJob);
		TextRevision textRevision = job.getTextRevision();
		if (textRevision == null) {
			log.warn("Job {} has no text revision", job.getId());
			job.setState(JobState.FAILED);
			job.setOutcomeCategory(OutcomeCategory.ERROR);
			this.jobRepository.save(job);
			return null;
		}

		SchedulingRequest request = textRevision.getRequest();
		if (request.getCurrentTextRevision() == null
				|| !request.getCurrentTextRevision().getId().equals(textRevision.getId())
				|| request.getState().isTerminal()) {
			log.info("Job {} is stale for request {}", job.getId(), request.getId());
			job.setState(JobState.STALE);
			this.jobRepository.save(job);
			return null;
		}

		Instant now = this.clock.instant();
		String prose = this.payloadService.decrypt(textRevision.getProsePayload(), String.class);

		// 1. Emergency keyword screen
		EmergencyKeywordScreen.EmergencyScreenResult emergencyResult = this.emergencyScreen.screen(prose);
		this.auditService.recordEvent(null, "EMERGENCY_SCREEN", "TextRevision", textRevision.getId().toString(),
				emergencyResult.emergencyDetected() ? "MATCH" : "CLEAR", null, null, null);
		if (emergencyResult.emergencyDetected()) {
			log.warn("Emergency detected in prose for request {}", request.getId());
			handleEmergencyDetected(job, request, textRevision, prose, emergencyResult, now);
			return null;
		}

		// 2. Build prompt
		ClinicPolicy policy = this.clinicPolicyRepository.findSingleton().orElseGet(ClinicPolicy::createDefaultPolicy);
		InterpretationPrompt prompt = buildPrompt(request, prose, textRevision.getSubmittedAt(), policy);

		return new PreparedInterpretation(suppliedJob, job.getId(), job.getLeaseToken(), request.getId(),
				textRevision.getId(), prompt, policy);
	}

	private void commit(PreparedInterpretation prepared, InterpretationCandidate candidate, int attempts) {
		BackgroundJob job = reload(prepared.suppliedJob());
		if (job.getState() != JobState.RUNNING || !Objects.equals(job.getLeaseToken(), prepared.leaseToken())) {
			return;
		}
		TextRevision textRevision = job.getTextRevision();
		SchedulingRequest request = textRevision != null ? textRevision.getRequest() : null;
		if (textRevision == null || request == null || !Objects.equals(textRevision.getId(), prepared.textRevisionId())
				|| request.getCurrentTextRevision() == null
				|| !Objects.equals(request.getCurrentTextRevision().getId(), prepared.textRevisionId())
				|| request.getState().isTerminal()) {
			job.setState(JobState.STALE);
			this.jobRepository.save(job);
			return;
		}
		job.setAttemptCount(attempts);
		Instant now = this.clock.instant();
		if (candidate != null) {
			handleSuccessfulInterpretation(job, request, textRevision, candidate, prepared.policy(), now);
		}
		else {
			handleUnusableInterpretation(job, request, textRevision, now);
		}
	}

	private BackgroundJob reload(BackgroundJob job) {
		if (job.getId() == null) {
			return job;
		}
		return this.jobRepository.findById(job.getId()).orElse(job);
	}

	private <T> T inTransaction(Supplier<T> action) {
		if (this.transactionTemplate == null) {
			return action.get();
		}
		return this.transactionTemplate.execute(status -> action.get());
	}

	private record PreparedInterpretation(BackgroundJob suppliedJob, Long jobId, String leaseToken, Long requestId,
			Long textRevisionId, InterpretationPrompt prompt, ClinicPolicy policy) {
	}

	private InterpretationCandidate interpretWithinBudget(InterpretationPrompt prompt, long deadlineNanos)
			throws InterruptedException, ExecutionException, TimeoutException {
		long remainingNanos = deadlineNanos - System.nanoTime();
		if (remainingNanos <= 0) {
			throw new TimeoutException("Interpretation deadline exhausted");
		}
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			Future<InterpretationCandidate> future = executor.submit(() -> this.interpretationClient.interpret(prompt));
			try {
				return future.get(remainingNanos, TimeUnit.NANOSECONDS);
			}
			catch (TimeoutException ex) {
				future.cancel(true);
				throw ex;
			}
		}
	}

	private void handleEmergencyDetected(BackgroundJob job, SchedulingRequest request, TextRevision textRevision,
			String prose, EmergencyKeywordScreen.EmergencyScreenResult emergencyResult, Instant now) {
		ProtectedPayload reasonPayload = this.payloadService.encrypt("EMERGENCY: " + prose);

		Interpretation interpretation = new Interpretation(textRevision, InterpretationSource.AI,
				"emergency-keyword-screen", null, null, ValidationState.VALID, now);
		Interpretation savedInterpretation = this.interpretationRepository.save(interpretation);

		WorkflowRevision workflowRevision = new WorkflowRevision(request, 1, textRevision, savedInterpretation,
				WorkflowRevisionState.OWNER_CONFIRMATION_REQUIRED, reasonPayload, 30, null, null,
				Urgency.EMERGENCY_SUSPECTED, 0);
		WorkflowRevision savedWorkflowRev = this.workflowRevisionRepository.save(workflowRevision);

		request.setCurrentWorkflowRevision(savedWorkflowRev);
		request.setState(RequestState.STAFF_HANDLING);
		request.setUpdatedAt(now);
		this.requestRepository.save(request);

		job.setState(JobState.SUCCEEDED);
		job.setOutcomeCategory(OutcomeCategory.EMERGENCY_DETECTED);
		job.setCompletedAt(now);
		this.jobRepository.save(job);
		this.auditService.recordStructuredEvent(null, "AI_EMERGENCY_SCREEN_ROUTED", "SchedulingRequest",
				request.getId().toString(), "SUCCESS", null, null,
				Map.of("textRevisionId", textRevision.getId(), "requestState",
						RequestState.AWAITING_INTERPRETATION.name()),
				Map.of("textRevisionId", textRevision.getId(), "requestState", request.getState().name(),
						"jobCommandId", job.getCommandId(), "outcome", job.getOutcomeCategory().name()));

		this.staffFallbackPort.sendToFallbackQueue(request.getId(), "EMERGENCY_DETECTED", Urgency.EMERGENCY_SUSPECTED,
				"Deterministic emergency screen requires immediate staff review");
	}

	private void handleSuccessfulInterpretation(BackgroundJob job, SchedulingRequest request, TextRevision textRevision,
			InterpretationCandidate candidate, ClinicPolicy policy, Instant now) {
		try {
			String rawJson = this.objectMapper.writeValueAsString(candidate);
			ProtectedPayload validatedPayload = this.payloadService.encrypt(rawJson);
			ProtectedPayload reasonPayload = this.payloadService.encrypt(candidate.visitReason());

			Interpretation interpretation = new Interpretation(textRevision, InterpretationSource.AI, "ollama",
					validatedPayload, validatedPayload, ValidationState.VALID, now);
			Interpretation savedInterpretation = this.interpretationRepository.save(interpretation);

			Integer preferredVetId = (candidate.preferredVeterinarian() != null)
					? candidate.preferredVeterinarian().id() : null;
			Integer requiredSpecialtyId = (candidate.requiredSpecialty() != null) ? candidate.requiredSpecialty().id()
					: null;

			WorkflowRevision workflowRevision = new WorkflowRevision(request, 1, textRevision, savedInterpretation,
					WorkflowRevisionState.OWNER_CONFIRMATION_REQUIRED, reasonPayload, candidate.durationMinutes(),
					preferredVetId, requiredSpecialtyId, candidate.urgency(), 0);

			if (candidate.availability() != null) {
				for (WindowCandidate wc : candidate.availability()) {
					String weekdaysStr = (wc.weekdays() != null)
							? wc.weekdays().stream().map(Enum::name).collect(Collectors.joining(",")) : null;
					AvailabilityWindow window = new AvailabilityWindow(workflowRevision, wc.classification(),
							wc.shape(), wc.date(), wc.rangeStart(), wc.rangeEnd(), weekdaysStr, wc.startTime(),
							wc.endTime(), wc.sourceText(), wc.resolutionNote());
					workflowRevision.addAvailabilityWindow(window);
				}
			}

			WorkflowRevision savedWorkflowRev = this.workflowRevisionRepository.save(workflowRevision);

			request.setCurrentWorkflowRevision(savedWorkflowRev);
			request.setState(RequestState.AWAITING_REVIEW);
			request.setUpdatedAt(now);
			this.requestRepository.save(request);

			job.setState(JobState.SUCCEEDED);
			job.setOutcomeCategory(OutcomeCategory.SUCCESS);
			job.setCompletedAt(now);
			this.jobRepository.save(job);
			this.auditService.recordStructuredEvent(null, "AI_INTERPRETATION_COMPLETED", "SchedulingRequest",
					request.getId().toString(), "SUCCESS", null, null,
					Map.of("textRevisionId", textRevision.getId(), "requestState",
							RequestState.AWAITING_INTERPRETATION.name()),
					Map.of("workflowRevisionId",
							String.valueOf(savedWorkflowRev != null ? savedWorkflowRev.getId() : null), "requestState",
							request.getState().name(), "jobCommandId", job.getCommandId(), "outcome",
							job.getOutcomeCategory().name()));
		}
		catch (Exception ex) {
			log.error("Failed to commit interpretation; correlationId={}, category={}", UUID.randomUUID(),
					ex.getClass().getSimpleName());
			handleUnusableInterpretation(job, request, textRevision, now);
		}
	}

	private void handleUnusableInterpretation(BackgroundJob job, SchedulingRequest request, TextRevision textRevision,
			Instant now) {
		Interpretation interpretation = new Interpretation(textRevision, InterpretationSource.AI, "ollama", null, null,
				ValidationState.UNUSABLE, now);
		this.interpretationRepository.save(interpretation);

		request.setState(RequestState.STAFF_HANDLING);
		request.setUpdatedAt(now);
		this.requestRepository.save(request);

		job.setState(JobState.FAILED);
		job.setOutcomeCategory(OutcomeCategory.UNUSABLE_OUTPUT);
		job.setCompletedAt(now);
		this.jobRepository.save(job);
		this.auditService.recordStructuredEvent(null, "AI_INTERPRETATION_FAILED", "SchedulingRequest",
				request.getId().toString(), "FALLBACK", null, null,
				Map.of("textRevisionId", textRevision.getId(), "requestState",
						RequestState.AWAITING_INTERPRETATION.name()),
				Map.of("requestState", request.getState().name(), "jobCommandId", job.getCommandId(), "outcome",
						job.getOutcomeCategory().name()));

		this.staffFallbackPort.sendToFallbackQueue(request.getId(), "UNUSABLE_OUTPUT", Urgency.ROUTINE,
				"Automated interpretation failed or produced unusable output");
	}

	private InterpretationPrompt buildPrompt(SchedulingRequest request, String prose, Instant submittedAt,
			ClinicPolicy policy) {
		Owner owner = this.ownerRepository.findById(request.getOwnerId()).orElse(null);
		Pet pet = null;
		if (owner != null) {
			pet = owner.getPets().stream().filter(p -> p.getId().equals(request.getPetId())).findFirst().orElse(null);
		}

		String petName = (pet != null) ? pet.getName() : "Pet";
		String petTypeName = (pet != null && pet.getType() != null) ? pet.getType().getName() : "Animal";

		List<CatalogChoice> vets = this.vetRepository.findAll()
			.stream()
			.map(v -> new CatalogChoice(v.getId(), v.getFirstName() + " " + v.getLastName()))
			.toList();

		List<CatalogChoice> specialties = this.vetRepository.findSpecialties()
			.stream()
			.map(s -> new CatalogChoice(s.getId(), s.getName()))
			.toList();

		return new InterpretationPrompt(prose, petName, petTypeName, policy.getTimeZone(),
				policy.getAllowedDurations().stream().toList(), vets, specialties, submittedAt);
	}

}
