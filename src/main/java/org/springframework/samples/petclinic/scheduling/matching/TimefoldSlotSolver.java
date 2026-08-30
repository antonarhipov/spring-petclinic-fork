package org.springframework.samples.petclinic.scheduling.matching;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;

@Component
public class TimefoldSlotSolver {

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
		try {
			SlotSelectionSolution solved = solver.solve(problem);
			boolean natural = !expired.get() && !Instant.now().isAfter(deadline);
			if (!natural) {
				return new SlotSelectionResult("TIMEOUT", null, SlotScorePolicy.score(snapshot, null), false);
			}
			CandidateSlot selected = solved.getAssignment().getSelectedSlot();
			SlotScoreComponents scored = SlotScorePolicy.score(snapshot, selected);
			if (selected == null || scored.score().hardScore(0) < 0) {
				boolean anyBase = snapshot.candidates()
					.stream()
					.anyMatch(c -> SlotScorePolicy.baseEligible(snapshot, c));
				String outcome = anyBase && snapshot.mode() == MatchingMode.PREFERRED_ONLY ? "NO_PREFERRED_MATCH"
						: "NO_FEASIBLE_SLOT";
				return new SlotSelectionResult(outcome, null, scored, true);
			}
			return new SlotSelectionResult("SELECTED", selected, scored, true);
		}
		finally {
			watchdog.shutdownNow();
		}
	}

}
