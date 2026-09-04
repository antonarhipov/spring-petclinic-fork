/*
 * Copyright 2012-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package org.springframework.samples.petclinic.scheduling.request;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentLifecycleService;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationRepository;
import org.springframework.samples.petclinic.scheduling.solver.SlotRanker;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SuggestionServiceOrderingTests {

	@Test
	@org.junit.jupiter.api.DisplayName("AC-63: accept locks vet before overlap recheck and creates nothing on conflict")
	void acceptLocksVetBeforeRecheckingAndRefusesAConflictingAppointment() {
		SchedulingRequestRepository requests = mock(SchedulingRequestRepository.class);
		RequestLifecycleService lifecycle = mock(RequestLifecycleService.class);
		InterpretationRepository interpretations = mock(InterpretationRepository.class);
		SlotRanker ranker = mock(SlotRanker.class);
		AppointmentLifecycleService appointments = mock(AppointmentLifecycleService.class);
		AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);
		VetRepository vets = mock(VetRepository.class);
		Clock clock = Clock.fixed(Instant.parse("2026-09-07T07:00:00Z"), ZoneId.of("Europe/Amsterdam"));
		SuggestionService service = new SuggestionService(requests, lifecycle, interpretations, ranker, appointments,
				appointmentRepository, vets, clock);

		Vet vet = new Vet();
		vet.setId(1);
		Pet pet = new Pet();
		pet.setId(1);
		ZonedDateTime heldStart = ZonedDateTime.now(clock).plusDays(1);
		SchedulingRequest request = new SchedulingRequest();
		request.setId(41);
		request.setState(RequestState.SUGGESTION_OFFERED);
		request.setPet(pet);
		request.setHold(vet, heldStart, 30);

		Appointment conflict = new Appointment();
		conflict.setVet(vet);
		conflict.setStartTime(heldStart.plusMinutes(15));
		conflict.setDuration(30);
		when(vets.findByIdForUpdate(1)).thenReturn(Optional.of(vet));
		when(appointmentRepository.findConfirmedByVetIdAndDateRange(any(), any(), any())).thenReturn(List.of(conflict));
		when(interpretations.findTopByRequestIdOrderByVersionDesc(41)).thenReturn(Optional.empty());
		when(ranker.rankSlots(request, null)).thenReturn(List.of());

		assertThatThrownBy(() -> service.accept(request, "owner")).isInstanceOf(IllegalStateException.class);

		InOrder order = inOrder(vets, appointmentRepository);
		order.verify(vets).findByIdForUpdate(1);
		order.verify(appointmentRepository).findConfirmedByVetIdAndDateRange(any(), any(), any());
		verify(appointments, never()).bookAppointment(any(), any(), any(), any(Integer.class), any(), any(), any());
		verify(lifecycle).acceptSlotLostNoneLeft(request, "owner", "Hold lost and no alternatives remain");
	}

}
