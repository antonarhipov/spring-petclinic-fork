package org.springframework.samples.petclinic.scheduling.matching;

import java.util.Objects;

import ai.timefold.solver.core.api.score.BendableScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import org.springframework.samples.petclinic.scheduling.model.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.model.ReservationStatus;

public class AppointmentPlanningConstraintProvider implements ConstraintProvider {

	public static final int HARD_LEVELS_SIZE = 1;

	public static final int SOFT_LEVELS_SIZE = 6;

	@Override
	public Constraint[] defineConstraints(ConstraintFactory factory) {
		return new Constraint[] {
				// Hard constraints (Hard level 0)
				invalidDstOffset(factory), startBeforeNextGrid(factory), startAfterHorizonEnd(factory),
				notInContiguousAvailability(factory), overlapsClosureOrLeave(factory), vetNotActiveOrEligible(factory),
				outsideOwnerAllowedWindows(factory), insideOwnerExcludedWindows(factory), exactRejectedPair(factory),
				overlapsAppointment(factory), overlapsActiveHold(factory),

				// Soft constraints (Soft levels 0 to 5)
				preferredWindow(factory), preferredVet(factory), earlierStart(factory), lighterWorkload(factory),
				stableStartTieBreaker(factory), stableVetIdTieBreaker(factory) };
	}

	// Hard Constraints

	Constraint invalidDstOffset(ConstraintFactory factory) {
		return factory.forEach(ProposedAssignment.class)
			.filter(a -> a.getCandidate() != null && !a.getCandidate().isValidDstOffset())
			.penalize(BendableScore.ofHard(HARD_LEVELS_SIZE, SOFT_LEVELS_SIZE, 0, 1L))
			.asConstraint("invalidDstOffset");
	}

	Constraint startBeforeNextGrid(ConstraintFactory factory) {
		return factory.forEach(ProposedAssignment.class)
			.filter(a -> a.getCandidate() != null && !a.getCandidate().isStartOnOrAfterNextGrid())
			.penalize(BendableScore.ofHard(HARD_LEVELS_SIZE, SOFT_LEVELS_SIZE, 0, 1L))
			.asConstraint("startBeforeNextGrid");
	}

	Constraint startAfterHorizonEnd(ConstraintFactory factory) {
		return factory.forEach(ProposedAssignment.class)
			.filter(a -> a.getCandidate() != null && !a.getCandidate().isStartBeforeHorizonEnd())
			.penalize(BendableScore.ofHard(HARD_LEVELS_SIZE, SOFT_LEVELS_SIZE, 0, 1L))
			.asConstraint("startAfterHorizonEnd");
	}

	Constraint notInContiguousAvailability(ConstraintFactory factory) {
		return factory.forEach(ProposedAssignment.class)
			.filter(a -> a.getCandidate() != null && !a.getCandidate().isInContiguousAvailability())
			.penalize(BendableScore.ofHard(HARD_LEVELS_SIZE, SOFT_LEVELS_SIZE, 0, 1L))
			.asConstraint("notInContiguousAvailability");
	}

	Constraint overlapsClosureOrLeave(ConstraintFactory factory) {
		return factory.forEach(ProposedAssignment.class)
			.filter(a -> a.getCandidate() != null && a.getCandidate().isOverlapsClosureOrLeave())
			.penalize(BendableScore.ofHard(HARD_LEVELS_SIZE, SOFT_LEVELS_SIZE, 0, 1L))
			.asConstraint("overlapsClosureOrLeave");
	}

	Constraint vetNotActiveOrEligible(ConstraintFactory factory) {
		return factory.forEach(ProposedAssignment.class)
			.filter(a -> a.getCandidate() != null && !a.getCandidate().isVetActiveAndEligible())
			.penalize(BendableScore.ofHard(HARD_LEVELS_SIZE, SOFT_LEVELS_SIZE, 0, 1L))
			.asConstraint("vetNotActiveOrEligible");
	}

	Constraint outsideOwnerAllowedWindows(ConstraintFactory factory) {
		return factory.forEach(ProposedAssignment.class)
			.filter(a -> a.getCandidate() != null && !a.getCandidate().isMatchesAllowedWindows())
			.penalize(BendableScore.ofHard(HARD_LEVELS_SIZE, SOFT_LEVELS_SIZE, 0, 1L))
			.asConstraint("outsideOwnerAllowedWindows");
	}

	Constraint insideOwnerExcludedWindows(ConstraintFactory factory) {
		return factory.forEach(ProposedAssignment.class)
			.filter(a -> a.getCandidate() != null && a.getCandidate().isMatchesExcludedWindows())
			.penalize(BendableScore.ofHard(HARD_LEVELS_SIZE, SOFT_LEVELS_SIZE, 0, 1L))
			.asConstraint("insideOwnerExcludedWindows");
	}

