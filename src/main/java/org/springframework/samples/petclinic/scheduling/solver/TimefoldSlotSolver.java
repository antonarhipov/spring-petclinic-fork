/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.scheduling.solver;

import java.util.List;
import java.util.Optional;

import ai.timefold.solver.core.api.solver.SolverFactory;
import org.springframework.stereotype.Service;

@Service
public class TimefoldSlotSolver implements SlotSolver {

	private final SolverFactory<SlotSelection> solverFactory;

	public TimefoldSlotSolver(SolverFactory<SlotSelection> solverFactory) {
		this.solverFactory = solverFactory;
	}

	@Override
	public Optional<RankedSlot> solve(List<RankedSlot> candidates) {
		if (candidates == null || candidates.isEmpty()) {
			return Optional.empty();
		}
		SlotSelection solved = this.solverFactory.buildSolver().solve(new SlotSelection(candidates));
		return Optional.ofNullable(solved.getAssignment().getSlot());
	}

}
