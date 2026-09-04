/* Copyright 2012-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package org.springframework.samples.petclinic.scheduling.request;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentLifecycleService;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicOpeningHourRepository;
import org.springframework.samples.petclinic.scheduling.clinic.VetExceptionRepository;
import org.springframework.samples.petclinic.scheduling.clinic.VetWeeklyBlockRepository;
import org.springframework.samples.petclinic.scheduling.interpretation.Interpretation;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationRepository;
import org.springframework.samples.petclinic.scheduling.solver.SlotRanker;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AskAnotherOptionTests {

	private static final ZoneId ZONE = ZoneId.of("Europe/Amsterdam");

	private static final ZonedDateTime START = ZonedDateTime.of(2026, 9, 8, 10, 0, 0, 0, ZONE);

	@Test
	@Tag("AC-66")
	void rejectReleasesAndOffersNextWithoutCap_AC66() {
		Fixture fixture = fixture();
		SchedulingRequest request = offeredRequest(fixture.vet, START);
		List<ZonedDateTime> offered = new ArrayList<>();
		for (int index = 1; index <= 12; index++) {
			ZonedDateTime next = START.plusHours(index);
			when(fixture.ranker.rankSlots(request, fixture.interpretation))
				.thenReturn(List.of(slot(fixture.vet, next)));

			fixture.service.requestAnotherOption(request, "owner", RejectionScope.NOT_THIS_TIME.name());

			offered.add(request.getHeldStart());
			assertThat(request.getHeldStart()).isEqualTo(next);
		}

		assertThat(offered).hasSize(12).doesNotHaveDuplicates().doesNotContain(START);
		verify(fixture.events, org.mockito.Mockito.times(12)).saveAndFlush(any(SchedulingRequestEvent.class));
	}

	@Test
	@Tag("AC-67")
	void notThisTimeScope_AC67() {
		SuggestionRejection rejection = new SuggestionRejection(3, 2, START, RejectionScope.NOT_THIS_TIME);

		assertThat(rejection.excludes(2, START, 3)).isTrue();
		assertThat(rejection.excludes(2, START.plusMinutes(15), 3)).isFalse();
		assertThat(rejection.excludes(3, START, 3)).isFalse();
		assertThat(rejection.excludes(2, START, 4)).isFalse();
	}

	@Test
	@Tag("AC-68")
	void notThisDayScope_AC68() {
		SuggestionRejection rejection = new SuggestionRejection(3, 2, START, RejectionScope.NOT_THIS_DAY);

		assertThat(rejection.excludes(1, START.plusHours(4), 3)).isTrue();
		assertThat(rejection.excludes(9, START.minusHours(1), 3)).isTrue();
		assertThat(rejection.excludes(2, START.plusDays(1), 3)).isFalse();
		assertThat(rejection.excludes(2, START, 4)).isFalse();
	}

	@Test
	@Tag("AC-69")
	void notThisVetScope_AC69() {
		SuggestionRejection rejection = new SuggestionRejection(3, 2, START, RejectionScope.NOT_THIS_VET);

		assertThat(rejection.excludes(2, START.plusDays(30), 3)).isTrue();
		assertThat(rejection.excludes(2, START.minusDays(1), 3)).isTrue();
		assertThat(rejection.excludes(3, START, 3)).isFalse();
		assertThat(rejection.excludes(2, START, 4)).isFalse();
	}

	@Test
	@Tag("AC-70")
	void editingTextClearsAppliedExclusions_AC70() {
		SchedulingRequestRepository requests = mock(SchedulingRequestRepository.class);
		SchedulingRequestEventRepository events = mock(SchedulingRequestEventRepository.class);
		when(requests.save(any(SchedulingRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
		RequestLifecycleService lifecycle = new RequestLifecycleService(requests, events,
				Clock.fixed(START.toInstant(), ZONE));
		Vet vet = vet(2);
		SchedulingRequest request = offeredRequest(vet, START);
		SuggestionRejection old = new SuggestionRejection(1, vet.getId(), START, RejectionScope.NOT_THIS_DAY);
		SchedulingRequestEvent audit = rejectionEvent(request, old);

		lifecycle.editText(request, "owner", "updated reason", "updated availability");

		assertThat(request.getState()).isEqualTo(RequestState.AWAITING_CONSENT);
		assertThat(request.hasHold()).isFalse();
		assertThat(SuggestionRejection.activeFor(List.of(audit), 1)).containsExactly(old);
		assertThat(SuggestionRejection.activeFor(List.of(audit), 2)).isEmpty();
		verify(events, never()).delete(any(SchedulingRequestEvent.class));
		verify(events, never()).deleteAll();
	}

	private static Fixture fixture() {
		SchedulingRequestRepository requests = mock(SchedulingRequestRepository.class);
		SchedulingRequestEventRepository events = mock(SchedulingRequestEventRepository.class);
		AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);
		RequestLifecycleService lifecycle = new RequestLifecycleService(requests, events,
				Clock.fixed(START.toInstant(), ZONE));
		HoldService holds = new HoldService(requests, appointmentRepository, lifecycle);
		InterpretationRepository interpretations = mock(InterpretationRepository.class);
		SlotRanker ranker = mock(SlotRanker.class);
		AppointmentLifecycleService appointments = mock(AppointmentLifecycleService.class);
		VetRepository vets = mock(VetRepository.class);
		Vet vet = vet(2);
		Interpretation interpretation = new Interpretation();
		interpretation.setVersion(1);
		when(interpretations.findTopByRequestIdOrderByVersionDesc(anyInt())).thenReturn(Optional.of(interpretation));
		when(requests.save(any(SchedulingRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(events.saveAndFlush(any(SchedulingRequestEvent.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));
		when(vets.findAll()).thenReturn(List.of(vet));
		when(vets.findByIdForUpdate(vet.getId())).thenReturn(Optional.of(vet));
		when(appointmentRepository.findConfirmedByVetIdAndDateRange(anyInt(), any(), any())).thenReturn(List.of());
		when(requests.findActiveHoldsByVetId(anyInt())).thenReturn(List.of());
		SuggestionService service = new SuggestionService(lifecycle, holds, interpretations, ranker, appointments, vets,
				mock(ClinicOpeningHourRepository.class), mock(VetWeeklyBlockRepository.class),
				mock(VetExceptionRepository.class), events, Clock.fixed(START.toInstant(), ZONE));
		return new Fixture(events, ranker, vet, interpretation, service);
	}

	private static SchedulingRequest offeredRequest(Vet vet, ZonedDateTime start) {
		Owner owner = new Owner();
		owner.setId(1);
		Pet pet = new Pet();
		pet.setId(1);
		SchedulingRequest request = new SchedulingRequest();
		request.setId(1);
		request.setOwner(owner);
		request.setPet(pet);
		request.setState(RequestState.SUGGESTION_OFFERED);
		request.setReasonText("reason");
		request.setAvailabilityText("availability");
		request.setHold(vet, start, 30);
		request.setActivePetId(1);
		request.setCreatedAt(START);
		request.setUpdatedAt(START);
		return request;
	}

	private static Vet vet(int id) {
		Vet vet = new Vet();
		vet.setId(id);
		vet.setFirstName("Scope");
		vet.setLastName("Vet");
		return vet;
	}

	private static SlotRanker.RankedSlot slot(Vet vet, ZonedDateTime start) {
		return new SlotRanker.RankedSlot(vet, start, 30, "next", "0hard/0medium/0soft");
	}

	private static SchedulingRequestEvent rejectionEvent(SchedulingRequest request, SuggestionRejection rejection) {
		SchedulingRequestEvent event = new SchedulingRequestEvent();
		event.setRequest(request);
		event.setFromState(RequestState.SUGGESTION_OFFERED);
		event.setToState(RequestState.SUGGESTION_OFFERED);
		event.setActor("owner");
		event.setAction(SuggestionRejection.EVENT_ACTION);
		event.setReason(rejection.scope().name());
		event.setPayload(rejection.payload());
		event.setTimestamp(START);
		return event;
	}

	private record Fixture(SchedulingRequestEventRepository events, SlotRanker ranker, Vet vet,
			Interpretation interpretation, SuggestionService service) {
	}

}
