/*
 * Copyright 2012-2025 the original author or authors.
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

package org.springframework.samples.petclinic.scheduling.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.exhaustivesearch.ExhaustiveSearchPhaseConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.scheduling.model.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.model.CareType;
import org.springframework.samples.petclinic.scheduling.model.ReservationStatus;

class SolverConstraintTests {

	private SolverFactory<SchedulingProblem> solverFactory;

	@BeforeEach
	void setUp() {
		SolverConfig solverConfig = new SolverConfig().withSolutionClass(SchedulingProblem.class)
			.withEntityClasses(ProposedAssignment.class)
			.withConstraintProviderClass(AppointmentPlanningConstraintProvider.class)
			.withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(2)))
			.withPhaseList(List.of(new ExhaustiveSearchPhaseConfig()));

		solverFactory = SolverFactory.create(solverConfig);
	}

	@Test
	void testHardConstraintsPenalizeInvalidCandidates() {
		Instant now = Instant.parse("2026-08-28T09:00:00Z");

		CandidateSlot invalidDst = new CandidateSlot(1, 1, 1, now, now.plusSeconds(1800), false, true, true, true,
				false, true, true, false, false, false, false, 0);

		CandidateSlot invalidGrid = new CandidateSlot(1, 1, 1, now, now.plusSeconds(1800), true, false, true, true,
				false, true, true, false, false, false, false, 0);

		CandidateSlot invalidHorizon = new CandidateSlot(1, 1, 1, now, now.plusSeconds(1800), true, true, false, true,
				false, true, true, false, false, false, false, 0);

		CandidateSlot nonContiguous = new CandidateSlot(1, 1, 1, now, now.plusSeconds(1800), true, true, true, false,
				false, true, true, false, false, false, false, 0);

		CandidateSlot closureOverlap = new CandidateSlot(1, 1, 1, now, now.plusSeconds(1800), true, true, true, true,
				true, true, true, false, false, false, false, 0);

		CandidateSlot vetIneligible = new CandidateSlot(1, 1, 1, now, now.plusSeconds(1800), true, true, true, true,
				false, false, true, false, false, false, false, 0);

		CandidateSlot outsideAllowed = new CandidateSlot(1, 1, 1, now, now.plusSeconds(1800), true, true, true, true,
				false, true, false, false, false, false, false, 0);

		CandidateSlot insideExcluded = new CandidateSlot(1, 1, 1, now, now.plusSeconds(1800), true, true, true, true,
				false, true, true, true, false, false, false, 0);

		CandidateSlot exactRejected = new CandidateSlot(1, 1, 1, now, now.plusSeconds(1800), true, true, true, true,
				false, true, true, false, true, false, false, 0);

		CandidateSlot validSlot = new CandidateSlot(2, 1, 1, now.plusSeconds(7200), now.plusSeconds(9000), true, true,
				true, true, false, true, true, false, false, false, false, 0);

		List<CandidateSlot> candidates = List.of(invalidDst, invalidGrid, invalidHorizon, nonContiguous, closureOverlap,
				vetIneligible, outsideAllowed, insideExcluded, exactRejected, validSlot);

		SchedulingRequestFact requestFact = new SchedulingRequestFact(1, 1, 1, CareType.GENERAL, null, null, 30, now,
				now.plusSeconds(86400), null, null);
		ProposedAssignment assignment = new ProposedAssignment("assignment-1");

		SchedulingProblem problem = new SchedulingProblem(assignment, candidates, requestFact, List.of(), List.of());

		Solver<SchedulingProblem> solver = solverFactory.buildSolver();
		SchedulingProblem solution = solver.solve(problem);

		assertThat(solution.getProposedAssignment().getCandidate()).isNotNull();
		assertThat(solution.getProposedAssignment().getCandidate().getVeterinarianId()).isEqualTo(2);
		assertThat(solution.getScore().hardScore(0)).isEqualTo(0);
	}

	@Test
	void testAppointmentOverlapPenalized() {
		Instant now = Instant.parse("2026-08-28T09:00:00Z");

		CandidateSlot overlappingSlot = new CandidateSlot(1, 1, 1, now, now.plusSeconds(1800), true, true, true, true,
				false, true, true, false, false, false, false, 0);

		CandidateSlot nonOverlappingSlot = new CandidateSlot(1, 1, 1, now.plusSeconds(3600), now.plusSeconds(5400),
				true, true, true, true, false, true, true, false, false, false, false, 0);

		AppointmentFact existingAppt = new AppointmentFact(100, 1, 2, 2, now, now.plusSeconds(1800),
				AppointmentStatus.BOOKED);

		SchedulingRequestFact requestFact = new SchedulingRequestFact(1, 1, 1, CareType.GENERAL, null, null, 30, now,
				now.plusSeconds(86400), null, null);
		ProposedAssignment assignment = new ProposedAssignment("assignment-1");

		SchedulingProblem problem = new SchedulingProblem(assignment, List.of(overlappingSlot, nonOverlappingSlot),
				requestFact, List.of(existingAppt), List.of());

		Solver<SchedulingProblem> solver = solverFactory.buildSolver();
		SchedulingProblem solution = solver.solve(problem);

		assertThat(solution.getProposedAssignment().getCandidate()).isNotNull();
		assertThat(solution.getProposedAssignment().getCandidate().getStartInstant()).isEqualTo(now.plusSeconds(3600));
		assertThat(solution.getScore().hardScore(0)).isEqualTo(0);
	}

	@Test
	void testActiveReservationOverlapPenalized() {
		Instant now = Instant.parse("2026-08-28T09:00:00Z");

		CandidateSlot overlappingSlot = new CandidateSlot(1, 1, 1, now, now.plusSeconds(1800), true, true, true, true,
				false, true, true, false, false, false, false, 0);

		CandidateSlot nonOverlappingSlot = new CandidateSlot(1, 1, 1, now.plusSeconds(3600), now.plusSeconds(5400),
				true, true, true, true, false, true, true, false, false, false, false, 0);

		ReservationFact activeHold = new ReservationFact(200, 1, 2, 2, now, now.plusSeconds(1800),
				ReservationStatus.ACTIVE);

		SchedulingRequestFact requestFact = new SchedulingRequestFact(1, 1, 1, CareType.GENERAL, null, null, 30, now,
				now.plusSeconds(86400), null, null);
		ProposedAssignment assignment = new ProposedAssignment("assignment-1");

		SchedulingProblem problem = new SchedulingProblem(assignment, List.of(overlappingSlot, nonOverlappingSlot),
				requestFact, List.of(), List.of(activeHold));

		Solver<SchedulingProblem> solver = solverFactory.buildSolver();
		SchedulingProblem solution = solver.solve(problem);

		assertThat(solution.getProposedAssignment().getCandidate()).isNotNull();
		assertThat(solution.getProposedAssignment().getCandidate().getStartInstant()).isEqualTo(now.plusSeconds(3600));
		assertThat(solution.getScore().hardScore(0)).isEqualTo(0);
	}

	@Test
	void testLexicographicalSoftRankingPreferences() {
		Instant t1 = Instant.parse("2026-08-28T09:00:00Z");
		Instant t2 = Instant.parse("2026-08-28T10:00:00Z");

		// Slot A: preferred window = true, preferred vet = false, start = t2
		CandidateSlot slotA = new CandidateSlot(1, 1, 1, t2, t2.plusSeconds(1800), true, true, true, true, false, true,
				true, false, false, true, false, 120);

		// Slot B: preferred window = false, preferred vet = true, start = t1 (earlier)
		CandidateSlot slotB = new CandidateSlot(2, 1, 1, t1, t1.plusSeconds(1800), true, true, true, true, false, true,
				true, false, false, false, true, 0);

		SchedulingRequestFact requestFact = new SchedulingRequestFact(1, 1, 1, CareType.GENERAL, null, null, 30, t1,
				t2.plusSeconds(86400), null, null);
		ProposedAssignment assignment = new ProposedAssignment("assignment-1");

		SchedulingProblem problem = new SchedulingProblem(assignment, List.of(slotA, slotB), requestFact, List.of(),
				List.of());

		Solver<SchedulingProblem> solver = solverFactory.buildSolver();
		SchedulingProblem solution = solver.solve(problem);

		// Soft 0 (preferred window) has higher priority than Soft 1 (preferred vet) and
		// Soft 2 (earlier start), so slotA must win.
		assertThat(solution.getProposedAssignment().getCandidate()).isNotNull();
		assertThat(solution.getProposedAssignment().getCandidate().getStartInstant()).isEqualTo(t2);
	}

	@Test
	void testPreferredVetRankingWhenWindowsEqual() {
		Instant t1 = Instant.parse("2026-08-28T09:00:00Z");
		Instant t2 = Instant.parse("2026-08-28T10:00:00Z");

		// Both preferred window = false
		// Slot A: preferred vet = true, start = t2
		CandidateSlot slotA = new CandidateSlot(1, 1, 1, t2, t2.plusSeconds(1800), true, true, true, true, false, true,
				true, false, false, false, true, 120);

		// Slot B: preferred vet = false, start = t1 (earlier)
		CandidateSlot slotB = new CandidateSlot(2, 1, 1, t1, t1.plusSeconds(1800), true, true, true, true, false, true,
				true, false, false, false, false, 0);

		SchedulingRequestFact requestFact = new SchedulingRequestFact(1, 1, 1, CareType.GENERAL, null, null, 30, t1,
				t2.plusSeconds(86400), null, null);
		ProposedAssignment assignment = new ProposedAssignment("assignment-1");

		SchedulingProblem problem = new SchedulingProblem(assignment, List.of(slotA, slotB), requestFact, List.of(),
				List.of());

		Solver<SchedulingProblem> solver = solverFactory.buildSolver();
		SchedulingProblem solution = solver.solve(problem);

		// Soft 1 (preferred vet) is higher priority than Soft 2 (earlier start), so slotA
		// must win.
		assertThat(solution.getProposedAssignment().getCandidate()).isNotNull();
		assertThat(solution.getProposedAssignment().getCandidate().getVeterinarianId()).isEqualTo(1);
	}

	@Test
	void testEarlierStartRankingWhenPreferencesEqual() {
		Instant t1 = Instant.parse("2026-08-28T09:00:00Z");
		Instant t2 = Instant.parse("2026-08-28T10:00:00Z");

		// Both window = false, vet = false
		CandidateSlot slotA = new CandidateSlot(1, 1, 1, t2, t2.plusSeconds(1800), true, true, true, true, false, true,
				true, false, false, false, false, 0);

		CandidateSlot slotB = new CandidateSlot(1, 1, 1, t1, t1.plusSeconds(1800), true, true, true, true, false, true,
				true, false, false, false, false, 120);

		SchedulingRequestFact requestFact = new SchedulingRequestFact(1, 1, 1, CareType.GENERAL, null, null, 30, t1,
				t2.plusSeconds(86400), null, null);
		ProposedAssignment assignment = new ProposedAssignment("assignment-1");

		SchedulingProblem problem = new SchedulingProblem(assignment, List.of(slotA, slotB), requestFact, List.of(),
				List.of());

		Solver<SchedulingProblem> solver = solverFactory.buildSolver();
		SchedulingProblem solution = solver.solve(problem);

		// Soft 2 (earlier start) chooses t1 over t2 even though slotB has more workload.
		assertThat(solution.getProposedAssignment().getCandidate()).isNotNull();
		assertThat(solution.getProposedAssignment().getCandidate().getStartInstant()).isEqualTo(t1);
	}

}
