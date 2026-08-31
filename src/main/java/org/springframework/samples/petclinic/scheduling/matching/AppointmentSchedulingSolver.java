package org.springframework.samples.petclinic.scheduling.matching;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AppointmentSchedulingSolver {

	private static final Logger log = LoggerFactory.getLogger(AppointmentSchedulingSolver.class);

	private final SolverFactory<AppointmentSchedulingSolution> solverFactory;

	public AppointmentSchedulingSolver(SolverFactory<AppointmentSchedulingSolution> solverFactory) {
		this.solverFactory = solverFactory;
	}

	public Optional<CandidateSlot> solve(List<CandidateSlot> candidateSlots) {
		if (candidateSlots == null || candidateSlots.isEmpty()) {
			log.info("No candidate slots available for solver");
			return Optional.empty();
		}

		log.info("Sending {} candidate slot(s) to solver:\n{}", candidateSlots.size(), candidateSlots);

		AppointmentAssignment assignment = new AppointmentAssignment(1L);
		AppointmentSchedulingSolution problem = new AppointmentSchedulingSolution(candidateSlots, assignment);

		Solver<AppointmentSchedulingSolution> solver = this.solverFactory.buildSolver();
		AppointmentSchedulingSolution solution;
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			Future<AppointmentSchedulingSolution> future = executor.submit(() -> solver.solve(problem));
			try {
				solution = future.get(5, TimeUnit.SECONDS);
			}
			catch (TimeoutException ex) {
				solver.terminateEarly();
				future.cancel(true);
				log.warn("Exhaustive matching did not complete within five seconds; discarding partial solution");
				return Optional.empty();
			}
			catch (Exception ex) {
				log.warn("Matching solve failed; category={}", ex.getClass().getSimpleName(), ex);
				return Optional.empty();
			}
		}

		if (solution == null || solution.getScore() == null || !solution.getScore().isFeasible()
				|| solution.getAssignment() == null || solution.getAssignment().getCandidateSlot() == null
				|| !candidateSlots.contains(solution.getAssignment().getCandidateSlot())) {
			log.warn("Solver returned infeasible or unassigned solution (Score: {}, Assignment: {})",
					solution != null ? solution.getScore() : "null",
					solution != null ? solution.getAssignment() : "null");
			return Optional.empty();
		}

		log.info("Solver result: selected slot = {} with score = {}", solution.getAssignment().getCandidateSlot(),
				solution.getScore());
		return Optional.of(solution.getAssignment().getCandidateSlot());
	}

	public List<CandidateSlot> rank(List<CandidateSlot> candidateSlots, int limit) {
		List<CandidateSlot> remaining = new ArrayList<>(candidateSlots);
		List<CandidateSlot> ranked = new ArrayList<>();
		while (!remaining.isEmpty() && ranked.size() < limit) {
			Optional<CandidateSlot> next = solve(remaining);
			if (next.isEmpty()) {
				break;
			}
			ranked.add(next.get());
			remaining.remove(next.get());
		}
		return List.copyOf(ranked);
	}

}