	Constraint exactRejectedPair(ConstraintFactory factory) {
		return factory.forEach(ProposedAssignment.class)
			.filter(a -> a.getCandidate() != null && a.getCandidate().isExactRejected())
			.penalize(BendableScore.ofHard(HARD_LEVELS_SIZE, SOFT_LEVELS_SIZE, 0, 1L))
			.asConstraint("exactRejectedPair");
	}

	Constraint overlapsAppointment(ConstraintFactory factory) {
		return factory.forEach(ProposedAssignment.class)
			.filter(a -> a.getCandidate() != null)
			.join(AppointmentFact.class, Joiners.filtering((assignment, appt) -> {
				if (appt.getStatus() == AppointmentStatus.CANCELLED) {
					return false;
				}
				CandidateSlot slot = assignment.getCandidate();
				boolean sameResource = Objects.equals(slot.getVeterinarianId(), appt.getVetId())
						|| (appt.getOwnerId() != null && Objects.equals(appt.getOwnerId(), slot.getOwnerId()))
						|| (appt.getPetId() != null && Objects.equals(appt.getPetId(), slot.getPetId()));
				if (!sameResource) {
					return false;
				}
				return slot.getStartInstant().isBefore(appt.getEndTime())
						&& appt.getStartTime().isBefore(slot.getEndInstant());
			}))
			.penalize(BendableScore.ofHard(HARD_LEVELS_SIZE, SOFT_LEVELS_SIZE, 0, 1L))
			.asConstraint("overlapsAppointment");
	}

	Constraint overlapsActiveHold(ConstraintFactory factory) {
		return factory.forEach(ProposedAssignment.class)
			.filter(a -> a.getCandidate() != null)
			.join(ReservationFact.class, Joiners.filtering((assignment, res) -> {
				if (res.getStatus() != ReservationStatus.ACTIVE) {
					return false;
				}
				CandidateSlot slot = assignment.getCandidate();
				boolean sameResource = Objects.equals(slot.getVeterinarianId(), res.getVetId())
						|| (res.getOwnerId() != null && Objects.equals(res.getOwnerId(), slot.getOwnerId()))
						|| (res.getPetId() != null && Objects.equals(res.getPetId(), slot.getPetId()));
				if (!sameResource) {
					return false;
				}
				return slot.getStartInstant().isBefore(res.getEndTime())
						&& res.getStartTime().isBefore(slot.getEndInstant());
			}))
			.penalize(BendableScore.ofHard(HARD_LEVELS_SIZE, SOFT_LEVELS_SIZE, 0, 1L))
			.asConstraint("overlapsActiveHold");
	}

	// Soft Constraints

	Constraint preferredWindow(ConstraintFactory factory) {
		return factory.forEach(ProposedAssignment.class)
			.filter(a -> a.getCandidate() != null && a.getCandidate().isPreferredWindow())
			.reward(BendableScore.ofSoft(HARD_LEVELS_SIZE, SOFT_LEVELS_SIZE, 0, 1L))
			.asConstraint("preferredWindow");
	}

	Constraint preferredVet(ConstraintFactory factory) {
		return factory.forEach(ProposedAssignment.class)
			.filter(a -> a.getCandidate() != null && a.getCandidate().isPreferredVet())
			.reward(BendableScore.ofSoft(HARD_LEVELS_SIZE, SOFT_LEVELS_SIZE, 1, 1L))
			.asConstraint("preferredVet");
	}

	Constraint earlierStart(ConstraintFactory factory) {
		return factory.forEach(ProposedAssignment.class)
			.filter(a -> a.getCandidate() != null)
			.penalize(BendableScore.ofSoft(HARD_LEVELS_SIZE, SOFT_LEVELS_SIZE, 2, 1L),
					a -> a.getCandidate().getStartInstant().getEpochSecond())
			.asConstraint("earlierStart");
	}

	Constraint lighterWorkload(ConstraintFactory factory) {
		return factory.forEach(ProposedAssignment.class)
			.filter(a -> a.getCandidate() != null)
			.penalize(BendableScore.ofSoft(HARD_LEVELS_SIZE, SOFT_LEVELS_SIZE, 3, 1L),
					a -> a.getCandidate().getCommittedWorkloadMinutes())
			.asConstraint("lighterWorkload");
	}

	Constraint stableStartTieBreaker(ConstraintFactory factory) {
		return factory.forEach(ProposedAssignment.class)
			.filter(a -> a.getCandidate() != null)
			.penalize(BendableScore.ofSoft(HARD_LEVELS_SIZE, SOFT_LEVELS_SIZE, 4, 1L),
					a -> a.getCandidate().getStartInstant().getEpochSecond())
			.asConstraint("stableStartTieBreaker");
	}

	Constraint stableVetIdTieBreaker(ConstraintFactory factory) {
		return factory.forEach(ProposedAssignment.class)
			.filter(a -> a.getCandidate() != null)
			.penalize(BendableScore.ofSoft(HARD_LEVELS_SIZE, SOFT_LEVELS_SIZE, 5, 1L),
					a -> a.getCandidate().getVeterinarianId().longValue())
			.asConstraint("stableVetIdTieBreaker");
	}

}
