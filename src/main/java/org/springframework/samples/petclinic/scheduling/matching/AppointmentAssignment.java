package org.springframework.samples.petclinic.scheduling.matching;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;

@PlanningEntity
public class AppointmentAssignment {

	@PlanningId
	private Long id;

	@PlanningVariable(valueRangeProviderRefs = "candidateSlotRange")
	private CandidateSlot candidateSlot;

	public AppointmentAssignment() {
	}

	public AppointmentAssignment(Long id) {
		this.id = id;
	}

	public AppointmentAssignment(Long id, CandidateSlot candidateSlot) {
		this.id = id;
		this.candidateSlot = candidateSlot;
	}

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public CandidateSlot getCandidateSlot() {
		return this.candidateSlot;
	}

	public void setCandidateSlot(CandidateSlot candidateSlot) {
		this.candidateSlot = candidateSlot;
	}

}
