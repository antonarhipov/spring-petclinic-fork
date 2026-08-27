package org.springframework.samples.petclinic.scheduling.matching;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;

@PlanningEntity
public class ProposedAssignment {

	@PlanningId
	private String id = "assignment-1";

	@PlanningVariable(valueRangeProviderRefs = "candidateRange")
	private CandidateSlot candidate;

	public ProposedAssignment() {
	}

	public ProposedAssignment(String id) {
		this.id = id;
	}

	public ProposedAssignment(String id, CandidateSlot candidate) {
		this.id = id;
		this.candidate = candidate;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public CandidateSlot getCandidate() {
		return candidate;
	}

	public void setCandidate(CandidateSlot candidate) {
		this.candidate = candidate;
	}

	@Override
	public String toString() {
		return "ProposedAssignment{" + "id='" + id + '\'' + ", candidate=" + candidate + '}';
	}

}
