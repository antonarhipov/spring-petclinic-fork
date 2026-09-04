/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.scheduling.solver;

import java.time.ZonedDateTime;
import java.util.Objects;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.interpretation.AvailabilityWindow;
import org.springframework.samples.petclinic.scheduling.interpretation.WindowKind;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;

/**
 * Timefold constraint provider enforcing the 8-tier scheduling score (RULE-6,
 * AC-111..AC-118).
 */
public class AppointmentConstraintProvider implements ConstraintProvider {

	@Override
	public Constraint[] defineConstraints(ConstraintFactory factory) {
		return new Constraint[] { vetDoubleBooking(factory), vetDoubleBookingWithExisting(factory),
				clinicOperatingHours(factory), continuousVetWorkingBlock(factory), specialtyMismatch(factory),
				activeHoldCollision(factory), excludedWindow(factory), outsidePositiveWindowUnion(factory),
				sameOwnerAppointmentCollision(factory), sameOwnerHoldCollision(factory), preferredTimeWindow(factory),
				preferredVet(factory), earliestTime(factory), specialtyMatchingPreference(factory),
				vetContinuity(factory), loadBalancing(factory) };
	}

	Constraint continuousVetWorkingBlock(ConstraintFactory factory) {
		return factory.forEach(AppointmentAssignment.class)
			.filter(a -> a.getStartTime() != null && !a.isWithinContinuousVetBlock())
			.penalize(HardMediumSoftScore.ONE_HARD)
			.asConstraint("Continuous veterinarian working block");
	}

	// H1 (Hard): Vet double-booking (AC-111)
	Constraint vetDoubleBooking(ConstraintFactory factory) {
		return factory.forEachUniquePair(AppointmentAssignment.class, Joiners.equal(AppointmentAssignment::getVet))
			.filter((a1, a2) -> a1.getStartTime() != null && a2.getStartTime() != null && a1.overlapsWith(a2))
			.penalize(HardMediumSoftScore.ONE_HARD)
			.asConstraint("Vet double-booking");
	}

	// H1 (Hard): Vet double-booking with existing appointments (AC-111)
	Constraint vetDoubleBookingWithExisting(ConstraintFactory factory) {
		return factory.forEach(AppointmentAssignment.class)
			.filter(a -> a.getVet() != null && a.getStartTime() != null)
			.join(Appointment.class, Joiners.equal(AppointmentAssignment::getVet, Appointment::getVet))
			.filter((assignment, existing) -> existing.getStartTime() != null
					&& assignment.overlapsWithExisting(existing))
			.penalize(HardMediumSoftScore.ONE_HARD)
			.asConstraint("Vet double-booking with existing");
	}

	// H2 (Hard): Clinic operating hours (AC-112)
	Constraint clinicOperatingHours(ConstraintFactory factory) {
		return factory.forEach(AppointmentAssignment.class)
			.filter(a -> a.getStartTime() != null && !a.isWithinClinicHours())
			.penalize(HardMediumSoftScore.ONE_HARD)
			.asConstraint("Clinic operating hours");
	}

	// H3 (Hard): Specialty mismatch (AC-113)
	Constraint specialtyMismatch(ConstraintFactory factory) {
		return factory.forEach(AppointmentAssignment.class)
			.filter(a -> a.getVet() != null && a.hasSpecialtyMismatch())
			.penalize(HardMediumSoftScore.ONE_HARD)
			.asConstraint("Specialty mismatch");
	}

	// H4 (Hard): Active hold collision (AC-114)
	Constraint activeHoldCollision(ConstraintFactory factory) {
		return factory.forEach(AppointmentAssignment.class)
			.filter(a -> a.getVet() != null && a.getStartTime() != null)
			.join(SchedulingRequest.class, Joiners.equal(AppointmentAssignment::getVet, SchedulingRequest::getHeldVet))
			.filter((assignment, hold) -> hold.getHeldStart() != null && hold.getHeldDuration() != null
					&& assignment.overlapsWithActiveHold(hold))
			.penalize(HardMediumSoftScore.ONE_HARD)
			.asConstraint("Active hold collision");
	}

