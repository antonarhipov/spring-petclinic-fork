/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.scheduling.solver;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.samples.petclinic.appointment.Appointment;
import org.springframework.samples.petclinic.appointment.AppointmentRequestWorkflowService;
import org.springframework.samples.petclinic.appointment.AppointmentRequestWorkflowService.CurrentOffer;
import org.springframework.samples.petclinic.appointment.ExpiredHoldCleanupService;
import org.springframework.samples.petclinic.appointment.SlotHold;
import org.springframework.samples.petclinic.appointment.SlotHoldAcquisitionService;
import org.springframework.samples.petclinic.appointment.SlotUnavailableException;
import org.springframework.stereotype.Service;

@Service
public class MatchingCoordinator {

	private final SchedulingCandidateService candidateService;

	private final SlotSolver slotSolver;

	private final SlotHoldAcquisitionService holdAcquisitionService;

	private final ExpiredHoldCleanupService cleanupService;

	private final AppointmentRequestWorkflowService workflowService;

	public MatchingCoordinator(SchedulingCandidateService candidateService, SlotSolver slotSolver,
			SlotHoldAcquisitionService holdAcquisitionService, ExpiredHoldCleanupService cleanupService,
			AppointmentRequestWorkflowService workflowService) {
		this.candidateService = candidateService;
		this.slotSolver = slotSolver;
		this.holdAcquisitionService = holdAcquisitionService;
		this.cleanupService = cleanupService;
		this.workflowService = workflowService;
	}

	public SuggestionResult suggest(Integer requestId, SchedulingCriteria criteria) {
		return suggest(requestId, criteria, "A suitable slot is available");
	}

	public SuggestionResult rejectAndSuggestAgain(Integer requestId, SchedulingCriteria criteria) {
		this.workflowService.rejectCurrent(requestId);
		return suggest(requestId, criteria, "Here is another option");
	}

	public AcceptResult accept(Integer requestId, SchedulingCriteria criteria) {
		this.cleanupService.cleanupExpired();
		CurrentOffer offer = this.workflowService.getCurrentOffer(requestId);

		if (offer.activeHoldId() != null) {
			Optional<Appointment> confirmed = this.workflowService.confirmHeld(requestId);
			if (confirmed.isPresent()) {
				return new AcceptResult(AcceptResult.Status.SCHEDULED, confirmed.get(), null, "Appointment scheduled");
			}
			return replacementAfterLoss(requestId, criteria);
		}

		List<RankedSlot> currentCandidates = this.candidateService.findFeasibleCandidates(requestId, criteria);
		Optional<RankedSlot> sameSlot = currentCandidates.stream()
			.filter(candidate -> candidate.vetId().equals(offer.vetId())
					&& candidate.startInstant().equals(offer.startInstant()))
			.findFirst();
		if (sameSlot.isEmpty()) {
			this.workflowService.markSuggestionLost(requestId);
			return replacementAfterLoss(requestId, criteria);
		}

		try {
			SlotHold reacquired = this.holdAcquisitionService.acquire(requestId, sameSlot.get(),
					criteria.durationMin());
			this.workflowService.attachHold(requestId, reacquired);
			Optional<Appointment> confirmed = this.workflowService.confirmHeld(requestId);
			if (confirmed.isPresent()) {
				return new AcceptResult(AcceptResult.Status.SCHEDULED, confirmed.get(), null,
						"Appointment scheduled after revalidating the expired hold");
			}
		}
		catch (SlotUnavailableException ex) {
			this.workflowService.markSuggestionLost(requestId);
		}
		return replacementAfterLoss(requestId, criteria);
	}

	private AcceptResult replacementAfterLoss(Integer requestId, SchedulingCriteria criteria) {
		SuggestionResult next = suggest(requestId, criteria,
				"That slot is no longer available; here is the next option");
		AcceptResult.Status status = next.status() == SuggestionResult.Status.HELD ? AcceptResult.Status.REPLACED
				: AcceptResult.Status.QUEUED_FOR_STAFF;
		return new AcceptResult(status, null, next, next.message());
	}

	private SuggestionResult suggest(Integer requestId, SchedulingCriteria criteria, String message) {
		this.cleanupService.cleanupExpired();
		this.workflowService.beginSuggestion(requestId);
		Set<RankedSlot> lostRaces = new HashSet<>();

		while (true) {
			List<RankedSlot> candidates = this.candidateService.findFeasibleCandidates(requestId, criteria)
				.stream()
				.filter(candidate -> !lostRaces.contains(candidate))
				.toList();
			Optional<RankedSlot> best;
			try {
				best = this.slotSolver.solve(candidates);
			}
			catch (RuntimeException ex) {
				this.workflowService.markNoFit(requestId);
				return SuggestionResult.queued(requestId, "Scheduling is unavailable; clinic staff will help");
			}
			if (best.isEmpty()) {
				this.workflowService.markNoFit(requestId);
				return SuggestionResult.queued(requestId, "No feasible slot remains; clinic staff will help");
			}

			try {
				SlotHold hold = this.holdAcquisitionService.acquire(requestId, best.get(), criteria.durationMin());
				try {
					this.workflowService.attachHold(requestId, hold);
				}
				catch (RuntimeException ex) {
					this.holdAcquisitionService.release(hold.getId());
					throw ex;
				}
				return SuggestionResult.held(requestId, best.get().vetId(), best.get().startInstant(),
						hold.getExpiresAt(), message);
			}
			catch (SlotUnavailableException ex) {
				lostRaces.add(best.get());
			}
		}
	}

}
