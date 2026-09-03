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

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import ai.timefold.solver.core.api.score.stream.test.ConstraintVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.interpretation.AvailabilityWindow;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.vet.Specialty;
import org.springframework.samples.petclinic.vet.Vet;

/**
 * Constraint provider verification tests covering the 8-tier score specification (RULE-6,
 * AC-111..AC-118).
 */
class AppointmentConstraintProviderTests {

	private static final ZoneId ZONE = ZoneId.of("Europe/Amsterdam");

	private final ConstraintVerifier<AppointmentConstraintProvider, ScheduleSolution> constraintVerifier = ConstraintVerifier
		.build(new AppointmentConstraintProvider(), ScheduleSolution.class, AppointmentAssignment.class);

	private Vet vet1;

	private Vet vet2;

	private Specialty radiology;

	private Specialty surgery;

	@BeforeEach
	void setUp() {
		this.radiology = new Specialty();
		this.radiology.setId(1);
		this.radiology.setName("radiology");

		this.surgery = new Specialty();
		this.surgery.setId(2);
		this.surgery.setName("surgery");

		this.vet1 = new Vet();
		this.vet1.setId(1);
		this.vet1.setFirstName("Helen");
		this.vet1.setLastName("Leary");
		this.vet1.addSpecialty(this.radiology);

		this.vet2 = new Vet();
		this.vet2.setId(2);
		this.vet2.setFirstName("Rafael");
		this.vet2.setLastName("Ortega");
		this.vet2.addSpecialty(this.surgery);
	}

	@Test
	void vetDoubleBookingPenalizesHard() {
		// Monday 2026-09-07 10:00 - 10:30
		ZonedDateTime start1 = ZonedDateTime.of(2026, 9, 7, 10, 0, 0, 0, ZONE);
		// Monday 2026-09-07 10:15 - 10:45 (overlapping)
		ZonedDateTime start2 = ZonedDateTime.of(2026, 9, 7, 10, 15, 0, 0, ZONE);

		AppointmentAssignment a1 = new AppointmentAssignment("1");
		a1.setVet(this.vet1);
		a1.setStartTime(start1);
		a1.setDuration(30);

		AppointmentAssignment a2 = new AppointmentAssignment("2");
		a2.setVet(this.vet1);
		a2.setStartTime(start2);
		a2.setDuration(30);

		this.constraintVerifier.verifyThat(AppointmentConstraintProvider::vetDoubleBooking)
			.given(a1, a2)
			.penalizesBy(1);

		// Non-overlapping
		a2.setStartTime(start1.plusMinutes(45));
		this.constraintVerifier.verifyThat(AppointmentConstraintProvider::vetDoubleBooking)
			.given(a1, a2)
			.penalizesBy(0);
	}

	@Test
	void vetDoubleBookingWithExistingPenalizesHard() {
		ZonedDateTime start = ZonedDateTime.of(2026, 9, 7, 10, 0, 0, 0, ZONE);

		Appointment existing = new Appointment();
		existing.setId(1);
		existing.setVet(this.vet1);
		existing.setStartTime(start);
		existing.setDuration(30);
		existing.setStatus(AppointmentStatus.CONFIRMED);

		AppointmentAssignment a1 = new AppointmentAssignment("1");
		a1.setVet(this.vet1);
		a1.setStartTime(start.plusMinutes(15));
		a1.setDuration(30);

		this.constraintVerifier.verifyThat(AppointmentConstraintProvider::vetDoubleBookingWithExisting)
			.given(a1, existing)
			.penalizesBy(1);
	}

