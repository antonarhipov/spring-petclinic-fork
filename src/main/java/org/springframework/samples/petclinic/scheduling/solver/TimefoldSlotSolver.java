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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TimefoldSlotSolver implements SlotSolver {

	private static final Logger logger = LoggerFactory.getLogger(TimefoldSlotSolver.class);

	private final SolverFactory<SlotSelection> solverFactory;

	public TimefoldSlotSolver(SolverFactory<SlotSelection> solverFactory) {
		this.solverFactory = solverFactory;
	}

	@Override
	public Optional<RankedSlot> solve(List<RankedSlot> candidates) {
		if (candidates == null || candidates.isEmpty()) {
			logger.debug("Slot solver skipped because there are no candidates");
			return Optional.empty();
		}
		logger.debug("Slot solver started with {} candidates", candidates.size());
		SlotSelection solved = this.solverFactory.buildSolver().solve(new SlotSelection(candidates));
		RankedSlot selected = solved.getAssignment().getSlot();
		logger.debug("Slot solver completed with score={} and selectedSlot={}", solved.getScore(), selected);
		return Optional.ofNullable(selected);
	}

}
