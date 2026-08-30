package org.springframework.samples.petclinic.scheduling.matching;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;

@Component
public class TimefoldSlotSolver {

	private static final Logger logger = LoggerFactory.getLogger(TimefoldSlotSolver.class);

	private final SolverFactory<SlotSelectionSolution> solverFactory;

	public TimefoldSlotSolver(SolverFactory<SlotSelectionSolution> solverFactory) {
		this.solverFactory = solverFactory;
	}

	public SlotSelectionResult solve(SlotSelectionSnapshot snapshot, Instant deadline) {
		SlotSelectionSolution problem = new SlotSelectionSolution();
		problem.setSnapshot(snapshot);
		problem.setCandidates(snapshot.candidates());
		problem.setAssignment(new SlotAssignment(1L));
		Solver<SlotSelectionSolution> solver = this.solverFactory.buildSolver();
		AtomicBoolean expired = new AtomicBoolean(false);
		long remaining = Math.max(1, Duration.between(Instant.now(), deadline).toMillis());
		ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();
		watchdog.schedule(() -> {
			expired.set(true);
			solver.terminateEarly();
		}, remaining, TimeUnit.MILLISECONDS);
		logger.info("Timefold solving requestId={} mode={} with {} candidate slots, budget {}ms", snapshot.requestId(),
				snapshot.mode(), snapshot.candidates().size(), remaining);
		long startedAt = System.currentTimeMillis();
		try {
			SlotSelectionSolution solved = solver.solve(problem);
			long elapsedMs = System.currentTimeMillis() - startedAt;
			boolean natural = !expired.get() && !Instant.now().isAfter(deadline);
			if (!natural) {
				logger.warn("Timefold TIMEOUT for requestId={} after {}ms (budget {}ms, deadline {})",
						snapshot.requestId(), elapsedMs, remaining, deadline);
				return new SlotSelectionResult("TIMEOUT", null, SlotScorePolicy.score(snapshot, null), false);
			}
			CandidateSlot selected = solved.getAssignment().getSelectedSlot();
			SlotScoreComponents scored = SlotScorePolicy.score(snapshot, selected);
			if (selected == null || scored.score().hardScore(0) < 0) {
				long baseEligible = snapshot.candidates()
					.stream()
					.filter(c -> SlotScorePolicy.baseEligible(snapshot, c))
					.count();
				boolean anyBase = baseEligible > 0;
				String outcome = anyBase && snapshot.mode() == MatchingMode.PREFERRED_ONLY ? "NO_PREFERRED_MATCH"
						: "NO_FEASIBLE_SLOT";
				logger.info(
						"Timefold produced no bookable slot for requestId={} after {}ms: outcome={}, "
								+ "selected={}, hardScore={}, candidates={}, baseEligibleCandidates={}",
						snapshot.requestId(), elapsedMs, outcome, selected == null ? "none" : selected.id(),
						scored.score().hardScore(0), snapshot.candidates().size(), baseEligible);
				return new SlotSelectionResult(outcome, null, scored, true);
			}
			logger.info(
					"Timefold SELECTED slot for requestId={} after {}ms: veterinarianId={}, startAt={}, endAt={}, "
							+ "preferenceClass={}, score={}, explanation={}",
					snapshot.requestId(), elapsedMs, selected.veterinarianId(), selected.startAt(), selected.endAt(),
					selected.preferenceClass(), scored.score(), scored.publicExplanationCode());
			logger.debug("Timefold score components for requestId={}: {}", snapshot.requestId(), scored.components());
			return new SlotSelectionResult("SELECTED", selected, scored, true);
		}
		finally {
			watchdog.shutdownNow();
		}
	}

}
