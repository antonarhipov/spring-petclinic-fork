package org.springframework.samples.petclinic.scheduling.solver;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;

/**
 * The single request planning entity used to represent a selection from generated,
 * conflict-free candidates. Candidate generation remains authoritative for calendar and
 * reservation-block safety.
 */
@PlanningEntity
public class AppointmentPlanningEntity {

	private CandidateSlot selectedSlot;

	@PlanningVariable(valueRangeProviderRefs = "candidateRange")
	public CandidateSlot getSelectedSlot() {
		return this.selectedSlot;
	}

	public void setSelectedSlot(CandidateSlot selectedSlot) {
		this.selectedSlot = selectedSlot;
	}

}
