package org.springframework.samples.petclinic.scheduling.matching;

import java.util.List;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.ProblemFactProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.BendableScore;

@PlanningSolution
public class SlotSelectionSolution {

	private SlotSelectionSnapshot snapshot;

	private List<CandidateSlot> candidates;

	private SlotAssignment assignment;

	private BendableScore score;

	@ProblemFactProperty
	public SlotSelectionSnapshot getSnapshot() {
		return this.snapshot;
	}

	public void setSnapshot(SlotSelectionSnapshot snapshot) {
		this.snapshot = snapshot;
	}

	@ProblemFactCollectionProperty
	@ValueRangeProvider(id = "candidateRange")
	public List<CandidateSlot> getCandidates() {
		return this.candidates;
	}

	public void setCandidates(List<CandidateSlot> candidates) {
		this.candidates = candidates;
	}

	@PlanningEntityProperty
	public SlotAssignment getAssignment() {
		return this.assignment;
	}

	public void setAssignment(SlotAssignment assignment) {
		this.assignment = assignment;
	}

	@PlanningScore(bendableHardLevelsSize = 1, bendableSoftLevelsSize = 4)
	public BendableScore getScore() {
		return this.score;
	}

	public void setScore(BendableScore score) {
		this.score = score;
	}

}
