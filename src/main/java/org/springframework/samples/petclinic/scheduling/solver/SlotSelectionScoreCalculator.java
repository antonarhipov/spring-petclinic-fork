/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.scheduling.solver;

import ai.timefold.solver.core.api.score.BendableScore;
import ai.timefold.solver.core.api.score.calculator.EasyScoreCalculator;

public class SlotSelectionScoreCalculator implements EasyScoreCalculator<SlotSelection, BendableScore> {

	@Override
	public BendableScore calculateScore(SlotSelection solution) {
		RankedSlot slot = solution.getAssignment().getSlot();
		if (slot == null) {
			return BendableScore.zero(1, 5);
		}
		long[] soft = { slot.windowRank(), slot.preferredVet() ? 1L : 0L, -slot.startInstant().getEpochSecond(),
				-slot.vetId().longValue(), -slot.startInstant().getEpochSecond() };
		return BendableScore.of(new long[] { 0L }, soft);
	}

}
