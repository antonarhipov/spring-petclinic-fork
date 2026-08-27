package org.springframework.samples.petclinic.scheduling.matching;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.exhaustivesearch.ExhaustiveSearchPhaseConfig;
import ai.timefold.solver.core.config.exhaustivesearch.ExhaustiveSearchType;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.samples.petclinic.scheduling.model.AuditEventType;
import org.springframework.samples.petclinic.scheduling.model.CareType;
import org.springframework.samples.petclinic.scheduling.model.ClinicSettings;
import org.springframework.samples.petclinic.scheduling.model.OperationStatus;
import org.springframework.samples.petclinic.scheduling.model.OperationToken;
import org.springframework.samples.petclinic.scheduling.model.OperationType;
import org.springframework.samples.petclinic.scheduling.model.RequestStatus;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.repository.ClinicSettingsRepository;
import org.springframework.samples.petclinic.scheduling.repository.OperationTokenRepository;
import org.springframework.samples.petclinic.scheduling.repository.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.service.AuditService;
import org.springframework.samples.petclinic.scheduling.service.LockCoordinator;
import org.springframework.samples.petclinic.scheduling.service.ReservationService;
import org.springframework.samples.petclinic.scheduling.service.SchedulingRequestService;
import org.springframework.samples.petclinic.scheduling.service.SchedulingTimeService;
import org.springframework.samples.petclinic.vet.Specialty;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class MatchingCoordinator {

	private static final Logger log = LoggerFactory.getLogger(MatchingCoordinator.class);

	private final SolverFactory<SchedulingProblem> solverFactory;

	private final SchedulingProblemFactory problemFactory;

	private final ReservationService reservationService;

	private final SchedulingRequestService schedulingRequestService;

	private final SchedulingRequestRepository schedulingRequestRepository;

	private final OperationTokenRepository operationTokenRepository;

	private final VetRepository vetRepository;

	private final ClinicSettingsRepository clinicSettingsRepository;

	private final LockCoordinator lockCoordinator;

	private final SchedulingTimeService timeService;

	private final AuditService auditService;

	private final ThreadPoolExecutor executor;

	public MatchingCoordinator(SchedulingProblemFactory problemFactory, ReservationService reservationService,
			SchedulingRequestService schedulingRequestService, SchedulingRequestRepository schedulingRequestRepository,
			OperationTokenRepository operationTokenRepository, VetRepository vetRepository,
			ClinicSettingsRepository clinicSettingsRepository, LockCoordinator lockCoordinator,
			SchedulingTimeService timeService, AuditService auditService) {
		this.problemFactory = problemFactory;
		this.reservationService = reservationService;
		this.schedulingRequestService = schedulingRequestService;
		this.schedulingRequestRepository = schedulingRequestRepository;
		this.operationTokenRepository = operationTokenRepository;
		this.vetRepository = vetRepository;
		this.clinicSettingsRepository = clinicSettingsRepository;
		this.lockCoordinator = lockCoordinator;
		this.timeService = timeService;
		this.auditService = auditService;

		SolverConfig solverConfig = new SolverConfig().withSolutionClass(SchedulingProblem.class)
			.withEntityClasses(ProposedAssignment.class)
			.withConstraintProviderClass(AppointmentPlanningConstraintProvider.class)
			.withPhaseList(List
				.of(new ExhaustiveSearchPhaseConfig().withExhaustiveSearchType(ExhaustiveSearchType.BRANCH_AND_BOUND)))
			.withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(2)));

		this.solverFactory = SolverFactory.create(solverConfig);

		this.executor = new ThreadPoolExecutor(2, 4, 60L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(20),
				new MatchingThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
	}

	@PreDestroy
	public void shutdown() {
		executor.shutdown();
		try {
			if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
				executor.shutdownNow();
			}
		}
		catch (InterruptedException e) {
			executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	@Transactional
	public void dispatchMatching(Integer requestId, String username) {
		SchedulingRequest request = lockCoordinator.lockRequest(requestId);
		if (request == null || request.getStatus() != RequestStatus.MATCHING) {
			return;
		}

		ClinicSettings settings = clinicSettingsRepository.findById(1).orElseGet(ClinicSettings::new);
		Instant now = timeService.now();

		// Check specialty capacity (UC4-AC47)
		if (request.getCareType() == CareType.SPECIALTY) {
			boolean specialtyAvailable = false;
			if (request.getRequiredSpecialty() != null) {
				List<Vet> vets = (List<Vet>) vetRepository.findAll();
				for (Vet v : vets) {
					if (v.isActive()) {
						for (Specialty s : v.getSpecialties()) {
							if (s.isActive() && Objects.equals(s.getId(), request.getRequiredSpecialty().getId())) {
								specialtyAvailable = true;
								break;
							}
						}
					}
				}
			}
			if (!specialtyAvailable) {
				schedulingRequestService.fallbackToStaff(request, "NO_SPECIALTY_VET", now, settings);
				return;
			}
		}

		// Create OperationToken
		String tokenString = UUID.randomUUID().toString();
		OperationToken token = new OperationToken();
		token.setToken(tokenString);
		token.setRequest(request);
		token.setOperationType(OperationType.SOLVER_MATCHING);
		token.setStatus(OperationStatus.DISPATCHED);
		token.setDispatchedAt(now);
		token.setDeadline(now.plusSeconds(2));
		operationTokenRepository.save(token);

		auditService.recordEvent(AuditEventType.OPERATION_DISPATCHED, username != null ? username : "owner", "OWNER",
				"OPERATION", tokenString, "SOLVER_DISPATCHED",
				Map.of("requestId", String.valueOf(requestId), "deadline", token.getDeadline().toString()));

		// The solver task must only be submitted once the token and request state above
		// are guaranteed to be committed and visible to the async worker thread;
		// otherwise the worker may run before commit and find no token, silently
		// stranding the request in MATCHING (Dec-3: Post-Commit Operation Token). When
		// no transaction synchronization is active (e.g. a caller that bypasses the
		// Spring transactional proxy, such as direct unit tests), submit immediately.
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					submitSolverTask(tokenString, requestId, username, token);
				}
			});
		}
		else {
			submitSolverTask(tokenString, requestId, username, token);
		}
	}

	private void submitSolverTask(String tokenString, Integer requestId, String username, OperationToken token) {
		try {
			executor.submit(() -> runSolverTask(tokenString, requestId, username));
		}
		catch (RejectedExecutionException e) {
			token.setStatus(OperationStatus.FAILED);
			operationTokenRepository.save(token);
			Instant now = timeService.now();
			ClinicSettings settings = clinicSettingsRepository.findById(1).orElseGet(ClinicSettings::new);
			boolean fellBack = schedulingRequestService.fallbackToStaffIfMatching(requestId, "SOLVER_UNAVAILABLE", now,
					settings);
			if (fellBack) {
				auditService.recordEvent(AuditEventType.OPERATION_FAILED, "matching-coordinator", "SYSTEM", "OPERATION",
						tokenString, "EXECUTOR_SATURATED", Map.of("requestId", String.valueOf(requestId)));
			}
		}
	}

	void runSolverTask(String tokenString, Integer requestId, String username) {
		try {
			Instant now = timeService.now();
			OperationToken token = operationTokenRepository.findByToken(tokenString).orElse(null);
			if (token == null || token.getStatus() != OperationStatus.DISPATCHED) {
				return;
			}

			if (token.getDeadline() != null && token.getDeadline().isBefore(now)) {
				token.setStatus(OperationStatus.TIMED_OUT);
				operationTokenRepository.save(token);
				handleSolverFailure(requestId, "SOLVER_UNAVAILABLE", "Deadline expired before solver execution");
				return;
			}

			SchedulingRequest request = schedulingRequestRepository.findWithDetailsById(requestId).orElse(null);
			if (request == null || request.getStatus() != RequestStatus.MATCHING) {
				token.setStatus(OperationStatus.CANCELLED);
				operationTokenRepository.save(token);
				return;
			}

			ClinicSettings settings = clinicSettingsRepository.findById(1).orElseGet(ClinicSettings::new);
			SchedulingProblem problem = problemFactory.createProblem(request, settings, now);
			log.info(
					"Submitting scheduling problem to solver: requestId={}, candidateCount={}, appointmentCount={}, reservationCount={}, careType={}, durationMinutes={}",
					requestId, problem.getCandidateList().size(), problem.getAppointmentList().size(),
					problem.getReservationList().size(), problem.getRequestFact().getCareType(),
					problem.getRequestFact().getDurationMinutes());

			Solver<SchedulingProblem> solver = solverFactory.buildSolver();
			SchedulingProblem solution = solver.solve(problem);
			CandidateSlot selectedCandidate = solution != null && solution.getProposedAssignment() != null
					? solution.getProposedAssignment().getCandidate() : null;
			log.info("Solver completed: requestId={}, score={}, selectedCandidate={}", requestId,
					solution != null ? solution.getScore() : null, selectedCandidate);

			if (solution != null && solution.getProposedAssignment() != null
					&& solution.getProposedAssignment().getCandidate() != null && solution.getScore() != null
					&& solution.getScore().hardScore(0) >= 0) {
				CandidateSlot winner = solution.getProposedAssignment().getCandidate();
				reservationService.commitGuidedHold(tokenString, requestId, winner.getVeterinarianId(),
						winner.getStartInstant(), winner.getEndInstant(), username);
			}
			else {
				token.setStatus(OperationStatus.COMPLETED);
				operationTokenRepository.save(token);
				boolean fellBack = schedulingRequestService.fallbackToStaffIfMatching(requestId,
						"SUGGESTIONS_EXHAUSTED", now, settings);
				if (fellBack) {
					auditService.recordEvent(AuditEventType.REQUEST_EXHAUSTED, "solver", "SYSTEM", "SCHEDULING_REQUEST",
							String.valueOf(requestId), "SUGGESTIONS_EXHAUSTED", Map.of());
				}
			}
		}
		catch (Exception ex) {
			handleSolverFailure(requestId, "SOLVER_UNAVAILABLE", ex.getMessage());
		}
	}

	private void handleSolverFailure(Integer requestId, String fallbackReason, String details) {
		Instant now = timeService.now();
		ClinicSettings settings = clinicSettingsRepository.findById(1).orElseGet(ClinicSettings::new);
		boolean fellBack = schedulingRequestService.fallbackToStaffIfMatching(requestId, fallbackReason, now, settings);
		if (fellBack) {
			auditService.recordEvent(AuditEventType.OPERATION_FAILED, "solver", "SYSTEM", "SCHEDULING_REQUEST",
					String.valueOf(requestId), fallbackReason, Map.of("details", details != null ? details : ""));
		}
	}

	private static class MatchingThreadFactory implements ThreadFactory {

		private final AtomicInteger threadNumber = new AtomicInteger(1);

		@Override
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r, "matching-solver-" + threadNumber.getAndIncrement());
			t.setDaemon(true);
			return t;
		}

	}

}
