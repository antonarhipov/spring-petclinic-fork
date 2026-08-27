package org.springframework.samples.petclinic.scheduling.matching;

import java.util.ArrayList;
import java.util.List;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.ProblemFactProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.BendableScore;

@PlanningSolution
public class SchedulingProblem {

	@PlanningEntityProperty
	private ProposedAssignment proposedAssignment;

	@ProblemFactCollectionProperty
	@ValueRangeProvider(id = "candidateRange")
	private List<CandidateSlot> candidateList = new ArrayList<>();

	@ProblemFactProperty
	private SchedulingRequestFact requestFact;

	@ProblemFactCollectionProperty
	private List<AppointmentFact> appointmentList = new ArrayList<>();

	@ProblemFactCollectionProperty
	private List<ReservationFact> reservationList = new ArrayList<>();

	@PlanningScore(bendableHardLevelsSize = 1, bendableSoftLevelsSize = 6)
	private BendableScore score;

	public SchedulingProblem() {
	}

	public SchedulingProblem(ProposedAssignment proposedAssignment, List<CandidateSlot> candidateList,
			SchedulingRequestFact requestFact, List<AppointmentFact> appointmentList,
			List<ReservationFact> reservationList) {
		this.proposedAssignment = proposedAssignment;
		this.candidateList = candidateList;
		this.requestFact = requestFact;
		this.appointmentList = appointmentList;
		this.reservationList = reservationList;
	}

	public ProposedAssignment getProposedAssignment() {
		return proposedAssignment;
	}

	public void setProposedAssignment(ProposedAssignment proposedAssignment) {
		this.proposedAssignment = proposedAssignment;
	}

	public List<CandidateSlot> getCandidateList() {
		return candidateList;
	}

	public void setCandidateList(List<CandidateSlot> candidateList) {
		this.candidateList = candidateList;
	}

	public SchedulingRequestFact getRequestFact() {
		return requestFact;
	}

	public void setRequestFact(SchedulingRequestFact requestFact) {
		this.requestFact = requestFact;
	}

	public List<AppointmentFact> getAppointmentList() {
		return appointmentList;
	}

	public void setAppointmentList(List<AppointmentFact> appointmentList) {
		this.appointmentList = appointmentList;
	}

	public List<ReservationFact> getReservationList() {
		return reservationList;
	}

	public void setReservationList(List<ReservationFact> reservationList) {
		this.reservationList = reservationList;
	}

	public BendableScore getScore() {
		return score;
	}

	public void setScore(BendableScore score) {
		this.score = score;
	}

}
