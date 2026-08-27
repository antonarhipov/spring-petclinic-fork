/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.scheduling.solver;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.samples.petclinic.appointment.Appointment;
import org.springframework.samples.petclinic.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.appointment.RejectedSuggestion;
import org.springframework.samples.petclinic.appointment.RejectedSuggestionRepository;
import org.springframework.samples.petclinic.appointment.SlotHold;
import org.springframework.samples.petclinic.appointment.SlotHoldRepository;
import org.springframework.samples.petclinic.calendar.ClinicSettings;
import org.springframework.samples.petclinic.calendar.ClinicSettingsRepository;
import org.springframework.samples.petclinic.calendar.GridGenerator;
import org.springframework.samples.petclinic.calendar.SlotCandidate;
import org.springframework.samples.petclinic.vet.Specialty;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SchedulingCandidateServiceTests {

	private static final Instant NOW = Instant.parse("2026-08-28T08:00:00Z");

	@Mock
	private ClinicSettingsRepository settingsRepository;

	@Mock
	private GridGenerator gridGenerator;

	@Mock
	private AppointmentRepository appointmentRepository;

	@Mock
	private SlotHoldRepository holdRepository;

	@Mock
	private RejectedSuggestionRepository rejectedRepository;

	@Mock
	private VetRepository vetRepository;

	private SchedulingCandidateService service;

	private Vet vet;

	@BeforeEach
	void setUp() {
		this.service = new SchedulingCandidateService(this.settingsRepository, this.gridGenerator,
				this.appointmentRepository, this.holdRepository, this.rejectedRepository, this.vetRepository,
				Clock.fixed(NOW, ZoneOffset.UTC));
		ClinicSettings settings = new ClinicSettings();
		this.vet = new Vet();
		this.vet.setId(1);
		Specialty surgery = new Specialty();
		surgery.setName("surgery");
		this.vet.addSpecialty(surgery);
		given(this.settingsRepository.getClinicSettings()).willReturn(settings);
		given(this.vetRepository.findAll()).willReturn(List.of(this.vet));
	}

	@Test
	void filtersExcludedRejectedBookedHeldAndSpecialtyMismatches() {
		Instant first = Instant.parse("2026-09-01T08:00:00Z");
		Instant second = first.plusSeconds(900);
		Instant third = second.plusSeconds(900);
		Instant fourth = third.plusSeconds(900);
		Instant fifth = fourth.plusSeconds(900);
		given(this.gridGenerator.generateCandidateSlots(eq(1), any(LocalDate.class), eq(30), any(ClinicSettings.class)))
			.willReturn(List.of(new SlotCandidate(1, first), new SlotCandidate(1, second), new SlotCandidate(1, third),
					new SlotCandidate(1, fourth), new SlotCandidate(1, fifth)));

		Appointment appointment = new Appointment();
		appointment.setStartInstant(second);
		appointment.setDurationMin(15);
		given(this.appointmentRepository.findByVetIdAndStatusNot(1, AppointmentStatus.CANCELLED))
			.willReturn(List.of(appointment));

		SlotHold hold = new SlotHold();
		hold.setStartInstant(third);
		hold.setDurationMin(15);
		given(this.holdRepository.findActiveByVetId(eq(1), any(Instant.class))).willReturn(List.of(hold));

		RejectedSuggestion rejected = new RejectedSuggestion();
		rejected.setVet(this.vet);
		rejected.setStartInstant(fourth);
		given(this.rejectedRepository.findByRequestId(10)).willReturn(List.of(rejected));

		SchedulingCriteria criteria = new SchedulingCriteria(30, "surgery", 1, List.of(), List.of(),
				List.of(new SchedulingWindow(first, first.plusSeconds(60))));
		assertThat(this.service.findFeasibleCandidates(10, criteria)).extracting(RankedSlot::startInstant)
			.containsExactly(fifth);

		SchedulingCriteria wrongSpecialty = new SchedulingCriteria(30, "radiology", null, List.of(), List.of(),
				List.of());
		assertThat(this.service.findFeasibleCandidates(10, wrongSpecialty)).isEmpty();
	}

	@Test
	void assignsPreferredAndAllowedWindowRanks() {
		given(this.appointmentRepository.findByVetIdAndStatusNot(1, AppointmentStatus.CANCELLED)).willReturn(List.of());
		given(this.holdRepository.findActiveByVetId(eq(1), any(Instant.class))).willReturn(List.of());
		given(this.rejectedRepository.findByRequestId(10)).willReturn(List.of());
		Instant preferred = Instant.parse("2026-09-01T08:00:00Z");
		Instant allowed = preferred.plusSeconds(3600);
		given(this.gridGenerator.generateCandidateSlots(eq(1), any(LocalDate.class), eq(30), any(ClinicSettings.class)))
			.willReturn(List.of(new SlotCandidate(1, allowed), new SlotCandidate(1, preferred)));
		SchedulingCriteria criteria = new SchedulingCriteria(30, null, 1,
				List.of(new SchedulingWindow(preferred, preferred.plusSeconds(60))),
				List.of(new SchedulingWindow(allowed, allowed.plusSeconds(60))), List.of());

		assertThat(this.service.findFeasibleCandidates(10, criteria))
			.extracting(RankedSlot::windowRank, RankedSlot::preferredVet)
			.containsExactly(org.assertj.core.groups.Tuple.tuple(1, true),
					org.assertj.core.groups.Tuple.tuple(2, true));
	}

}
