/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.scheduling.solver;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.samples.petclinic.PetClinicApplication;
import org.springframework.samples.petclinic.appointment.Appointment;
import org.springframework.samples.petclinic.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.appointment.AppointmentRequest;
import org.springframework.samples.petclinic.appointment.AppointmentRequestRepository;
import org.springframework.samples.petclinic.appointment.AppointmentRequestStatus;
import org.springframework.samples.petclinic.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.appointment.ExpiredHoldCleanupService;
import org.springframework.samples.petclinic.appointment.SlotHold;
import org.springframework.samples.petclinic.appointment.SlotHoldAcquisitionService;
import org.springframework.samples.petclinic.appointment.SlotHoldRepository;
import org.springframework.samples.petclinic.appointment.SlotUnavailableException;
import org.springframework.samples.petclinic.calendar.VetWeeklyShift;
import org.springframework.samples.petclinic.calendar.VetWeeklyShiftRepository;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = { PetClinicApplication.class, MatchingCoordinatorIntegrationTests.FixedClockConfig.class },
		properties = { "petclinic.demo-seeding=false", "timefold.solver.termination.spent-limit=50ms" })
@Execution(ExecutionMode.SAME_THREAD)
class MatchingCoordinatorIntegrationTests {

	private static final Instant NOW = Instant.parse("2026-08-28T08:00:00Z");

	private static final ZoneId ZONE = ZoneId.of("Europe/Amsterdam");

	@Autowired
	private MatchingCoordinator coordinator;

	@Autowired
	private AppointmentRequestRepository requestRepository;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private SlotHoldRepository holdRepository;

	@Autowired
	private SlotHoldAcquisitionService acquisitionService;

	@Autowired
	private ExpiredHoldCleanupService cleanupService;

	@Autowired
	private VetWeeklyShiftRepository shiftRepository;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private VetRepository vetRepository;

	@Test
	void rejectPermanentlyExcludesTheOfferAndAcceptSchedulesTheNextOne() {
		AppointmentRequest request = createConfirmedRequest();
		LocalDate date = LocalDate.of(2026, 9, 1);
		addShift(1, date.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(11, 0));
		SchedulingCriteria criteria = preferredCriteria(date, 1);

		SuggestionResult first = this.coordinator.suggest(request.getId(), criteria);
		SuggestionResult second = this.coordinator.rejectAndSuggestAgain(request.getId(), criteria);

		assertThat(first.status()).isEqualTo(SuggestionResult.Status.HELD);
		assertThat(second.status()).isEqualTo(SuggestionResult.Status.HELD);
		assertThat(second.startInstant()).isNotEqualTo(first.startInstant());
		assertThat(this.holdRepository.findByRequestId(request.getId())).hasSize(1);

		AcceptResult accepted = this.coordinator.accept(request.getId(), criteria);
		assertThat(accepted.status()).isEqualTo(AcceptResult.Status.SCHEDULED);
		assertThat(accepted.appointment().getStartInstant()).isEqualTo(second.startInstant());
		assertThat(this.requestRepository.findById(request.getId()).orElseThrow().getStatus())
			.isEqualTo(AppointmentRequestStatus.SCHEDULED);
	}

	@Test
	void expiredHoldIsCleanedButItsSnapshotCanBeReacquiredAndAccepted() {
		AppointmentRequest request = createConfirmedRequest();
		LocalDate date = LocalDate.of(2026, 9, 2);
		addShift(2, date.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(10, 0));
		SchedulingCriteria criteria = preferredCriteria(date, 2);
		SuggestionResult offered = this.coordinator.suggest(request.getId(), criteria);

		SlotHold hold = this.holdRepository.findByRequestId(request.getId()).getFirst();
		hold.setExpiresAt(NOW.minusSeconds(1));
		this.holdRepository.saveAndFlush(hold);
		assertThat(this.cleanupService.cleanupExpiredAt(NOW)).isEqualTo(1);
		AppointmentRequest afterCleanup = this.requestRepository.findById(request.getId()).orElseThrow();
		assertThat(afterCleanup.getActiveHoldId()).isNull();
		assertThat(afterCleanup.getSuggestedStartInstant()).isEqualTo(offered.startInstant());

		AcceptResult accepted = this.coordinator.accept(request.getId(), criteria);
		assertThat(accepted.status()).isEqualTo(AcceptResult.Status.SCHEDULED);
		assertThat(accepted.appointment().getStartInstant()).isEqualTo(offered.startInstant());
	}

