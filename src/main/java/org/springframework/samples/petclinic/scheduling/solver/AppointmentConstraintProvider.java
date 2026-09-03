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

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;

/**
 * Timefold constraint provider enforcing the 8-tier scheduling score (RULE-6,
 * AC-111..AC-118).
 */
public class AppointmentConstraintProvider implements ConstraintProvider {

	@Override
	public Constraint[] defineConstraints(ConstraintFactory factory) {
		return new Constraint[] { vetDoubleBooking(factory), vetDoubleBookingWithExisting(factory),
				clinicOperatingHours(factory), specialtyMismatch(factory), activeHoldCollision(factory),
				preferredTimeWindow(factory), specialtyMatchingPreference(factory), vetContinuity(factory),
				loadBalancing(factory) };
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

	// M1 (Medium): Preferred time window (AC-115)
	Constraint preferredTimeWindow(ConstraintFactory factory) {
		return factory.forEach(AppointmentAssignment.class)
			.filter(a -> a.getStartTime() != null && a.isWithinPreferredWindow())
			.reward(HardMediumSoftScore.ONE_MEDIUM)
			.asConstraint("Preferred time window");
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
