package org.springframework.samples.petclinic.scheduling.matching;

import java.util.List;
import java.util.Optional;

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

		AppointmentAssignment assignment = new AppointmentAssignment(1L);
		AppointmentSchedulingSolution problem = new AppointmentSchedulingSolution(candidateSlots, assignment);

		Solver<AppointmentSchedulingSolution> solver = this.solverFactory.buildSolver();
		AppointmentSchedulingSolution solution = solver.solve(problem);

		if (solution.getAssignment() == null || solution.getAssignment().getCandidateSlot() == null) {
			log.warn("Solver returned unassigned solution (Score: {})", solution.getScore());
			return Optional.empty();
		}

		log.info("Solver selected slot: {} with score: {}", solution.getAssignment().getCandidateSlot(),
				solution.getScore());
		return Optional.of(solution.getAssignment().getCandidateSlot());
	}

}
