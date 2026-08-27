/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.scheduling.solver;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.samples.petclinic.appointment.AppointmentRequestWorkflowService;
import org.springframework.samples.petclinic.appointment.ExpiredHoldCleanupService;
import org.springframework.samples.petclinic.appointment.SlotHold;
import org.springframework.samples.petclinic.appointment.SlotHoldAcquisitionService;
import org.springframework.samples.petclinic.appointment.SlotUnavailableException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchingCoordinatorTests {

	@Mock
	private SchedulingCandidateService candidateService;

	@Mock
	private SlotSolver slotSolver;

	@Mock
	private SlotHoldAcquisitionService acquisitionService;

	@Mock
	private ExpiredHoldCleanupService cleanupService;

	@Mock
	private AppointmentRequestWorkflowService workflowService;

	private MatchingCoordinator coordinator;

	private SchedulingCriteria criteria;

	@BeforeEach
	void setUp() {
		this.coordinator = new MatchingCoordinator(this.candidateService, this.slotSolver, this.acquisitionService,
				this.cleanupService, this.workflowService);
		this.criteria = new SchedulingCriteria(30, null, null, List.of(), List.of(), List.of());
	}

	@Test
	void solverFailureQueuesForStaff() {
		RankedSlot slot = new RankedSlot(1, Instant.parse("2026-09-01T08:00:00Z"), 0, false);
		given(this.candidateService.findFeasibleCandidates(10, this.criteria)).willReturn(List.of(slot));
		given(this.slotSolver.solve(List.of(slot))).willThrow(new IllegalStateException("solver down"));

		SuggestionResult result = this.coordinator.suggest(10, this.criteria);

		assertThat(result.status()).isEqualTo(SuggestionResult.Status.QUEUED_FOR_STAFF);
		verify(this.workflowService).markNoFit(10);
	}

	@Test
	void losingAcquisitionRaceImmediatelySolvesForAnotherCandidate() {
		RankedSlot first = new RankedSlot(1, Instant.parse("2026-09-01T08:00:00Z"), 0, false);
		RankedSlot second = new RankedSlot(2, Instant.parse("2026-09-01T09:00:00Z"), 0, false);
		given(this.candidateService.findFeasibleCandidates(10, this.criteria)).willReturn(List.of(first, second));
		given(this.slotSolver.solve(List.of(first, second))).willReturn(Optional.of(first));
		given(this.slotSolver.solve(List.of(second))).willReturn(Optional.of(second));
		given(this.acquisitionService.acquire(10, first, 30)).willThrow(new SlotUnavailableException());
		SlotHold acquired = new SlotHold();
		acquired.setId(42);
		acquired.setStartInstant(second.startInstant());
		acquired.setExpiresAt(Instant.parse("2026-09-01T09:10:00Z"));
		given(this.acquisitionService.acquire(10, second, 30)).willReturn(acquired);

		SuggestionResult result = this.coordinator.suggest(10, this.criteria);

		assertThat(result.vetId()).isEqualTo(2);
		verify(this.workflowService).attachHold(eq(10), any(SlotHold.class));
	}

}
