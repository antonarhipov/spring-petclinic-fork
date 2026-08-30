package org.springframework.samples.petclinic.scheduling.matching;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.scheduling.appointment.HoldAcquisitionOutcome;
import org.springframework.samples.petclinic.scheduling.appointment.ReservationService;
import org.springframework.samples.petclinic.scheduling.appointment.StaleAcquisitionException;
import org.springframework.samples.petclinic.scheduling.audit.IntegrationExecution;
import org.springframework.samples.petclinic.scheduling.audit.IntegrationExecutionRepository;
import org.springframework.samples.petclinic.scheduling.config.SchedulingProperties;
import org.springframework.samples.petclinic.scheduling.job.IntegrationExecutionDispatcher;
import org.springframework.samples.petclinic.scheduling.queue.FallbackRoutingService;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchingCoordinatorTests {

	@Test
	void staleAcquisitionRetriesOnceThenHolds() {
		Harness harness = harness();
		CandidateSlot slot = slot();
		when(harness.snapshots.create(any(), any())).thenReturn(snapshot(slot));
		when(harness.solver.solve(any(), any())).thenReturn(selected(slot));
		when(harness.reservations.acquire(any(), any(), any(), any(), any())).thenThrow(new StaleAcquisitionException())
			.thenReturn(HoldAcquisitionOutcome.HELD);
		harness.coordinator.execute(harness.execution.getId());
		verify(harness.solver, times(2)).solve(any(), any());
		verify(harness.fallback, never()).ensureQueueItem(any(), any(), anyBoolean());
		assertThat(harness.execution.getOutcome()).isEqualTo("SELECTED");
		assertThat(harness.request.getState()).isEqualTo(RequestState.MATCHING);
	}

	@Test
	void secondStaleAcquisitionRoutesToStaffWithoutOffer() {
		Harness harness = harness();
		CandidateSlot slot = slot();
		when(harness.snapshots.create(any(), any())).thenReturn(snapshot(slot));
		when(harness.solver.solve(any(), any())).thenReturn(selected(slot));
		when(harness.reservations.acquire(any(), any(), any(), any(), any())).thenReturn(HoldAcquisitionOutcome.STALE,
				HoldAcquisitionOutcome.STALE);
		harness.coordinator.execute(harness.execution.getId());
		verify(harness.solver, times(2)).solve(any(), any());
		verify(harness.fallback).ensureQueueItem(1L, "STALE_ACQUISITION", false);
		assertThat(harness.request.getState()).isEqualTo(RequestState.STAFF_HANDLING);
		assertThat(harness.execution.getOutcome()).isEqualTo("STALE_ACQUISITION");
	}

	@Test
	void supersededExecutionDoesNotAcquireHold() {
		Harness harness = harness();
		harness.request.setState(RequestState.READY_FOR_SUGGESTION);
		harness.coordinator.execute(harness.execution.getId());
		verify(harness.solver, never()).solve(any(), any());
		verify(harness.reservations, never()).acquire(any(), any(), any(), any(), any());
		assertThat(harness.execution.getState()).isEqualTo("SUPERSEDED");
		assertThat(harness.execution.getOutcome()).isEqualTo("SUPERSEDED");
	}

	private Harness harness() {
		SchedulingRequestRepository requests = mock(SchedulingRequestRepository.class);
		IntegrationExecutionRepository executions = mock(IntegrationExecutionRepository.class);
		IntegrationExecutionDispatcher dispatcher = mock(IntegrationExecutionDispatcher.class);
		SlotSelectionSnapshotFactory snapshots = mock(SlotSelectionSnapshotFactory.class);
		TimefoldSlotSolver solver = mock(TimefoldSlotSolver.class);
		ReservationService reservations = mock(ReservationService.class);
		FallbackRoutingService fallback = mock(FallbackRoutingService.class);
		SchedulingProperties properties = new SchedulingProperties();
		Clock clock = Clock.fixed(Instant.parse("2026-03-16T14:00:00Z"), ZoneOffset.UTC);
		MatchingCoordinator coordinator = new MatchingCoordinator(requests, executions, dispatcher, snapshots, solver,
				reservations, fallback, properties, clock);
		SchedulingRequest request = new SchedulingRequest();
		org.springframework.test.util.ReflectionTestUtils.setField(request, "id", 1L);
		request.setState(RequestState.MATCHING);
		request.setActiveRequestRevisionId(9L);
		request.setOwnerId(1);
		when(requests.findById(1L)).thenReturn(Optional.of(request));
		when(requests.findByIdAndOwnerId(1L, 1)).thenReturn(Optional.of(request));
		IntegrationExecution execution = new IntegrationExecution();
		execution.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
		execution.setKind("TIMEFOLD_MATCH");
		execution.setRequestId(1L);
		execution.setRequestRevisionId(9L);
		execution.setState("RUNNING");
		execution.setInputJson("{\"mode\":\"PREFERRED_ONLY\"}");
		execution.setDeadlineAt(Instant.parse("2026-03-16T14:00:04Z"));
		execution.setTriggeredAt(Instant.parse("2026-03-16T14:00:00Z"));
		execution.setSchemaVersion("1.0");
		when(executions.findById(execution.getId())).thenReturn(Optional.of(execution));
		when(executions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		return new Harness(coordinator, request, execution, snapshots, solver, reservations, fallback);
	}

	private CandidateSlot slot() {
		Instant start = Instant.parse("2026-03-16T15:00:00Z");
		return new CandidateSlot("1@" + start, 1, start, start.plusSeconds(1800), "STANDARD", 0);
	}

	private SlotSelectionSnapshot snapshot(CandidateSlot slot) {
		Instant now = Instant.parse("2026-03-16T14:00:00Z");
		return new SlotSelectionSnapshot("1.0", "slot-selection-1", 1L, 9L, 0, MatchingMode.PREFERRED_ONLY, "UTC", now,
				now.plusSeconds(15 * 60), now.plusSeconds(7 * 24 * 3600), 30, 15, 1L, 7, null, 1, "NONE",
				List.of(new TimeWindow(slot.startAt(), slot.endAt().plusSeconds(1800), false)), List.of(), List.of(),
				Set.of(), List.of(new VetFact(1, Set.of())),
				List.of(new HoursFact(null, DayOfWeek.MONDAY, LocalTime.of(0, 0), LocalTime.of(23, 59))), List.of(),
				List.of(), List.of(slot));
	}

	private SlotSelectionResult selected(CandidateSlot slot) {
		return new SlotSelectionResult("SELECTED", slot, SlotScorePolicy.score(snapshot(slot), slot), true);
	}

	private record Harness(MatchingCoordinator coordinator, SchedulingRequest request, IntegrationExecution execution,
			SlotSelectionSnapshotFactory snapshots, TimefoldSlotSolver solver, ReservationService reservations,
			FallbackRoutingService fallback) {
	}

}
