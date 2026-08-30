package org.springframework.samples.petclinic.scheduling.matching;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.samples.petclinic.scheduling.appointment.HoldAcquisitionOutcome;
import org.springframework.samples.petclinic.scheduling.appointment.ReservationService;
import org.springframework.samples.petclinic.scheduling.appointment.StaleAcquisitionException;
import org.springframework.samples.petclinic.scheduling.audit.IntegrationExecution;
import org.springframework.samples.petclinic.scheduling.audit.IntegrationExecutionRepository;
import org.springframework.samples.petclinic.scheduling.config.SchedulingProperties;
import org.springframework.samples.petclinic.scheduling.job.IntegrationExecutionDispatcher;
import org.springframework.samples.petclinic.scheduling.queue.FallbackRoutingService;
import org.springframework.samples.petclinic.scheduling.request.OwnerResourceNotFoundException;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.system.StaleStateException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchingCoordinator {

	private final SchedulingRequestRepository requests;

	private final IntegrationExecutionRepository executions;

	private final IntegrationExecutionDispatcher dispatcher;

	private final SlotSelectionSnapshotFactory snapshots;

	private final TimefoldSlotSolver solver;

	private final ReservationService reservations;

	private final FallbackRoutingService fallback;

	private final SchedulingProperties properties;

	private final Clock clock;

	public MatchingCoordinator(SchedulingRequestRepository requests, IntegrationExecutionRepository executions,
			IntegrationExecutionDispatcher dispatcher, SlotSelectionSnapshotFactory snapshots,
			TimefoldSlotSolver solver, ReservationService reservations, FallbackRoutingService fallback,
			SchedulingProperties properties, Clock clock) {
		this.requests = requests;
		this.executions = executions;
		this.dispatcher = dispatcher;
		this.snapshots = snapshots;
		this.solver = solver;
		this.reservations = reservations;
		this.fallback = fallback;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional
	public UUID requestSuggestion(Long requestId, Integer ownerId, Integer expectedVersion, MatchingMode mode) {
		SchedulingRequest request = this.requests.findByIdAndOwnerId(requestId, ownerId)
			.orElseThrow(OwnerResourceNotFoundException::new);
		if (expectedVersion != null && request.getVersion() != null && !expectedVersion.equals(request.getVersion())) {
			throw new StaleStateException("stale", request.getState().name(), null);
		}
		RequestState required = mode == MatchingMode.ALLOWED_FALLBACK ? RequestState.AWAITING_FALLBACK_CHOICE
				: RequestState.READY_FOR_SUGGESTION;
		if (request.getState() != required) {
			throw new IllegalStateException("INVALID_STATE");
		}
		Instant now = Instant.now(this.clock);
		String triggerKey = request.getId() + ":" + request.getActiveRequestRevisionId() + ":" + mode
				+ ":TIMEFOLD_MATCH";
		IntegrationExecution existing = this.executions.findByTriggerKey(triggerKey).orElse(null);
		if (existing != null) {
			return existing.getId();
		}
		IntegrationExecution execution = new IntegrationExecution();
		execution.setId(UUID.randomUUID());
		execution.setKind("TIMEFOLD_MATCH");
		execution.setRequestId(request.getId());
		execution.setRequestRevisionId(request.getActiveRequestRevisionId());
		execution.setTriggerKey(triggerKey);
		execution.setState("PENDING");
		execution.setTriggeredAt(now);
		execution.setDeadlineAt(now.plus(this.properties.getMatchDeadline()));
		execution.setSchemaVersion("1.0");
		execution.setInputJson("{\"mode\":\"" + mode.name() + "\"}");
		this.executions.save(execution);
		request.setState(RequestState.MATCHING);
		request.setOwnerStatusCode("MATCHING");
		request.setUpdatedAt(now);
		this.dispatcher.dispatchAfterCommit(execution.getId());
		return execution.getId();
	}

	@Transactional
	public void forwardToStaff(Long requestId, Integer ownerId, Integer expectedVersion) {
		SchedulingRequest request = this.requests.findByIdAndOwnerId(requestId, ownerId)
			.orElseThrow(OwnerResourceNotFoundException::new);
		if (expectedVersion != null && request.getVersion() != null && !expectedVersion.equals(request.getVersion())) {
			throw new StaleStateException("stale", request.getState().name(), null);
		}
		request.setState(RequestState.STAFF_HANDLING);
		request.setOwnerStatusCode("STAFF_HANDLING");
		request.setUpdatedAt(Instant.now(this.clock));
		this.fallback.ensureQueueItem(requestId, "OWNER_FORWARD", request.isSuspectedEmergency());
	}

	public void execute(UUID executionId) {
		IntegrationExecution execution = this.executions.findById(executionId).orElseThrow();
		SchedulingRequest request = this.requests.findById(execution.getRequestId()).orElseThrow();
		if (request.getState() != RequestState.MATCHING
				|| !execution.getRequestRevisionId().equals(request.getActiveRequestRevisionId())) {
			execution.setState("SUPERSEDED");
			execution.setOutcome("SUPERSEDED");
			execution.setFinishedAt(Instant.now(this.clock));
			this.executions.save(execution);
			return;
		}
		MatchingMode mode = execution.getInputJson().contains("ALLOWED_FALLBACK") ? MatchingMode.ALLOWED_FALLBACK
				: MatchingMode.PREFERRED_ONLY;
		SlotSelectionSnapshot snapshot = this.snapshots.create(request.getId(), mode);
		SlotSelectionResult result = this.solver.solve(snapshot, execution.getDeadlineAt());
		apply(execution, request, snapshot, result, false);
	}

	private void apply(IntegrationExecution execution, SchedulingRequest request, SlotSelectionSnapshot snapshot,
			SlotSelectionResult result, boolean retried) {
		Instant now = Instant.now(this.clock);
		execution.setAttemptCount(execution.getAttemptCount() + 1);
		if ("SELECTED".equals(result.outcome())) {
			try {
				HoldAcquisitionOutcome hold = this.reservations.acquire(request.getId(), execution.getId(),
						result.selectedCandidate(), snapshot, result.score().publicExplanationCode());
				if (hold == HoldAcquisitionOutcome.HELD) {
					execution.setState("COMPLETE");
					execution.setOutcome("SELECTED");
					execution.setFinishedAt(now);
					this.executions.save(execution);
					return;
				}
				if (hold == HoldAcquisitionOutcome.STALE && !retried && now.isBefore(execution.getDeadlineAt())) {
					SlotSelectionSnapshot refreshed = this.snapshots.create(request.getId(), snapshot.mode());
					SlotSelectionResult retry = this.solver.solve(refreshed, execution.getDeadlineAt());
					apply(execution, request, refreshed, retry, true);
					return;
				}
				routeStaff(request, execution, "STALE_ACQUISITION", now);
				return;
			}
			catch (StaleAcquisitionException ex) {
				if (!retried && Instant.now(this.clock).isBefore(execution.getDeadlineAt())) {
					SlotSelectionSnapshot refreshed = this.snapshots.create(request.getId(), snapshot.mode());
					SlotSelectionResult retry = this.solver.solve(refreshed, execution.getDeadlineAt());
					apply(execution, request, refreshed, retry, true);
					return;
				}
				routeStaff(request, execution, "STALE_ACQUISITION", now);
				return;
			}
		}
		if ("NO_PREFERRED_MATCH".equals(result.outcome())) {
			request.setState(RequestState.AWAITING_FALLBACK_CHOICE);
			request.setOwnerStatusCode("AWAITING_FALLBACK_CHOICE");
			request.setUpdatedAt(now);
			execution.setState("COMPLETE");
			execution.setOutcome("NO_PREFERRED_MATCH");
			execution.setFinishedAt(now);
			this.executions.save(execution);
			return;
		}
		routeStaff(request, execution, result.outcome(), now);
	}

	private void routeStaff(SchedulingRequest request, IntegrationExecution execution, String outcome, Instant now) {
		request.setState(RequestState.STAFF_HANDLING);
		request.setOwnerStatusCode("STAFF_HANDLING");
		request.setUpdatedAt(now);
		this.fallback.ensureQueueItem(request.getId(), outcome, request.isSuspectedEmergency());
		execution.setState("COMPLETE");
		execution.setOutcome(outcome);
		execution.setFinishedAt(now);
		this.executions.save(execution);
	}

}
