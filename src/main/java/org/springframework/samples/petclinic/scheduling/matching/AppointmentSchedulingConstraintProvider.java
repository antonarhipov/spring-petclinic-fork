package org.springframework.samples.petclinic.scheduling.matching;

import ai.timefold.solver.core.api.score.BendableScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;

public class AppointmentSchedulingConstraintProvider implements ConstraintProvider {

	private static final int HARD_LEVELS_SIZE = 1;

	private static final int SOFT_LEVELS_SIZE = 6;

	@Override
	public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
		return new Constraint[] { assignmentMustBePresent(constraintFactory), preferredTimeWindow(constraintFactory),
				preferredVeterinarian(constraintFactory), avoidFallbackWindow(constraintFactory),
				earliestStartTime(constraintFactory), clinicEfficiency(constraintFactory),
				stableTieBreak(constraintFactory) };
	}

	Constraint assignmentMustBePresent(ConstraintFactory constraintFactory) {
		// forEachIncludingUnassigned is required here because a plain forEach()
		// silently excludes entities whose planning variable is still unassigned,
		// which is exactly the case this hard constraint must detect.
		return constraintFactory.forEachIncludingUnassigned(AppointmentAssignment.class)
			.filter(assignment -> assignment.getCandidateSlot() == null)
			.penalize(BendableScore.ofHard(HARD_LEVELS_SIZE, SOFT_LEVELS_SIZE, 0, 1))
			.asConstraint("assignmentMustBePresent");
	}

	Constraint preferredTimeWindow(ConstraintFactory constraintFactory) {
		return constraintFactory.forEach(AppointmentAssignment.class)
			.filter(assignment -> assignment.getCandidateSlot() != null
					&& assignment.getCandidateSlot().isInPreferredWindow())
			.reward(BendableScore.ofSoft(HARD_LEVELS_SIZE, SOFT_LEVELS_SIZE, 0, 100))
			.asConstraint("preferredTimeWindow");
	}

	Constraint preferredVeterinarian(ConstraintFactory constraintFactory) {
		return constraintFactory.forEach(AppointmentAssignment.class)
			.filter(assignment -> assignment.getCandidateSlot() != null
					&& assignment.getCandidateSlot().isPreferredVet())
			.reward(BendableScore.ofSoft(HARD_LEVELS_SIZE, SOFT_LEVELS_SIZE, 1, 100))
			.asConstraint("preferredVeterinarian");
	}

	Constraint avoidFallbackWindow(ConstraintFactory constraintFactory) {
		return constraintFactory.forEach(AppointmentAssignment.class)
			.filter(assignment -> assignment.getCandidateSlot() != null
					&& assignment.getCandidateSlot().isInFallbackWindow())
			.penalize(BendableScore.ofSoft(HARD_LEVELS_SIZE, SOFT_LEVELS_SIZE, 2, 100))
			.asConstraint("avoidFallbackWindow");
	}

	Constraint earliestStartTime(ConstraintFactory constraintFactory) {
		return constraintFactory.forEach(AppointmentAssignment.class)
			.filter(assignment -> assignment.getCandidateSlot() != null)
			.penalize(BendableScore.ofSoft(HARD_LEVELS_SIZE, SOFT_LEVELS_SIZE, 3, 1),
					assignment -> assignment.getCandidateSlot().getMinutesFromReference())
			.asConstraint("earliestStartTime");
	}

	Constraint clinicEfficiency(ConstraintFactory constraintFactory) {
		return constraintFactory.forEach(AppointmentAssignment.class)
			.filter(assignment -> assignment.getCandidateSlot() != null)
			.penalize(BendableScore.ofSoft(HARD_LEVELS_SIZE, SOFT_LEVELS_SIZE, 4, 1),
					assignment -> assignment.getCandidateSlot().getGapMinutes())
			.asConstraint("clinicEfficiency");
	}

	Constraint stableTieBreak(ConstraintFactory constraintFactory) {
		return constraintFactory.forEach(AppointmentAssignment.class)
			.filter(assignment -> assignment.getCandidateSlot() != null)
			.penalize(BendableScore.ofSoft(HARD_LEVELS_SIZE, SOFT_LEVELS_SIZE, 5, 1),
					assignment -> Math.abs(assignment.getCandidateSlot().getTieBreakKey().hashCode() % 10000))
			.asConstraint("stableTieBreak");
	}

}
