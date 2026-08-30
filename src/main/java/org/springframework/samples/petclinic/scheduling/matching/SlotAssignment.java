package org.springframework.samples.petclinic.scheduling.matching;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;

@PlanningEntity
public class SlotAssignment {

	@PlanningId
	private Long id;

	private CandidateSlot selectedSlot;

	public SlotAssignment() {
	}

	public SlotAssignment(Long id) {
		this.id = id;
	}

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	@PlanningVariable(valueRangeProviderRefs = "candidateRange", allowsUnassigned = true)
	public CandidateSlot getSelectedSlot() {
		return this.selectedSlot;
	}

	public void setSelectedSlot(CandidateSlot selectedSlot) {
		this.selectedSlot = selectedSlot;
	}

}