	@Test
	void expiredAndTakenOfferAutomaticallyAdvancesAndHoldRacesDoNotDeadEnd() {
		AppointmentRequest request = createConfirmedRequest();
		LocalDate date = LocalDate.of(2026, 9, 3);
		addShift(3, date.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(11, 0));
		SchedulingCriteria criteria = preferredCriteria(date, 3);
		SuggestionResult offered = this.coordinator.suggest(request.getId(), criteria);

		AppointmentRequest competingRequest = createConfirmedRequest();
		assertThatThrownBy(() -> this.acquisitionService.acquire(competingRequest.getId(),
				new RankedSlot(offered.vetId(), offered.startInstant(), 2, true), 30))
			.isInstanceOf(SlotUnavailableException.class);

		SlotHold hold = this.holdRepository.findByRequestId(request.getId()).getFirst();
		hold.setExpiresAt(NOW.minusSeconds(1));
		this.holdRepository.saveAndFlush(hold);
		this.cleanupService.cleanupExpiredAt(NOW);
		bookAppointmentAt(offered.vetId(), offered.startInstant());

		AcceptResult result = this.coordinator.accept(request.getId(), criteria);
		assertThat(result.status()).isEqualTo(AcceptResult.Status.REPLACED);
		assertThat(result.nextSuggestion().startInstant()).isNotEqualTo(offered.startInstant());
		assertThat(result.message()).contains("no longer available");
	}

	@Test
	void noFitQueuesTheRequestAndUndefinedTransitionLeavesStateUnchanged() {
		AppointmentRequest noFit = createConfirmedRequest();
		SchedulingCriteria impossible = new SchedulingCriteria(30, "oncology", null, List.of(), List.of(), List.of());
		assertThat(this.coordinator.suggest(noFit.getId(), impossible).status())
			.isEqualTo(SuggestionResult.Status.QUEUED_FOR_STAFF);
		assertThat(this.requestRepository.findById(noFit.getId()).orElseThrow().getStatus())
			.isEqualTo(AppointmentRequestStatus.QUEUED_FOR_STAFF);

		AppointmentRequest draft = createRequest(AppointmentRequestStatus.DRAFT);
		assertThatThrownBy(() -> this.coordinator.suggest(draft.getId(), impossible))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Cannot begin suggestion");
		assertThat(this.requestRepository.findById(draft.getId()).orElseThrow().getStatus())
			.isEqualTo(AppointmentRequestStatus.DRAFT);
	}

	private AppointmentRequest createConfirmedRequest() {
		return createRequest(AppointmentRequestStatus.CONFIRMED);
	}

	private AppointmentRequest createRequest(AppointmentRequestStatus status) {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().getFirst();
		AppointmentRequest request = new AppointmentRequest();
		request.setOwner(owner);
		request.setPet(pet);
		request.setFreeText("Need an appointment");
		request.setStatus(status);
		return this.requestRepository.saveAndFlush(request);
	}

	private void addShift(int vetId, DayOfWeek day, LocalTime start, LocalTime end) {
		Vet vet = this.vetRepository.findById(vetId).orElseThrow();
		this.shiftRepository.saveAndFlush(new VetWeeklyShift(vet, day.getValue(), start, end));
	}

	private SchedulingCriteria preferredCriteria(LocalDate date, int preferredVetId) {
		Instant start = date.atTime(9, 0).atZone(ZONE).toInstant();
		Instant end = date.atTime(11, 0).atZone(ZONE).toInstant();
		return new SchedulingCriteria(30, null, preferredVetId, List.of(new SchedulingWindow(start, end)), List.of(),
				List.of());
	}

	private void bookAppointmentAt(int vetId, Instant start) {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Appointment appointment = new Appointment();
		appointment.setPet(owner.getPets().getFirst());
		appointment.setVet(this.vetRepository.findById(vetId).orElseThrow());
		appointment.setStartInstant(start);
		appointment.setDurationMin(30);
		appointment.setStatus(AppointmentStatus.SCHEDULED);
		this.appointmentRepository.saveAndFlush(appointment);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClockConfig {

		@Bean
		@Primary
		Clock fixedSchedulingClock() {
			return Clock.fixed(NOW, ZoneOffset.UTC);
		}

	}

}