	Constraint excludedWindow(ConstraintFactory factory) {
		return factory.forEach(AppointmentAssignment.class)
			.filter(assignment -> assignment.getStartTime() != null && assignment.getPreferredWindows()
				.stream()
				.filter(window -> window.kind() == WindowKind.EXCLUDED)
				.anyMatch(window -> matches(window, assignment.getStartTime())))
			.penalize(HardMediumSoftScore.ONE_HARD)
			.asConstraint("Owner excluded window");
	}

	Constraint outsidePositiveWindowUnion(ConstraintFactory factory) {
		return factory.forEach(AppointmentAssignment.class)
			.filter(assignment -> assignment.getStartTime() != null && hasPositiveWindows(assignment)
					&& assignment.getPreferredWindows()
						.stream()
						.filter(window -> window.kind() != WindowKind.EXCLUDED)
						.noneMatch(window -> matches(window, assignment.getStartTime())))
			.penalize(HardMediumSoftScore.ONE_HARD)
			.asConstraint("Outside preferred or allowed window union");
	}

	Constraint sameOwnerAppointmentCollision(ConstraintFactory factory) {
		return factory.forEach(AppointmentAssignment.class)
			.filter(assignment -> assignment.getOwnerId() != null && assignment.getStartTime() != null)
			.join(Appointment.class)
			.filter(AppointmentConstraintProvider::sameOwnerOtherPetOverlap)
			.penalize(HardMediumSoftScore.ONE_HARD)
			.asConstraint("Same owner appointment collision");
	}

	Constraint sameOwnerHoldCollision(ConstraintFactory factory) {
		return factory.forEach(AppointmentAssignment.class)
			.filter(assignment -> assignment.getOwnerId() != null && assignment.getStartTime() != null)
			.join(SchedulingRequest.class)
			.filter(AppointmentConstraintProvider::sameOwnerOtherPetHoldOverlap)
			.penalize(HardMediumSoftScore.ONE_HARD)
			.asConstraint("Same owner hold collision");
	}

	// M1 (Medium): Preferred time window (AC-115)
	Constraint preferredTimeWindow(ConstraintFactory factory) {
		return factory.forEach(AppointmentAssignment.class)
			.filter(AppointmentConstraintProvider::isInsidePreferredWindow)
			.reward(HardMediumSoftScore.ONE_MEDIUM)
			.asConstraint("Preferred time window");
	}

	Constraint preferredVet(ConstraintFactory factory) {
		return factory.forEach(AppointmentAssignment.class)
			.filter(assignment -> assignment.getVet() != null && assignment.getPreferredVetId() != null
					&& Objects.equals(assignment.getVet().getId(), assignment.getPreferredVetId()))
			.reward(HardMediumSoftScore.ONE_SOFT, assignment -> 100_000)
			.asConstraint("Preferred veterinarian");
	}

	Constraint earliestTime(ConstraintFactory factory) {
		return factory.forEach(AppointmentAssignment.class)
			.filter(assignment -> assignment.getStartTime() != null)
			.penalize(HardMediumSoftScore.ONE_SOFT, assignment -> assignment.getStartTime().toEpochSecond() / 60)
			.asConstraint("Earliest date and time");
	}

	static HardMediumSoftScore rankingScore(AppointmentAssignment assignment) {
		int medium = isInsidePreferredWindow(assignment) ? 1 : 0;
		int soft = assignment.getVet() != null && assignment.getPreferredVetId() != null
				&& Objects.equals(assignment.getVet().getId(), assignment.getPreferredVetId()) ? 100_000 : 0;
		soft -= Math.toIntExact(assignment.getStartTime().toEpochSecond() / 60);
		return HardMediumSoftScore.of(0, medium, soft);
	}