	@Test
	void clinicOperatingHoursPenalizesHard() {
		// Valid weekday slot: Monday 10:00 to 10:30
		AppointmentAssignment valid = new AppointmentAssignment("1");
		valid.setVet(this.vet1);
		valid.setStartTime(ZonedDateTime.of(2026, 9, 7, 10, 0, 0, 0, ZONE));
		valid.setDuration(30);

		// Sunday: 2026-09-13
		AppointmentAssignment sunday = new AppointmentAssignment("2");
		sunday.setVet(this.vet1);
		sunday.setStartTime(ZonedDateTime.of(2026, 9, 13, 10, 0, 0, 0, ZONE));
		sunday.setDuration(30);

		// Saturday after 13:00: 2026-09-12 14:00
		AppointmentAssignment saturdayLate = new AppointmentAssignment("3");
		saturdayLate.setVet(this.vet1);
		saturdayLate.setStartTime(ZonedDateTime.of(2026, 9, 12, 14, 0, 0, 0, ZONE));
		saturdayLate.setDuration(30);

		// Weekday early: Monday 07:30
		AppointmentAssignment weekdayEarly = new AppointmentAssignment("4");
		weekdayEarly.setVet(this.vet1);
		weekdayEarly.setStartTime(ZonedDateTime.of(2026, 9, 7, 7, 30, 0, 0, ZONE));
		weekdayEarly.setDuration(30);

		this.constraintVerifier.verifyThat(AppointmentConstraintProvider::clinicOperatingHours)
			.given(valid)
			.penalizesBy(0);

		this.constraintVerifier.verifyThat(AppointmentConstraintProvider::clinicOperatingHours)
			.given(sunday)
			.penalizesBy(1);

		this.constraintVerifier.verifyThat(AppointmentConstraintProvider::clinicOperatingHours)
			.given(saturdayLate)
			.penalizesBy(1);

		this.constraintVerifier.verifyThat(AppointmentConstraintProvider::clinicOperatingHours)
			.given(weekdayEarly)
			.penalizesBy(1);
	}

	@Test
	void specialtyMismatchPenalizesHard() {
		ZonedDateTime start = ZonedDateTime.of(2026, 9, 7, 10, 0, 0, 0, ZONE);

		// Needs radiology, assigned to vet2 (surgery only)
		AppointmentAssignment mismatch = new AppointmentAssignment("1");
		mismatch.setVet(this.vet2);
		mismatch.setStartTime(start);
		mismatch.setRequiredSpecialty("radiology");

		// Needs radiology, assigned to vet1 (radiology)
		AppointmentAssignment match = new AppointmentAssignment("2");
		match.setVet(this.vet1);
		match.setStartTime(start);
		match.setRequiredSpecialty("radiology");

		this.constraintVerifier.verifyThat(AppointmentConstraintProvider::specialtyMismatch)
			.given(mismatch)
			.penalizesBy(1);

		this.constraintVerifier.verifyThat(AppointmentConstraintProvider::specialtyMismatch)
			.given(match)
			.penalizesBy(0);
	}

	@Test
	void activeHoldCollisionPenalizesHard() {
		ZonedDateTime holdStart = ZonedDateTime.of(2026, 9, 7, 11, 0, 0, 0, ZONE);

		SchedulingRequest hold = new SchedulingRequest();
		hold.setId(10);
		hold.setHeldVet(this.vet1);
		hold.setHeldStart(holdStart);
		hold.setHeldDuration(30);

		AppointmentAssignment colliding = new AppointmentAssignment("1");
		colliding.setVet(this.vet1);
		colliding.setStartTime(holdStart.plusMinutes(15));
		colliding.setDuration(30);

		AppointmentAssignment nonColliding = new AppointmentAssignment("2");
		nonColliding.setVet(this.vet1);
		nonColliding.setStartTime(holdStart.plusMinutes(45));
		nonColliding.setDuration(30);

		this.constraintVerifier.verifyThat(AppointmentConstraintProvider::activeHoldCollision)
			.given(colliding, hold)
			.penalizesBy(1);

		this.constraintVerifier.verifyThat(AppointmentConstraintProvider::activeHoldCollision)
			.given(nonColliding, hold)
			.penalizesBy(0);
	}

