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

package org.springframework.samples.petclinic.scheduling.request;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentLifecycleService;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationRepository;
import org.springframework.samples.petclinic.scheduling.solver.SlotRanker;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HoldLifecycleTests {

	private static final ZoneId ZONE = ZoneId.of("Europe/Amsterdam");

	private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 9, 7, 9, 0, 0, 0, ZONE);

	@Test
	@Tag("AC-57")
	void invalidStateCannotSuggest_AC57() {
		SchedulingRequestRepository requests = mock(SchedulingRequestRepository.class);
		RequestLifecycleService lifecycle = mock(RequestLifecycleService.class);
		HoldService holds = mock(HoldService.class);
		InterpretationRepository interpretations = mock(InterpretationRepository.class);
		SlotRanker ranker = mock(SlotRanker.class);
		AppointmentLifecycleService appointments = mock(AppointmentLifecycleService.class);
		VetRepository vets = mock(VetRepository.class);
		SuggestionService service = new SuggestionService(lifecycle, holds, interpretations, ranker, appointments, vets,
				Clock.fixed(NOW.toInstant(), ZONE));
		SchedulingRequest request = request(1, RequestState.AWAITING_CONSENT);

		assertThatThrownBy(() -> service.confirm(request, "owner"))
			.isInstanceOf(IllegalRequestTransitionException.class)
			.hasMessageContaining("suggest");

		assertThat(request.getState()).isEqualTo(RequestState.AWAITING_CONSENT);
		assertThat(request.hasHold()).isFalse();
		verifyNoInteractions(requests, lifecycle, holds, interpretations, ranker, appointments, vets);
	}

	@Test
	@Tag("AC-58")
	void firstRankedSlotBecomesOnlyHold_AC58() {
		Fixture fixture = fixture();
		SchedulingRequest request = request(11, RequestState.INTERPRETED);
		Vet firstVet = vet(1);
		Vet secondVet = vet(2);
		SlotRanker.RankedSlot first = slot(firstVet, NOW.plusDays(1), 30);
		SlotRanker.RankedSlot second = slot(secondVet, NOW.plusDays(2), 45);
		fixture.allowRanking(List.of(firstVet, secondVet), List.of(first, second));

		SchedulingRequest offered = fixture.service.confirm(request, "owner");

		assertThat(offered).isSameAs(request);
		assertThat(offered.getState()).isEqualTo(RequestState.SUGGESTION_OFFERED);
		assertThat(offered.getHeldVet()).isSameAs(firstVet);
		assertThat(offered.getHeldStart()).isEqualTo(first.startTime());
		assertThat(offered.getHeldDuration()).isEqualTo(first.duration());
		verify(fixture.requests).save(request);
		verify(fixture.events).save(any(SchedulingRequestEvent.class));
	}

	@Test
	@Tag("AC-59")
	void heldSlotUnavailableToOthers_AC59() {
		Fixture fixture = fixture();
		SchedulingRequest secondRequest = request(22, RequestState.INTERPRETED);
		Vet vet = vet(1);
		ZonedDateTime heldStart = NOW.plusDays(1);
		ZonedDateTime alternativeStart = heldStart.plusHours(2);
		SchedulingRequest firstOwnerHold = request(21, RequestState.SUGGESTION_OFFERED);
		firstOwnerHold.setHold(vet, heldStart, 30);
		when(fixture.requests.findActiveHoldsByVetId(vet.getId())).thenReturn(List.of(firstOwnerHold));
		fixture.allowRanking(List.of(vet), List.of(slot(vet, heldStart, 30), slot(vet, alternativeStart, 30)));

		SchedulingRequest offered = fixture.service.confirm(secondRequest, "other-owner");

		assertThat(offered.getHeldStart()).isEqualTo(alternativeStart);
		assertThat(offered.getHeldStart()).isNotEqualTo(firstOwnerHold.getHeldStart());
	}

	@Test
	@Tag("AC-60")
	void holdHasNoTimerAndOnlyNamedActionsRelease_AC60() {
		MutableClock clock = new MutableClock(NOW.toInstant(), ZONE);
		SchedulingRequestRepository requests = mock(SchedulingRequestRepository.class);
		SchedulingRequestEventRepository events = mock(SchedulingRequestEventRepository.class);
		when(requests.save(any(SchedulingRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
		RequestLifecycleService lifecycle = new RequestLifecycleService(requests, events, clock);
		Vet originalVet = vet(1);
		Vet replacementVet = vet(2);
		ZonedDateTime originalStart = NOW.plusDays(1);
		ZonedDateTime replacementStart = NOW.plusDays(2);

		SchedulingRequest timerless = heldRequest(31, originalVet, originalStart);
		clock.advance(Duration.ofDays(365));
		assertThat(timerless.hasHold()).isTrue();
		assertThat(timerless.getHeldStart()).isEqualTo(originalStart);

		SchedulingRequest accepted = heldRequest(32, originalVet, originalStart);
		lifecycle.acceptSuggestion(accepted, "owner");
		assertReleased(accepted, originalVet, originalStart);

		SchedulingRequest another = heldRequest(33, originalVet, originalStart);
		lifecycle.askForAnotherOptionSlotsRemain(another, "owner", replacementVet, replacementStart, 30);
		assertReplaced(another, originalVet, originalStart, replacementVet, replacementStart);

		SchedulingRequest routed = heldRequest(34, originalVet, originalStart);
		lifecycle.routeToStaff(routed, "owner", "owner requested staff");
		assertReleased(routed, originalVet, originalStart);

		SchedulingRequest edited = heldRequest(35, originalVet, originalStart);
		lifecycle.editText(edited, "owner", "new reason", "new availability");
		assertReleased(edited, originalVet, originalStart);

		SchedulingRequest abandoned = heldRequest(36, originalVet, originalStart);
		lifecycle.abandon(abandoned, "owner", "no longer needed");
		assertReleased(abandoned, originalVet, originalStart);

		SchedulingRequest superseded = heldRequest(37, originalVet, originalStart);
		lifecycle.staffPlaceSuggestion(superseded, "staff", replacementVet, replacementStart, 30);
		assertReplaced(superseded, originalVet, originalStart, replacementVet, replacementStart);

		SchedulingRequest staffReleased = heldRequest(38, originalVet, originalStart);
		lifecycle.staffReleaseHold(staffReleased, "staff", "clinic changed");
		assertReleased(staffReleased, originalVet, originalStart);
	}

	@Test
	@Tag("AC-61")
	void noSlotsRoutesToStaff_AC61() {
		Fixture fixture = fixture();
		SchedulingRequest request = request(41, RequestState.INTERPRETED);
		fixture.allowRanking(List.of(vet(1)), List.of());
		ArgumentCaptor<SchedulingRequestEvent> event = ArgumentCaptor.forClass(SchedulingRequestEvent.class);

		SchedulingRequest routed = fixture.service.confirm(request, "owner");

		assertThat(routed.getState()).isEqualTo(RequestState.WITH_STAFF);
		assertThat(routed.hasHold()).isFalse();
		verify(fixture.events).save(event.capture());
		assertThat(event.getValue().getFromState()).isEqualTo(RequestState.INTERPRETED);
		assertThat(event.getValue().getToState()).isEqualTo(RequestState.WITH_STAFF);
		assertThat(event.getValue().getAction()).isEqualTo("confirm no feasible slots");
		assertThat(event.getValue().getReason()).isEqualTo("No feasible slots available");
		verify(fixture.appointments, never()).bookAppointment(any(), any(), any(), anyInt(), any(), any(), any());
	}

	private static Fixture fixture() {
		SchedulingRequestRepository requests = mock(SchedulingRequestRepository.class);
		SchedulingRequestEventRepository events = mock(SchedulingRequestEventRepository.class);
		AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);
		InterpretationRepository interpretations = mock(InterpretationRepository.class);
		SlotRanker ranker = mock(SlotRanker.class);
		AppointmentLifecycleService appointments = mock(AppointmentLifecycleService.class);
		VetRepository vets = mock(VetRepository.class);
		when(requests.save(any(SchedulingRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(interpretations.findTopByRequestIdOrderByVersionDesc(anyInt())).thenReturn(Optional.empty());
		when(appointmentRepository.findConfirmedByVetIdAndDateRange(anyInt(), any(), any())).thenReturn(List.of());
		when(requests.findActiveHoldsByVetId(anyInt())).thenReturn(List.of());
		RequestLifecycleService lifecycle = new RequestLifecycleService(requests, events,
				Clock.fixed(NOW.toInstant(), ZONE));
		HoldService holds = new HoldService(requests, appointmentRepository, lifecycle);
		SuggestionService service = new SuggestionService(lifecycle, holds, interpretations, ranker, appointments, vets,
				Clock.fixed(NOW.toInstant(), ZONE));
		return new Fixture(requests, events, ranker, appointments, vets, service);
	}

	private static SchedulingRequest request(int id, RequestState state) {
		Owner owner = new Owner();
		owner.setId(id);
		Pet pet = new Pet();
		pet.setId(id);
		SchedulingRequest request = new SchedulingRequest();
		request.setId(id);
		request.setOwner(owner);
		request.setPet(pet);
		request.setState(state);
		request.setReasonText("checkup");
		request.setAvailabilityText("weekday morning");
		request.setActivePetId(pet.getId());
		request.setCreatedAt(NOW);
		request.setUpdatedAt(NOW);
		return request;
	}

	private static SchedulingRequest heldRequest(int id, Vet vet, ZonedDateTime start) {
		SchedulingRequest request = request(id, RequestState.SUGGESTION_OFFERED);
		request.setHold(vet, start, 30);
		return request;
	}

	private static Vet vet(int id) {
		Vet vet = new Vet();
		vet.setId(id);
		vet.setFirstName("Vet" + id);
		vet.setLastName("Test");
		return vet;
	}

	private static SlotRanker.RankedSlot slot(Vet vet, ZonedDateTime start, int duration) {
		return new SlotRanker.RankedSlot(vet, start, duration, "ranked", "0hard/0medium/0soft");
	}

	private static void assertReleased(SchedulingRequest request, Vet originalVet, ZonedDateTime originalStart) {
		assertThat(request.hasHold()).isFalse();
		assertThat(request.getHeldVet()).isNotSameAs(originalVet);
		assertThat(request.getHeldStart()).isNotEqualTo(originalStart);
	}

	private static void assertReplaced(SchedulingRequest request, Vet originalVet, ZonedDateTime originalStart,
			Vet replacementVet, ZonedDateTime replacementStart) {
		assertThat(request.hasHold()).isTrue();
		assertThat(request.getHeldVet()).isSameAs(replacementVet).isNotSameAs(originalVet);
		assertThat(request.getHeldStart()).isEqualTo(replacementStart).isNotEqualTo(originalStart);
	}

	private record Fixture(SchedulingRequestRepository requests, SchedulingRequestEventRepository events,
			SlotRanker ranker, AppointmentLifecycleService appointments, VetRepository vets,
			SuggestionService service) {

		void allowRanking(List<Vet> eligibleVets, List<SlotRanker.RankedSlot> rankedSlots) {
			when(this.vets.findAll()).thenReturn(eligibleVets);
			eligibleVets.forEach(vet -> when(this.vets.findByIdForUpdate(vet.getId())).thenReturn(Optional.of(vet)));
			when(this.ranker.rankSlots(any(SchedulingRequest.class),
					nullable(org.springframework.samples.petclinic.scheduling.interpretation.Interpretation.class)))
				.thenReturn(rankedSlots);
		}

	}

	private static final class MutableClock extends Clock {

		private Instant instant;

		private final ZoneId zone;

		private MutableClock(Instant instant, ZoneId zone) {
			this.instant = instant;
			this.zone = zone;
		}

		void advance(Duration duration) {
			this.instant = this.instant.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return this.zone;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return new MutableClock(this.instant, zone);
		}

		@Override
		public Instant instant() {
			return this.instant;
		}

	}

}
