package org.springframework.samples.petclinic.scheduling.matching;

import ai.timefold.solver.core.api.score.BendableScore;
import ai.timefold.solver.core.api.score.calculator.EasyScoreCalculator;

public class SlotEasyScoreCalculator implements EasyScoreCalculator<SlotSelectionSolution, BendableScore> {

	@Override
	public BendableScore calculateScore(SlotSelectionSolution solution) {
		CandidateSlot slot = solution.getAssignment() == null ? null : solution.getAssignment().getSelectedSlot();
		return SlotScorePolicy.score(solution.getSnapshot(), slot).score();
	}

}
