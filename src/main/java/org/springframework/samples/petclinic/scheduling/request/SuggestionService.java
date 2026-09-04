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
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
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

	private final SchedulingRequestRepository requestRepository;

	private final RequestLifecycleService requestLifecycleService;

	private final InterpretationRepository interpretationRepository;

	private final SlotRanker slotRanker;

	private final AppointmentLifecycleService appointmentLifecycleService;

	private final AppointmentRepository appointmentRepository;

	private final VetRepository vetRepository;

	private final Clock clock;

	public SuggestionService(SchedulingRequestRepository requestRepository,
			RequestLifecycleService requestLifecycleService, InterpretationRepository interpretationRepository,
			SlotRanker slotRanker, AppointmentLifecycleService appointmentLifecycleService,
			AppointmentRepository appointmentRepository, VetRepository vetRepository, Clock clock) {
		this.requestRepository = requestRepository;
		this.requestLifecycleService = requestLifecycleService;
		this.interpretationRepository = interpretationRepository;
		this.slotRanker = slotRanker;
		this.appointmentLifecycleService = appointmentLifecycleService;
		this.appointmentRepository = appointmentRepository;
		this.vetRepository = vetRepository;
		this.clock = clock;
	}

	public SchedulingRequest confirm(SchedulingRequest request, String actor) {
		Objects.requireNonNull(request, "request must not be null");

		Interpretation interpretation = this.interpretationRepository
			.findTopByRequestIdOrderByVersionDesc(request.getId())
			.orElse(null);

		List<SlotRanker.RankedSlot> rankedSlots = this.slotRanker.rankSlots(request, interpretation);

		if (rankedSlots.isEmpty()) {
			return this.requestLifecycleService.confirmNoFeasibleSlots(request, actor, "No feasible slots available");
		}

		for (SlotRanker.RankedSlot candidate : rankedSlots) {
			Vet vet = candidate.vet();
			if (vet != null && vet.getId() != null) {
				// Acquire pessimistic write lock on the vet row (RULE-10)
				Vet lockedVet = this.vetRepository.findByIdForUpdate(vet.getId()).orElseThrow();

				// Verify slot availability against confirmed appointments and active
				// holds
				if (isSlotAvailable(vet.getId(), candidate.startTime(), candidate.duration(), request.getId())) {
					return this.requestLifecycleService.confirmFeasible(request, actor, lockedVet,
							candidate.startTime(), candidate.duration());
				}
			}
		}

		return this.requestLifecycleService.confirmNoFeasibleSlots(request, actor, "All candidate slots conflicted");
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

		// Re-validate hold under lock (RULE-11)
		if (!isSlotAvailable(heldVet.getId(), heldStart, heldDuration, request.getId())) {
			Interpretation interpretation = this.interpretationRepository
				.findTopByRequestIdOrderByVersionDesc(request.getId())
				.orElse(null);
			List<SlotRanker.RankedSlot> nextSlots = this.slotRanker.rankSlots(request, interpretation);

			SlotRanker.RankedSlot availableNext = null;
			for (SlotRanker.RankedSlot candidate : nextSlots) {
				if (candidate.vet() != null && isSlotAvailable(candidate.vet().getId(), candidate.startTime(),
						candidate.duration(), request.getId())) {
					availableNext = candidate;
					break;
				}
			}

			if (availableNext != null) {
				this.requestLifecycleService.acceptSlotLostNextExists(request, actor, availableNext.vet(),
						availableNext.startTime(), availableNext.duration());
				throw new IllegalStateException("Hold expired/conflicted; next alternative slot offered");
			}
			else {
				this.requestLifecycleService.acceptSlotLostNoneLeft(request, actor,
						"Hold lost and no alternatives remain");
				throw new IllegalStateException("Hold expired/conflicted; request routed to staff");
			}
		}

		// Hold is valid: book appointment and mark request accepted
		Appointment appointment = this.appointmentLifecycleService.bookAppointment(request.getPet(), heldVet, heldStart,
				heldDuration, request.getReasonText(), request, actor);

		this.requestLifecycleService.acceptSuggestion(request, actor);
		return appointment;
	}

	private boolean isSlotAvailable(Integer vetId, ZonedDateTime start, int duration, Integer currentRequestId) {
		ZonedDateTime end = start.plusMinutes(duration);

		List<Appointment> conflicts = this.appointmentRepository.findConfirmedByVetIdAndDateRange(vetId,
				start.minusMinutes(120), end.plusMinutes(120));
		for (Appointment app : conflicts) {
			if (app.getStartTime().isBefore(end) && app.getEndTime().isAfter(start)) {
				return false;
			}
		}

		List<SchedulingRequest> activeHolds = this.requestRepository.findActiveHoldsByVetId(vetId);
		for (SchedulingRequest hold : activeHolds) {
			if (!Objects.equals(hold.getId(), currentRequestId) && hold.getHeldStart() != null
					&& hold.getHeldDuration() != null) {
				ZonedDateTime holdEnd = hold.getHeldStart().plusMinutes(hold.getHeldDuration());
				if (hold.getHeldStart().isBefore(end) && holdEnd.isAfter(start)) {
					return false;
				}
			}
		}

		return true;
	}

}
