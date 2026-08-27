/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.scheduling.solver;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimefoldSlotSolverTests {

	private final TimefoldSlotSolver solver = new TimefoldSlotSolver(
			SolverFactory.create(new SolverConfig().withSolutionClass(SlotSelection.class)
				.withEntityClasses(SlotAssignment.class)
				.withEasyScoreCalculatorClass(SlotSelectionScoreCalculator.class)
				.withTerminationSpentLimit(Duration.ofMillis(50))));

	@Test
	void selectsUsingLexicographicRankingAndDeterministicTieBreak() {
		Instant early = Instant.parse("2026-09-01T08:00:00Z");
		Instant late = Instant.parse("2026-09-01T10:00:00Z");
		List<RankedSlot> candidates = List.of(new RankedSlot(3, early, 1, true), new RankedSlot(2, late, 2, false),
				new RankedSlot(1, late, 2, false));

		assertThat(this.solver.solve(candidates)).contains(new RankedSlot(1, late, 2, false));
	}

	@Test
	void preferredVetIsSoftAndSoonerBreaksOtherwiseEqualScores() {
		Instant early = Instant.parse("2026-09-01T08:00:00Z");
		Instant late = Instant.parse("2026-09-01T10:00:00Z");

		assertThat(this.solver.solve(List.of(new RankedSlot(2, late, 1, true), new RankedSlot(1, early, 1, false))))
			.contains(new RankedSlot(2, late, 1, true));
		assertThat(this.solver.solve(List.of(new RankedSlot(2, late, 1, false), new RankedSlot(1, early, 1, false))))
			.contains(new RankedSlot(1, early, 1, false));
	}

	@Test
	void emptyCandidateSetIsDefiniteNoFit() {
		assertThat(this.solver.solve(List.of())).isEmpty();
	}

}
