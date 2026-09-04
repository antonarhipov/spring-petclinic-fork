/* Copyright 2012-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package org.springframework.samples.petclinic.scheduling.solver;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.test.ConstraintVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicOpeningHour;
import org.springframework.samples.petclinic.scheduling.clinic.VetWeeklyBlock;
import org.springframework.samples.petclinic.scheduling.interpretation.AvailabilityWindow;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class TimefoldHardConstraintBTests {

	private static final ZoneId ZONE = ZoneId.of("Europe/Amsterdam");

	private static final ZonedDateTime MONDAY_10 = ZonedDateTime.of(2026, 9, 7, 10, 0, 0, 0, ZONE);

	private final ConstraintVerifier<AppointmentConstraintProvider, ScheduleSolution> verifier = ConstraintVerifier
		.build(new AppointmentConstraintProvider(), ScheduleSolution.class, AppointmentAssignment.class);

	private Vet vet;

	private Vet otherVet;

	private ClinicOpeningHour opening;

	private VetWeeklyBlock block;

	@BeforeEach
	void setUp() {
		this.vet = vet(1);
		this.otherVet = vet(2);
		this.opening = opening();
		this.block = block(this.vet);
	}

	@Test
	@Tag("AC-78")
	void excludedWindowRejected_AC78() {
		AvailabilityWindow excluded = AvailabilityWindow.excluded(LocalDate.of(2026, 9, 7), LocalTime.of(9, 0),
				LocalTime.of(11, 0), "not Monday morning");
		assertThat(feasible(MONDAY_10, List.of(excluded), List.of(), List.of(), MONDAY_10.minusDays(1),
				MONDAY_10.plusDays(30), null, null))
			.isFalse();
		this.verifier.verifyThat(AppointmentConstraintProvider::excludedWindow)
			.given(assignment(this.vet, MONDAY_10, List.of(excluded), null))
			.penalizesBy(1);
	}

	@Test
	@Tag("AC-79")
	void outsidePositiveUnionRejected_AC79() {
		AvailabilityWindow allowed = AvailabilityWindow.allowed(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 7),
				LocalTime.of(13, 0), LocalTime.of(15, 0), "Monday afternoon");
		assertThat(feasible(MONDAY_10, List.of(allowed), List.of(), List.of(), MONDAY_10.minusDays(1),
				MONDAY_10.plusDays(30), null, null))
			.isFalse();
		this.verifier.verifyThat(AppointmentConstraintProvider::outsidePositiveWindowUnion)
			.given(assignment(this.vet, MONDAY_10, List.of(allowed), null))
			.penalizesBy(1);
	}

	@Test
	@Tag("AC-80")
	void outsideHorizonRejected_AC80() {
		assertThat(feasible(MONDAY_10.plusDays(35), List.of(), List.of(), List.of(), MONDAY_10, MONDAY_10.plusDays(30),
				null, null))
			.isFalse();
	}

	@Test
	@Tag("AC-81")
	void sameOwnerCrossPetOverlapRejected_AC81() {
		Owner owner = owner(10);
		Pet requestedPet = pet(20);
		Pet otherPet = pet(21);
		SchedulingRequest source = request(owner, otherPet);
		Appointment appointment = new Appointment();
		appointment.setPet(otherPet);
		appointment.setVet(this.otherVet);
		appointment.setRequest(source);
		appointment.setStartTime(MONDAY_10);
		appointment.setDuration(30);
		SchedulingRequest hold = request(owner, otherPet);
		hold.setHeldVet(this.otherVet);
		hold.setHeldStart(MONDAY_10);
		hold.setHeldDuration(30);
		ZonedDateTime overlap = MONDAY_10.plusMinutes(15);

		assertThat(feasible(overlap, List.of(), List.of(appointment), List.of(), MONDAY_10.minusDays(1),
				MONDAY_10.plusDays(30), owner.getId(), requestedPet.getId()))
			.isFalse();
		assertThat(feasible(overlap, List.of(), List.of(), List.of(hold), MONDAY_10.minusDays(1),
				MONDAY_10.plusDays(30), owner.getId(), requestedPet.getId()))
			.isFalse();
		AppointmentAssignment assignment = assignment(this.vet, overlap, List.of(), null);
		assignment.setOwnerId(owner.getId());
		assignment.setPetId(requestedPet.getId());
		this.verifier.verifyThat(AppointmentConstraintProvider::sameOwnerAppointmentCollision)
			.given(assignment, appointment)
			.penalizesBy(1);
		this.verifier.verifyThat(AppointmentConstraintProvider::sameOwnerHoldCollision)
			.given(assignment, hold)
			.penalizesBy(1);
	}

	@Test
	@Tag("AC-82")
	void preferredWindowThenVetThenEarliest_AC82() {
		AvailabilityWindow preferred = AvailabilityWindow.preferred(LocalDate.of(2026, 9, 7), LocalTime.of(11, 0),
				LocalTime.of(12, 0), "preferred");
		HardMediumSoftScore preferredWindow = score(this.otherVet, MONDAY_10.withHour(11), List.of(preferred), 1);
		HardMediumSoftScore preferredVet = score(this.vet, MONDAY_10, List.of(preferred), 1);
		HardMediumSoftScore earlierOtherVet = score(this.otherVet, MONDAY_10, List.of(), 1);
		HardMediumSoftScore laterOtherVet = score(this.otherVet, MONDAY_10.plusMinutes(15), List.of(), 1);

		assertThat(preferredWindow).isGreaterThan(preferredVet);
		assertThat(preferredVet).isGreaterThan(earlierOtherVet);
		assertThat(earlierOtherVet).isGreaterThan(laterOtherVet);
	}

	private HardMediumSoftScore score(Vet assignedVet, ZonedDateTime start, List<AvailabilityWindow> windows,
			Integer preferredVetId) {
		return AppointmentConstraintProvider.rankingScore(assignment(assignedVet, start, windows, preferredVetId));
	}

	private AppointmentAssignment assignment(Vet assignedVet, ZonedDateTime start, List<AvailabilityWindow> windows,
			Integer preferredVetId) {
		AppointmentAssignment assignment = new AppointmentAssignment("test");
		assignment.setVet(assignedVet);
		assignment.setStartTime(start);
		assignment.setDuration(30);
		assignment.setPreferredWindows(windows);
		assignment.setPreferredVetId(preferredVetId);
		return assignment;
	}

	private boolean feasible(ZonedDateTime start, List<AvailabilityWindow> windows, List<Appointment> appointments,
			List<SchedulingRequest> holds, ZonedDateTime horizonStart, ZonedDateTime horizonEnd, Integer ownerId,
			Integer petId) {
		return DefaultSlotRanker.isCandidateFeasible(this.vet, start, 30, List.of(this.opening), List.of(this.block),
				appointments, holds, null, windows, horizonStart, horizonEnd, ownerId, petId);
	}

	private static Vet vet(int id) {
		Vet vet = new Vet();
		vet.setId(id);
		vet.setFirstName("Vet");
		vet.setLastName(Integer.toString(id));
		return vet;
	}

	private static ClinicOpeningHour opening() {
		ClinicOpeningHour opening = new ClinicOpeningHour();
		ReflectionTestUtils.setField(opening, "dayOfWeek", DayOfWeek.MONDAY);
		ReflectionTestUtils.setField(opening, "openTime", LocalTime.of(9, 0));
		ReflectionTestUtils.setField(opening, "closeTime", LocalTime.of(17, 0));
		return opening;
	}

	private static VetWeeklyBlock block(Vet vet) {
		VetWeeklyBlock block = new VetWeeklyBlock();
		ReflectionTestUtils.setField(block, "vet", vet);
		ReflectionTestUtils.setField(block, "dayOfWeek", DayOfWeek.MONDAY);
		ReflectionTestUtils.setField(block, "startTime", LocalTime.of(9, 0));
		ReflectionTestUtils.setField(block, "endTime", LocalTime.of(17, 0));
		return block;
	}

	private static Owner owner(int id) {
		Owner owner = new Owner();
		owner.setId(id);
		return owner;
	}

	private static Pet pet(int id) {
		Pet pet = new Pet();
		pet.setId(id);
		return pet;
	}

	private static SchedulingRequest request(Owner owner, Pet pet) {
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(owner);
		request.setPet(pet);
		return request;
	}

}
