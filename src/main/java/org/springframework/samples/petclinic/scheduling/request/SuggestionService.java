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
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentLifecycleService;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicOpeningHour;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicOpeningHourRepository;
import org.springframework.samples.petclinic.scheduling.clinic.VetExceptionRepository;
import org.springframework.samples.petclinic.scheduling.clinic.VetWeeklyBlock;
import org.springframework.samples.petclinic.scheduling.clinic.VetWeeklyBlockRepository;
import org.springframework.samples.petclinic.scheduling.interpretation.Interpretation;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationRepository;
import org.springframework.samples.petclinic.scheduling.solver.SlotRanker;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service orchestrating suggestion generation, per-vet locking, hold reservation, and
 * booking confirmation (RULE-8, RULE-10, RULE-11, AC-58, AC-63).
 */
@Service
@Transactional
public class SuggestionService {

	private final RequestLifecycleService requestLifecycleService;

	private final HoldService holdService;

	private final InterpretationRepository interpretationRepository;

	private final SlotRanker slotRanker;

	private final AppointmentLifecycleService appointmentLifecycleService;

	private final VetRepository vetRepository;

	private final ClinicOpeningHourRepository openingHourRepository;

	private final VetWeeklyBlockRepository weeklyBlockRepository;

	private final VetExceptionRepository exceptionRepository;

	private final Clock clock;

	public SuggestionService(RequestLifecycleService requestLifecycleService, HoldService holdService,
			InterpretationRepository interpretationRepository, SlotRanker slotRanker,
			AppointmentLifecycleService appointmentLifecycleService, VetRepository vetRepository,
			ClinicOpeningHourRepository openingHourRepository, VetWeeklyBlockRepository weeklyBlockRepository,
			VetExceptionRepository exceptionRepository, Clock clock) {
		this.requestLifecycleService = requestLifecycleService;
		this.holdService = holdService;
		this.interpretationRepository = interpretationRepository;
		this.slotRanker = slotRanker;
		this.appointmentLifecycleService = appointmentLifecycleService;
		this.vetRepository = vetRepository;
		this.openingHourRepository = openingHourRepository;
		this.weeklyBlockRepository = weeklyBlockRepository;
		this.exceptionRepository = exceptionRepository;
		this.clock = clock;
	}

	public SchedulingRequest confirm(SchedulingRequest request, String actor) {
		Objects.requireNonNull(request, "request must not be null");
		if (request.getState() != RequestState.INTERPRETED && request.getState() != RequestState.SUGGESTION_OFFERED) {
			throw new IllegalRequestTransitionException(request.getState(), "suggest");
		}
		// The Timefold call must observe the same locked availability snapshot that is
		// revalidated before the hold is written (RULE-10/RULE-11). Lock in stable id
		// order to avoid lock-order inversions when several vets are eligible.
		this.vetRepository.findAll()
			.stream()
			.sorted((left, right) -> Integer.compare(left.getId(), right.getId()))
			.forEach(vet -> this.vetRepository.findByIdForUpdate(vet.getId()).orElseThrow());

		Interpretation interpretation = this.interpretationRepository
			.findTopByRequestIdOrderByVersionDesc(request.getId())
			.orElse(null);

		List<SlotRanker.RankedSlot> rankedSlots = this.slotRanker.rankSlots(request, interpretation);

		if (rankedSlots.isEmpty()) {
			return this.holdService.exhausted(request, actor, "No feasible slots available");
		}

		for (SlotRanker.RankedSlot candidate : rankedSlots) {
			Vet vet = candidate.vet();
			if (vet != null && vet.getId() != null) {
				// Acquire pessimistic write lock on the vet row (RULE-10)
				Vet lockedVet = this.vetRepository.findByIdForUpdate(vet.getId()).orElseThrow();

				// Verify slot availability against confirmed appointments and active
				// holds
				if (this.holdService.isAvailable(vet.getId(), candidate.startTime(), candidate.duration(),
						request.getId())) {
					SlotRanker.RankedSlot lockedCandidate = new SlotRanker.RankedSlot(lockedVet, candidate.startTime(),
							candidate.duration(), candidate.explanation(), candidate.score());
					return this.holdService.offer(request, actor, lockedCandidate);
				}
			}
		}

		return this.holdService.exhausted(request, actor, "All candidate slots conflicted");
	}

	public Appointment accept(SchedulingRequest request, String actor) {
		Objects.requireNonNull(request, "request must not be null");

		if (request.getState() != RequestState.SUGGESTION_OFFERED || !request.hasHold()) {
			throw new IllegalRequestTransitionException(request.getState(), "accept");
		}

		Vet heldVet = request.getHeldVet();
		ZonedDateTime heldStart = request.getHeldStart();
		int heldDuration = request.getHeldDuration();

		// Acquire pessimistic write lock on held vet (RULE-10)
		heldVet = this.vetRepository.findByIdForUpdate(heldVet.getId()).orElseThrow();

		// Re-validate hold under lock (RULE-10)
		if (!isCurrentHoldValid(request)) {
			recoverLostHold(request, actor, false);
			return null;
		}

		// Hold is valid: book appointment and mark request accepted
		Appointment appointment = this.appointmentLifecycleService.bookAppointment(request.getPet(), heldVet, heldStart,
				heldDuration, request.getReasonText(), request, actor);

		this.requestLifecycleService.acceptSuggestion(request, actor);
		return appointment;
	}

