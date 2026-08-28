package org.springframework.samples.petclinic.scheduling.solver;

import java.util.List;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.HardSoftScore;

@PlanningSolution
public class AppointmentPlanningSolution {

	@ProblemFactCollectionProperty
	@ValueRangeProvider(id = "candidateRange")
	private List<CandidateSlot> candidates;

	@PlanningEntityCollectionProperty
	private List<AppointmentPlanningEntity> selections;

	@PlanningScore
	private HardSoftScore score;

	public AppointmentPlanningSolution() {
	}

	public AppointmentPlanningSolution(List<CandidateSlot> candidates, List<AppointmentPlanningEntity> selections) {
		this.candidates = candidates;
		this.selections = selections;
	}

	public List<CandidateSlot> getCandidates() {
		return this.candidates;
	}

	public List<AppointmentPlanningEntity> getSelections() {
		return this.selections;
	}

	public HardSoftScore getScore() {
		return this.score;
	}

	public void setScore(HardSoftScore score) {
		this.score = score;
	}

}