	private static boolean isInsidePreferredWindow(AppointmentAssignment assignment) {
		return assignment.getStartTime() != null && assignment.getPreferredWindows()
			.stream()
			.filter(window -> window.kind() == WindowKind.PREFERRED)
			.anyMatch(window -> matches(window, assignment.getStartTime()));
	}

	private static boolean hasPositiveWindows(AppointmentAssignment assignment) {
		return assignment.getPreferredWindows().stream().anyMatch(window -> window.kind() != WindowKind.EXCLUDED);
	}

	private static boolean matches(AvailabilityWindow window, ZonedDateTime start) {
		if (window.dateVal() != null && !window.dateVal().equals(start.toLocalDate())) {
			return false;
		}
		if (window.startDate() != null && start.toLocalDate().isBefore(window.startDate())) {
			return false;
		}
		if (window.endDate() != null && start.toLocalDate().isAfter(window.endDate())) {
			return false;
		}
		if (window.dayOfWeek() != null && window.dayOfWeek() != start.getDayOfWeek()) {
			return false;
		}
		return (window.startTime() == null || !start.toLocalTime().isBefore(window.startTime()))
				&& (window.endTime() == null || start.toLocalTime().isBefore(window.endTime()));
	}

	private static boolean sameOwnerOtherPetOverlap(AppointmentAssignment assignment, Appointment appointment) {
		return appointment.getPet() != null && appointment.getRequest() != null
				&& appointment.getRequest().getOwner() != null
				&& Objects.equals(assignment.getOwnerId(), appointment.getRequest().getOwner().getId())
				&& !Objects.equals(assignment.getPetId(), appointment.getPet().getId())
				&& overlaps(assignment.getStartTime(), assignment.getEndTime(), appointment.getStartTime(),
						appointment.getEndTime());
	}

	private static boolean sameOwnerOtherPetHoldOverlap(AppointmentAssignment assignment, SchedulingRequest hold) {
		return hold.getOwner() != null && hold.getPet() != null
				&& Objects.equals(assignment.getOwnerId(), hold.getOwner().getId())
				&& !Objects.equals(assignment.getPetId(), hold.getPet().getId()) && hold.getHeldStart() != null
				&& hold.getHeldDuration() != null && overlaps(assignment.getStartTime(), assignment.getEndTime(),
						hold.getHeldStart(), hold.getHeldStart().plusMinutes(hold.getHeldDuration()));
	}

	private static boolean overlaps(ZonedDateTime firstStart, ZonedDateTime firstEnd, ZonedDateTime secondStart,
			ZonedDateTime secondEnd) {
		return secondStart != null && secondEnd != null && firstStart.isBefore(secondEnd)
				&& firstEnd.isAfter(secondStart);
	}

	// S1 (Soft): Specialty matching preference (AC-116)
	Constraint specialtyMatchingPreference(ConstraintFactory factory) {
		return factory.forEach(AppointmentAssignment.class)
			.filter(a -> a.getVet() != null && a.matchesSpecialtyPreference())
			.reward(HardMediumSoftScore.ONE_SOFT)
			.asConstraint("Specialty matching preference");
	}

	// S2 (Soft): Vet continuity (AC-117)
	Constraint vetContinuity(ConstraintFactory factory) {
		return factory.forEach(AppointmentAssignment.class)
			.filter(a -> a.getVet() != null && a.matchesPreviousVet())
			.reward(HardMediumSoftScore.ONE_SOFT)
			.asConstraint("Vet continuity");
	}

	// S3 (Soft): Vet load balancing (AC-118)
	Constraint loadBalancing(ConstraintFactory factory) {
		return factory.forEach(AppointmentAssignment.class)
			.filter(a -> a.getVet() != null)
			.groupBy(AppointmentAssignment::getVet, ConstraintCollectors.count())
			.penalize(HardMediumSoftScore.ONE_SOFT, (vet, count) -> (long) count * count)
			.asConstraint("Vet load balancing");
	}

}
