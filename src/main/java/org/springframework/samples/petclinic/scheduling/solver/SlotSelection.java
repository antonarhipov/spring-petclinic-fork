/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.scheduling.solver;

import java.util.ArrayList;
import java.util.List;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.BendableScore;

@PlanningSolution
public class SlotSelection {

	@ProblemFactCollectionProperty
	@ValueRangeProvider(id = "slotRange")
	private List<RankedSlot> candidates = new ArrayList<>();

	@PlanningEntityProperty
	private SlotAssignment assignment = new SlotAssignment();

	@PlanningScore(bendableHardLevelsSize = 1, bendableSoftLevelsSize = 5)
	private BendableScore score;

	public SlotSelection() {
	}

	public SlotSelection(List<RankedSlot> candidates) {
		this.candidates = new ArrayList<>(candidates);
	}

	public List<RankedSlot> getCandidates() {
		return this.candidates;
	}

	public void setCandidates(List<RankedSlot> candidates) {
		this.candidates = candidates;
	}

	public SlotAssignment getAssignment() {
		return this.assignment;
	}

	public void setAssignment(SlotAssignment assignment) {
		this.assignment = assignment;
	}

	public BendableScore getScore() {
		return this.score;
	}

	public void setScore(BendableScore score) {
		this.score = score;
	}

}
