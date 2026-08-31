package org.springframework.samples.petclinic.scheduling.matching;

import java.time.Instant;

import ai.timefold.solver.core.api.score.BendableScore;
import ai.timefold.solver.core.api.score.stream.test.ConstraintVerifier;
import org.junit.jupiter.api.Test;

/**
 * Verifies the hard feasibility constraint and all 6 lexicographical soft constraint
 * levels defined by {@link AppointmentSchedulingConstraintProvider}: preferred time
 * window, preferred veterinarian, fallback window avoidance, earliest start time, clinic
 * efficiency, and the deterministic tie-breaker.
 */
class AppointmentSchedulingConstraintProviderTests {

	private final ConstraintVerifier<AppointmentSchedulingConstraintProvider, AppointmentSchedulingSolution> constraintVerifier = ConstraintVerifier
		.build(new AppointmentSchedulingConstraintProvider(), AppointmentSchedulingSolution.class,
				AppointmentAssignment.class);

	private CandidateSlot slot(boolean preferredWindow, boolean preferredVet, boolean fallbackWindow,
			int minutesFromReference, int gapMinutes, String tieBreakKey) {
		Instant start = Instant.parse("2026-09-01T09:00:00Z");
		return new CandidateSlot(1, "Dr. Carter", start, start.plusSeconds(1800), "UTC", 30, preferredWindow,
				preferredVet, fallbackWindow, minutesFromReference, gapMinutes, tieBreakKey);
	}

	@Test
	void unassignedSlotIsPenalizedAsHardViolation() {
		AppointmentAssignment assignment = new AppointmentAssignment(1L, null);

		this.constraintVerifier.verifyThat(AppointmentSchedulingConstraintProvider::assignmentMustBePresent)
			.given(assignment)
			.penalizesBy(1);
	}

	@Test
	void assignedSlotDoesNotTriggerHardViolation() {
		AppointmentAssignment assignment = new AppointmentAssignment(1L, slot(false, false, false, 0, 0, "a"));

		this.constraintVerifier.verifyThat(AppointmentSchedulingConstraintProvider::assignmentMustBePresent)
			.given(assignment)
			.penalizesBy(0);
	}

	@Test
	void preferredTimeWindowIsRewarded() {
		// Reward constraints without an explicit match weigher always deduce a match
		// weight of 1 per match; the fixed constraint weight (100) is applied by
		// Timefold separately and is verified via the solver-level soft level tests.
		AppointmentAssignment inWindow = new AppointmentAssignment(1L, slot(true, false, false, 0, 0, "a"));
		AppointmentAssignment outsideWindow = new AppointmentAssignment(2L, slot(false, false, false, 0, 0, "b"));

		this.constraintVerifier.verifyThat(AppointmentSchedulingConstraintProvider::preferredTimeWindow)
			.given(inWindow)
			.rewardsWith(1);

		this.constraintVerifier.verifyThat(AppointmentSchedulingConstraintProvider::preferredTimeWindow)
			.given(outsideWindow)
			.rewardsWith(0);
	}

	@Test
	void preferredVeterinarianIsRewarded() {
		AppointmentAssignment preferredVet = new AppointmentAssignment(1L, slot(false, true, false, 0, 0, "a"));
		AppointmentAssignment otherVet = new AppointmentAssignment(2L, slot(false, false, false, 0, 0, "b"));

		this.constraintVerifier.verifyThat(AppointmentSchedulingConstraintProvider::preferredVeterinarian)
			.given(preferredVet)
			.rewardsWith(1);

		this.constraintVerifier.verifyThat(AppointmentSchedulingConstraintProvider::preferredVeterinarian)
			.given(otherVet)
			.rewardsWith(0);
	}

	@Test
	void fallbackWindowIsPenalized() {
		AppointmentAssignment inFallback = new AppointmentAssignment(1L, slot(false, false, true, 0, 0, "a"));
		AppointmentAssignment notFallback = new AppointmentAssignment(2L, slot(false, false, false, 0, 0, "b"));

		this.constraintVerifier.verifyThat(AppointmentSchedulingConstraintProvider::avoidFallbackWindow)
			.given(inFallback)
			.penalizesBy(1);

		this.constraintVerifier.verifyThat(AppointmentSchedulingConstraintProvider::avoidFallbackWindow)
			.given(notFallback)
			.penalizesBy(0);
	}

	@Test
	void earliestStartTimePenalizesLaterMinutesFromReference() {
		AppointmentAssignment earlySlot = new AppointmentAssignment(1L, slot(false, false, false, 0, 0, "a"));
		AppointmentAssignment laterSlot = new AppointmentAssignment(2L, slot(false, false, false, 45, 0, "b"));

		this.constraintVerifier.verifyThat(AppointmentSchedulingConstraintProvider::earliestStartTime)
			.given(earlySlot)
			.penalizesBy(0);

		this.constraintVerifier.verifyThat(AppointmentSchedulingConstraintProvider::earliestStartTime)
			.given(laterSlot)
			.penalizesBy(45);
	}

	@Test
	void clinicEfficiencyPenalizesLargerGaps() {
		AppointmentAssignment tightSlot = new AppointmentAssignment(1L, slot(false, false, false, 0, 0, "a"));
		AppointmentAssignment gappySlot = new AppointmentAssignment(2L, slot(false, false, false, 0, 20, "b"));

		this.constraintVerifier.verifyThat(AppointmentSchedulingConstraintProvider::clinicEfficiency)
			.given(tightSlot)
			.penalizesBy(0);

		this.constraintVerifier.verifyThat(AppointmentSchedulingConstraintProvider::clinicEfficiency)
			.given(gappySlot)
			.penalizesBy(20);
	}

	@Test
	void stableTieBreakIsDeterministicallyPenalizedByHashKey() {
		String tieBreakKey = "vet-1:2026-09-01T09:00:00Z";
		AppointmentAssignment assignment = new AppointmentAssignment(1L, slot(false, false, false, 0, 0, tieBreakKey));
		int expectedPenalty = Math.abs(tieBreakKey.hashCode() % 10000);

		this.constraintVerifier.verifyThat(AppointmentSchedulingConstraintProvider::stableTieBreak)
			.given(assignment)
			.penalizesBy(expectedPenalty);
	}

	@Test
	void unassignedAssignmentsDoNotTriggerSoftConstraints() {
		AppointmentAssignment unassigned = new AppointmentAssignment(1L, null);

		this.constraintVerifier.verifyThat(AppointmentSchedulingConstraintProvider::preferredTimeWindow)
			.given(unassigned)
			.rewardsWith(0);
		this.constraintVerifier.verifyThat(AppointmentSchedulingConstraintProvider::earliestStartTime)
			.given(unassigned)
			.penalizesBy(0);
	}

	@Test
	void fullSolutionScoreCombinesAllSixSoftLevelsInLexicographicalOrder() {
		String tieBreakKey = "vet-1:2026-09-01T09:00:00Z";
		int expectedTieBreakPenalty = Math.abs(tieBreakKey.hashCode() % 10000);
		AppointmentAssignment assignment = new AppointmentAssignment(1L, slot(true, true, false, 0, 0, tieBreakKey));

		BendableScore expectedScore = BendableScore.of(new long[] { 0 },
				new long[] { 100, 100, 0, 0, 0, -expectedTieBreakPenalty });

		this.constraintVerifier.verifyThat().given(assignment).scores(expectedScore);
	}

}