	@Test
	void preferredTimeWindowRewardsMedium() {
		AvailabilityWindow window = AvailabilityWindow.preferredDayOfWeek(DayOfWeek.MONDAY, LocalTime.of(9, 0),
				LocalTime.of(12, 0), "Mon mornings");

		AppointmentAssignment matching = new AppointmentAssignment("1");
		matching.setVet(this.vet1);
		matching.setPreferredWindows(List.of(window));
		matching.setStartTime(ZonedDateTime.of(2026, 9, 7, 10, 0, 0, 0, ZONE)); // Monday
																				// 10:00
		matching.setDuration(30);

		AppointmentAssignment outside = new AppointmentAssignment("2");
		outside.setVet(this.vet1);
		outside.setPreferredWindows(List.of(window));
		outside.setStartTime(ZonedDateTime.of(2026, 9, 8, 10, 0, 0, 0, ZONE)); // Tuesday
																				// 10:00
		outside.setDuration(30);

		this.constraintVerifier.verifyThat(AppointmentConstraintProvider::preferredTimeWindow)
			.given(matching)
			.rewardsWith(1);

		this.constraintVerifier.verifyThat(AppointmentConstraintProvider::preferredTimeWindow)
			.given(outside)
			.rewardsWith(0);
	}

	@Test
	void specialtyMatchingPreferenceRewardsSoft() {
		ZonedDateTime start = ZonedDateTime.of(2026, 9, 7, 10, 0, 0, 0, ZONE);

		AppointmentAssignment matching = new AppointmentAssignment("1");
		matching.setVet(this.vet1);
		matching.setStartTime(start);
		matching.setRequiredSpecialty("radiology");

		AppointmentAssignment nonMatching = new AppointmentAssignment("2");
		nonMatching.setVet(this.vet2);
		nonMatching.setStartTime(start);
		nonMatching.setRequiredSpecialty("radiology");

		this.constraintVerifier.verifyThat(AppointmentConstraintProvider::specialtyMatchingPreference)
			.given(matching)
			.rewardsWith(1);

		this.constraintVerifier.verifyThat(AppointmentConstraintProvider::specialtyMatchingPreference)
			.given(nonMatching)
			.rewardsWith(0);
	}

	@Test
	void vetContinuityRewardsSoft() {
		ZonedDateTime start = ZonedDateTime.of(2026, 9, 7, 10, 0, 0, 0, ZONE);

		AppointmentAssignment continuity = new AppointmentAssignment("1");
		continuity.setVet(this.vet1);
		continuity.setStartTime(start);
		continuity.setPreviousVetId(1); // Matches vet1

		AppointmentAssignment noContinuity = new AppointmentAssignment("2");
		noContinuity.setVet(this.vet2);
		noContinuity.setStartTime(start);
		noContinuity.setPreviousVetId(1); // vet2 != 1

		this.constraintVerifier.verifyThat(AppointmentConstraintProvider::vetContinuity)
			.given(continuity)
			.rewardsWith(1);

		this.constraintVerifier.verifyThat(AppointmentConstraintProvider::vetContinuity)
			.given(noContinuity)
			.rewardsWith(0);
	}

	@Test
	void loadBalancingRewardsBalancedSchedule() {
		ZonedDateTime start = ZonedDateTime.of(2026, 9, 7, 10, 0, 0, 0, ZONE);

		// Unbalanced: both assignments on vet1 -> penalty = 2^2 = 4
		AppointmentAssignment a1 = new AppointmentAssignment("1");
		a1.setVet(this.vet1);
		a1.setStartTime(start);

		AppointmentAssignment a2 = new AppointmentAssignment("2");
		a2.setVet(this.vet1);
		a2.setStartTime(start.plusHours(1));

		this.constraintVerifier.verifyThat(AppointmentConstraintProvider::loadBalancing).given(a1, a2).penalizesBy(4);

		// Balanced: 1 on vet1, 1 on vet2 -> penalty = 1^2 + 1^2 = 2
		AppointmentAssignment a3 = new AppointmentAssignment("3");
		a3.setVet(this.vet2);
		a3.setStartTime(start);

		this.constraintVerifier.verifyThat(AppointmentConstraintProvider::loadBalancing).given(a1, a3).penalizesBy(2);
	}

}