	/**
	 * Revalidates an offered hold when its detail page is opened. Returns {@code true}
	 * when the stale hold was replaced or the request was handed to staff.
	 */
	public boolean revalidateOnView(SchedulingRequest request, String actor) {
		Objects.requireNonNull(request, "request must not be null");
		if (request.getState() != RequestState.SUGGESTION_OFFERED || !request.hasHold()) {
			return false;
		}
		this.vetRepository.findByIdForUpdate(request.getHeldVet().getId()).orElseThrow();
		if (isCurrentHoldValid(request)) {
			return false;
		}
		recoverLostHold(request, actor, true);
		return true;
	}

	/**
	 * HTTP entry point for the ask-another action. Rejection persistence and scoped
	 * re-ranking are added by the dedicated ask-another slice; until then this method
	 * exposes the action only in its legal request state without mutating the hold.
	 */
	public SchedulingRequest requestAnotherOption(SchedulingRequest request, String actor, String scope) {
		Objects.requireNonNull(request, "request must not be null");
		Objects.requireNonNull(actor, "actor must not be null");
		Objects.requireNonNull(scope, "scope must not be null");
		if (request.getState() != RequestState.SUGGESTION_OFFERED) {
			throw new IllegalRequestTransitionException(request.getState(), "ask for another option");
		}
		return request;
	}

	private boolean isCurrentHoldValid(SchedulingRequest request) {
		Vet vet = request.getHeldVet();
		ZonedDateTime start = request.getHeldStart();
		int duration = request.getHeldDuration();
		ZonedDateTime end = start.plusMinutes(duration);
		if (start.isBefore(ZonedDateTime.now(this.clock))) {
			return false;
		}
		boolean overlapFree = this.holdService.isAvailable(vet.getId(), start, duration, request.getId());
		boolean withinOpening = this.openingHourRepository.findAll()
			.stream()
			.filter(hours -> hours.getDayOfWeek() == start.getDayOfWeek())
			.filter(hours -> !hours.isClosed())
			.anyMatch(hours -> contains(hours, start, end));
		boolean withinBlock = this.weeklyBlockRepository.findByVetId(vet.getId())
			.stream()
			.filter(block -> block.getDayOfWeek() == start.getDayOfWeek())
			.anyMatch(block -> contains(block, start, end));
		boolean unavailable = this.exceptionRepository.findByVetId(vet.getId())
			.stream()
			.anyMatch(
					exception -> exception.isUnavailable() && exception.getExceptionDate().equals(start.toLocalDate()));
		return overlapFree && withinOpening && withinBlock && !unavailable;
	}

	private void recoverLostHold(SchedulingRequest request, String actor, boolean viewing) {
		Interpretation interpretation = this.interpretationRepository
			.findTopByRequestIdOrderByVersionDesc(request.getId())
			.orElse(null);
		List<SlotRanker.RankedSlot> nextSlots = this.slotRanker.rankSlots(request, interpretation);
		for (SlotRanker.RankedSlot candidate : nextSlots) {
			if (candidate.vet() == null || candidate.vet().getId() == null) {
				continue;
			}
			Vet lockedVet = this.vetRepository.findByIdForUpdate(candidate.vet().getId()).orElseThrow();
			if (this.holdService.isAvailable(lockedVet.getId(), candidate.startTime(), candidate.duration(),
					request.getId())) {
				if (viewing) {
					this.requestLifecycleService.viewRevalidateNextExists(request, actor, lockedVet,
							candidate.startTime(), candidate.duration());
				}
				else {
					this.requestLifecycleService.acceptSlotLostNextExists(request, actor, lockedVet,
							candidate.startTime(), candidate.duration());
				}
				return;
			}
		}
		if (viewing) {
			this.requestLifecycleService.viewRevalidateNoneLeft(request, actor, "Held slot became unavailable");
		}
		else {
			this.requestLifecycleService.acceptSlotLostNoneLeft(request, actor, "Hold lost and no alternatives remain");
		}
	}

	private static boolean contains(ClinicOpeningHour opening, ZonedDateTime start, ZonedDateTime end) {
		return !start.toLocalTime().isBefore(opening.getOpenTime())
				&& !end.toLocalTime().isAfter(opening.getCloseTime());
	}

	private static boolean contains(VetWeeklyBlock block, ZonedDateTime start, ZonedDateTime end) {
		return !start.toLocalTime().isBefore(block.getStartTime()) && !end.toLocalTime().isAfter(block.getEndTime());
	}

}
