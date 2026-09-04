/* Copyright 2012-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package org.springframework.samples.petclinic.scheduling.solver;

import java.lang.reflect.Field;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import ai.timefold.solver.core.api.score.stream.test.ConstraintVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicOpeningHour;
import org.springframework.samples.petclinic.scheduling.clinic.VetWeeklyBlock;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.vet.Specialty;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class TimefoldHardConstraintATests {

	private static final ZoneId ZONE = ZoneId.of("Europe/Amsterdam");

	private static final ZonedDateTime MONDAY_10 = ZonedDateTime.of(2026, 9, 7, 10, 0, 0, 0, ZONE);

	private final ConstraintVerifier<AppointmentConstraintProvider, ScheduleSolution> verifier = ConstraintVerifier
		.build(new AppointmentConstraintProvider(), ScheduleSolution.class, AppointmentAssignment.class);

	private Vet radiologist;

	private ClinicOpeningHour mondayOpening;

	private VetWeeklyBlock fullMondayBlock;

	@BeforeEach
	void setUp() {
		Specialty radiology = new Specialty();
		radiology.setId(1);
		radiology.setName("radiology");
		this.radiologist = new Vet();
		this.radiologist.setId(2);
		this.radiologist.setFirstName("Helen");
		this.radiologist.setLastName("Leary");
		this.radiologist.addSpecialty(radiology);
		this.mondayOpening = opening(DayOfWeek.MONDAY, 9, 17, false);
		this.fullMondayBlock = block(this.radiologist, DayOfWeek.MONDAY, 9, 17);
	}

	@Test
	@Tag("AC-73")
	void singleEntitySingleVariableAndDualLayerRejection_AC73() {
		assertThat(AppointmentAssignment.class).hasAnnotation(PlanningEntity.class);
		assertThat(List.of(AppointmentAssignment.class.getDeclaredFields())
			.stream()
			.filter(field -> field.isAnnotationPresent(PlanningVariable.class))
			.map(Field::getName)).containsExactly("slot");
		AppointmentAssignment assignment = assignment(MONDAY_10.withHour(8), "radiology");
		ScheduleSolution problem = new ScheduleSolution(List.of(new AppointmentSlot(this.radiologist, MONDAY_10)),
				assignment, List.of(), List.of());
		assertThat(problem.getAssignmentList()).containsExactly(assignment);
		assertThat(problem.getSlotList()).containsExactly(new AppointmentSlot(this.radiologist, MONDAY_10));

		assertThat(
				feasible(MONDAY_10.withHour(8), 30, List.of(this.fullMondayBlock), List.of(), List.of(), "radiology"))
			.isFalse();
		this.verifier.verifyThat(AppointmentConstraintProvider::clinicOperatingHours).given(assignment).penalizesBy(1);
	}

	@Test
	@Tag("AC-74")
	void outsideOpeningHoursRejected_AC74() {
		ZonedDateTime outside = MONDAY_10.withHour(8).withMinute(45);
		assertThat(feasible(outside, 30, List.of(this.fullMondayBlock), List.of(), List.of(), "radiology")).isFalse();
		this.verifier.verifyThat(AppointmentConstraintProvider::clinicOperatingHours)
			.given(assignment(outside, "radiology"))
			.penalizesBy(1);
	}

	@Test
	@Tag("AC-75")
	void splitShiftGapRejected_AC75() {
		List<VetWeeklyBlock> split = List.of(block(this.radiologist, DayOfWeek.MONDAY, 9, 12),
				block(this.radiologist, DayOfWeek.MONDAY, 13, 17));
		ZonedDateTime spanningGap = MONDAY_10.withHour(11).withMinute(45);
		assertThat(feasible(spanningGap, 30, split, List.of(), List.of(), "radiology")).isFalse();
		AppointmentAssignment assignment = assignment(spanningGap, "radiology");
		assignment.setVetWorkingBlocks(split);
		this.verifier.verifyThat(AppointmentConstraintProvider::continuousVetWorkingBlock)
			.given(assignment)
			.penalizesBy(1);
	}

	@Test
	@Tag("AC-76")
	void appointmentAndHoldOverlapRejected_AC76() {
		Appointment existing = new Appointment();
		existing.setVet(this.radiologist);
		existing.setStartTime(MONDAY_10);
		existing.setDuration(30);
		SchedulingRequest hold = new SchedulingRequest();
		hold.setHeldVet(this.radiologist);
		hold.setHeldStart(MONDAY_10);
		hold.setHeldDuration(30);
		ZonedDateTime overlap = MONDAY_10.plusMinutes(15);

		assertThat(feasible(overlap, 30, List.of(this.fullMondayBlock), List.of(existing), List.of(), "radiology"))
			.isFalse();
		assertThat(feasible(overlap, 30, List.of(this.fullMondayBlock), List.of(), List.of(hold), "radiology"))
			.isFalse();
		AppointmentAssignment assignment = assignment(overlap, "radiology");
		this.verifier.verifyThat(AppointmentConstraintProvider::vetDoubleBookingWithExisting)
			.given(assignment, existing)
			.penalizesBy(1);
		this.verifier.verifyThat(AppointmentConstraintProvider::activeHoldCollision)
			.given(assignment, hold)
			.penalizesBy(1);
	}

	@Test
	@Tag("AC-77")
	void missingSpecialtyRejected_AC77() {
		assertThat(feasible(MONDAY_10, 30, List.of(this.fullMondayBlock), List.of(), List.of(), "surgery")).isFalse();
		this.verifier.verifyThat(AppointmentConstraintProvider::specialtyMismatch)
			.given(assignment(MONDAY_10, "surgery"))
			.penalizesBy(1);
	}

	private boolean feasible(ZonedDateTime start, int duration, List<VetWeeklyBlock> blocks,
			List<Appointment> appointments, List<SchedulingRequest> holds, String specialty) {
		return DefaultSlotRanker.isCandidateFeasible(this.radiologist, start, duration, List.of(this.mondayOpening),
				blocks, appointments, holds, specialty);
	}

	private AppointmentAssignment assignment(ZonedDateTime start, String specialty) {
		AppointmentAssignment assignment = new AppointmentAssignment("test");
		assignment.setVet(this.radiologist);
		assignment.setStartTime(start);
		assignment.setDuration(30);
		assignment.setRequiredSpecialty(specialty);
		assignment.setClinicOpeningHours(List.of(this.mondayOpening));
		assignment.setVetWorkingBlocks(List.of(this.fullMondayBlock));
		return assignment;
	}

	private static ClinicOpeningHour opening(DayOfWeek day, int open, int close, boolean closed) {
		ClinicOpeningHour hours = new ClinicOpeningHour();
		ReflectionTestUtils.setField(hours, "dayOfWeek", day);
		ReflectionTestUtils.setField(hours, "openTime", LocalTime.of(open, 0));
		ReflectionTestUtils.setField(hours, "closeTime", LocalTime.of(close, 0));
		ReflectionTestUtils.setField(hours, "closed", closed);
		return hours;
	}

	private static VetWeeklyBlock block(Vet vet, DayOfWeek day, int start, int end) {
		VetWeeklyBlock block = new VetWeeklyBlock();
		ReflectionTestUtils.setField(block, "vet", vet);
		ReflectionTestUtils.setField(block, "dayOfWeek", day);
		ReflectionTestUtils.setField(block, "startTime", LocalTime.of(start, 0));
		ReflectionTestUtils.setField(block, "endTime", LocalTime.of(end, 0));
		return block;
	}

}
