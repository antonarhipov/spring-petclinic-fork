package org.springframework.samples.petclinic.scheduling.matching;

import java.util.ArrayList;
import java.util.List;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.BendableScore;

@PlanningSolution
public class AppointmentSchedulingSolution {

	@ValueRangeProvider(id = "candidateSlotRange")
	@ProblemFactCollectionProperty
	private List<CandidateSlot> candidateSlots = new ArrayList<>();

	@PlanningEntityProperty
	private AppointmentAssignment assignment;

	@PlanningScore(bendableHardLevelsSize = 1, bendableSoftLevelsSize = 6)
	private BendableScore score;

	public AppointmentSchedulingSolution() {
	}

	public AppointmentSchedulingSolution(List<CandidateSlot> candidateSlots, AppointmentAssignment assignment) {
		this.candidateSlots = (candidateSlots != null) ? candidateSlots : new ArrayList<>();
		this.assignment = assignment;
	}

	public List<CandidateSlot> getCandidateSlots() {
		return this.candidateSlots;
	}

	public void setCandidateSlots(List<CandidateSlot> candidateSlots) {
		this.candidateSlots = candidateSlots;
	}

	public AppointmentAssignment getAssignment() {
		return this.assignment;
	}

	public void setAssignment(AppointmentAssignment assignment) {
		this.assignment = assignment;
	}

	public BendableScore getScore() {
		return this.score;
	}

	public void setScore(BendableScore score) {
		this.score = score;
	}

}
