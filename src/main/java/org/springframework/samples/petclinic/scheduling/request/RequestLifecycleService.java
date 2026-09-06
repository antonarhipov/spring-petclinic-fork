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
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service enforcing the request lifecycle state machine (RULE-15, AC-123).
 */
@Service
@Transactional
public class RequestLifecycleService {

	private static final Logger logger = LoggerFactory.getLogger(RequestLifecycleService.class);

	private final SchedulingRequestRepository requestRepository;

	private final SchedulingRequestEventRepository eventRepository;

	private final Clock clock;

	public RequestLifecycleService(SchedulingRequestRepository requestRepository,
			SchedulingRequestEventRepository eventRepository, Clock clock) {
		this.requestRepository = requestRepository;
		this.eventRepository = eventRepository;
		this.clock = clock;
	}

	public SchedulingRequest createRequest(Owner owner, Pet pet, String reasonText, String availabilityText,
			String actor) {
		Objects.requireNonNull(owner, "owner must not be null");
		Objects.requireNonNull(pet, "pet must not be null");

		if (this.requestRepository.findByActivePetId(pet.getId()).isPresent()) {
			throw new ActiveRequestExistsException();
		}

		ZonedDateTime now = ZonedDateTime.now(this.clock);
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(owner);
		request.setPet(pet);
		request.setState(RequestState.AWAITING_CONSENT);
		request.setReasonText(reasonText);
		request.setAvailabilityText(availabilityText);
		request.setActivePetId(pet.getId());
		request.setFailedAttempts(0);
		request.setCreatedAt(now);
		request.setUpdatedAt(now);

		SchedulingRequest saved;
		try {
			saved = this.requestRepository.saveAndFlush(request);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ActiveRequestExistsException();
		}

		SchedulingRequestEvent event = new SchedulingRequestEvent();
		event.setRequest(saved);
		event.setFromState(null);
		event.setToState(RequestState.AWAITING_CONSENT);
		event.setActor(actor);
		event.setAction("CREATE_REQUEST");
		event.setTimestamp(now);
		this.eventRepository.save(event);

		return saved;
	}

	public SchedulingRequest consent(SchedulingRequest request, String actor) {
		validateState(request, Set.of(RequestState.AWAITING_CONSENT), "consent");
		return applyTransition(request, RequestState.INTERPRETING, actor, "CONSENT_GRANTED", null, null);
	}

	public SchedulingRequest declineConsent(SchedulingRequest request, String actor) {
		validateState(request, Set.of(RequestState.AWAITING_CONSENT), "decline consent");
		return applyTransition(request, RequestState.WITH_STAFF, actor, "decline consent", null, null);
	}

	public SchedulingRequest editText(SchedulingRequest request, String actor, String newReasonText,
			String newAvailabilityText) {
		validateState(request, Set.of(RequestState.AWAITING_CONSENT, RequestState.INTERPRETATION_FAILED,
				RequestState.INTERPRETED, RequestState.SUGGESTION_OFFERED), "edit text");
		request.clearHold();
		if (newReasonText != null) {
			request.setReasonText(newReasonText);
		}
		if (newAvailabilityText != null) {
			request.setAvailabilityText(newAvailabilityText);
		}
		return applyTransition(request, RequestState.AWAITING_CONSENT, actor, "edit text", null, null);
	}

	public SchedulingRequest abandon(SchedulingRequest request, String actor, String reason) {
		validateState(request,
				Set.of(RequestState.AWAITING_CONSENT, RequestState.INTERPRETING, RequestState.INTERPRETATION_FAILED,
						RequestState.INTERPRETED, RequestState.SUGGESTION_OFFERED, RequestState.WITH_STAFF),
				"abandon");
		return applyTransition(request, RequestState.ABANDONED, actor, "abandon", reason, null);
	}

	public SchedulingRequest interpretationUsable(SchedulingRequest request, String actor) {
		validateState(request, Set.of(RequestState.INTERPRETING), "interpretation usable");
		return applyTransition(request, RequestState.INTERPRETED, actor, "INTERPRETATION_APPLIED", null, null);
	}

	public SchedulingRequest interpretationFailed(SchedulingRequest request, String actor, String reason) {
		validateState(request, Set.of(RequestState.INTERPRETING), "interpretation failed");
		request.setFailedAttempts(request.getFailedAttempts() + 1);
		return applyTransition(request, RequestState.INTERPRETATION_FAILED, actor, "interpretation failed", reason,
				null);
	}

	public SchedulingRequest interpretationModelUnavailable(SchedulingRequest request, String actor, String reason) {
		validateState(request, Set.of(RequestState.INTERPRETING), "model unavailable");
		return applyTransition(request, RequestState.WITH_STAFF, actor, "model unavailable", reason, null);
	}

	public SchedulingRequest interpretationUnmatchedSpecialty(SchedulingRequest request, String actor,
			String specialty) {
		validateState(request, Set.of(RequestState.INTERPRETING), "unmatched specialty");
		return applyTransition(request, RequestState.WITH_STAFF, actor, "unmatched specialty", specialty, null);
	}

	public SchedulingRequest systemRestartInterrupted(SchedulingRequest request, String actor) {
		validateState(request, Set.of(RequestState.INTERPRETING), "system restart");
		return applyTransition(request, RequestState.INTERPRETATION_FAILED, actor, "system restart", "interrupted",
				null);
	}

	public SchedulingRequest confirmFeasible(SchedulingRequest request, String actor, Vet heldVet,
			ZonedDateTime heldStart, int heldDuration) {
		validateState(request, Set.of(RequestState.INTERPRETED), "confirm feasible");
		request.setHold(heldVet, heldStart, heldDuration);
		return applyTransition(request, RequestState.SUGGESTION_OFFERED, actor, "confirm feasible", null, null);
	}

	public SchedulingRequest confirmNoFeasibleSlots(SchedulingRequest request, String actor, String reason) {
		validateState(request, Set.of(RequestState.INTERPRETED), "confirm no feasible slots");
		return applyTransition(request, RequestState.WITH_STAFF, actor, "confirm no feasible slots", reason, null);
	}

	public SchedulingRequest acceptSuggestion(SchedulingRequest request, String actor) {
		validateState(request, Set.of(RequestState.SUGGESTION_OFFERED), "accept");
		return applyTransition(request, RequestState.ACCEPTED, actor, "accept", null, null);
	}

	public SchedulingRequest acceptSlotLostNextExists(SchedulingRequest request, String actor, Vet nextVet,
			ZonedDateTime nextStart, int nextDuration) {
		validateState(request, Set.of(RequestState.SUGGESTION_OFFERED), "accept — slot lost next exists");
		request.setHold(nextVet, nextStart, nextDuration);
		return applyTransition(request, RequestState.SUGGESTION_OFFERED, actor, "accept — slot lost next exists", null,
				null);
	}

	public SchedulingRequest acceptSlotLostNoneLeft(SchedulingRequest request, String actor, String reason) {
		validateState(request, Set.of(RequestState.SUGGESTION_OFFERED), "accept — slot lost none left");
		request.clearHold();
		return applyTransition(request, RequestState.WITH_STAFF, actor, "accept — slot lost none left", reason, null);
	}

	public SchedulingRequest askForAnotherOptionSlotsRemain(SchedulingRequest request, String actor, Vet nextVet,
			ZonedDateTime nextStart, int nextDuration) {
		validateState(request, Set.of(RequestState.SUGGESTION_OFFERED), "ask for another option — slots remain");
		request.setHold(nextVet, nextStart, nextDuration);
		return applyTransition(request, RequestState.SUGGESTION_OFFERED, actor, "ask for another option — slots remain",
				null, null);
	}

	public SchedulingRequest askForAnotherOptionExhausted(SchedulingRequest request, String actor, String reason) {
		validateState(request, Set.of(RequestState.SUGGESTION_OFFERED), "ask for another option — exhausted");
		request.clearHold();
		return applyTransition(request, RequestState.WITH_STAFF, actor, "ask for another option — exhausted", reason,
				null);
	}

	public SchedulingRequest viewRevalidateNextExists(SchedulingRequest request, String actor, Vet nextVet,
			ZonedDateTime nextStart, int nextDuration) {
		validateState(request, Set.of(RequestState.SUGGESTION_OFFERED), "view re-validates — next exists");
		request.setHold(nextVet, nextStart, nextDuration);
		return applyTransition(request, RequestState.SUGGESTION_OFFERED, actor, "view re-validates — next exists", null,
				null);
	}

	public SchedulingRequest viewRevalidateNoneLeft(SchedulingRequest request, String actor, String reason) {
		validateState(request, Set.of(RequestState.SUGGESTION_OFFERED), "view re-validates — none left");
		request.clearHold();
		return applyTransition(request, RequestState.WITH_STAFF, actor, "view re-validates — none left", reason, null);
	}

	public SchedulingRequest routeToStaff(SchedulingRequest request, String actor, String reason) {
		validateState(request,
				Set.of(RequestState.INTERPRETATION_FAILED, RequestState.INTERPRETED, RequestState.SUGGESTION_OFFERED),
				"route to staff");
		request.clearHold();
		return applyTransition(request, RequestState.WITH_STAFF, actor, "route to staff", reason, null);
	}

	public SchedulingRequest staffReleaseHold(SchedulingRequest request, String actor, String reason) {
		validateState(request, Set.of(RequestState.SUGGESTION_OFFERED), "staff release hold");
		request.clearHold();
		return applyTransition(request, RequestState.WITH_STAFF, actor, "staff release hold", reason, null);
	}

	public SchedulingRequest staffPlaceSuggestion(SchedulingRequest request, String actor, Vet vet, ZonedDateTime start,
			int duration) {
		validateState(request, Set.of(RequestState.SUGGESTION_OFFERED, RequestState.WITH_STAFF),
				"staff place suggestion");
		request.setHold(vet, start, duration);
		return applyTransition(request, RequestState.SUGGESTION_OFFERED, actor, "staff place suggestion", null, null);
	}

	public SchedulingRequest staffEditInterpretation(SchedulingRequest request, String actor) {
		validateState(request, Set.of(RequestState.WITH_STAFF), "staff edit interpretation");
		return applyTransition(request, RequestState.WITH_STAFF, actor, "staff edit interpretation", null, null);
	}

	public SchedulingRequest staffBookAttach(SchedulingRequest request, String actor, String reason) {
		validateState(request,
				Set.of(RequestState.AWAITING_CONSENT, RequestState.INTERPRETING, RequestState.INTERPRETATION_FAILED,
						RequestState.INTERPRETED, RequestState.SUGGESTION_OFFERED, RequestState.WITH_STAFF),
				"staff book attach");
		return applyTransition(request, RequestState.ACCEPTED, actor, "staff book attach", reason, null);
	}

	public SchedulingRequest staffBookLeaveOpen(SchedulingRequest request, String actor, String reason) {
		validateState(request,
				Set.of(RequestState.AWAITING_CONSENT, RequestState.INTERPRETING, RequestState.INTERPRETATION_FAILED,
						RequestState.INTERPRETED, RequestState.SUGGESTION_OFFERED, RequestState.WITH_STAFF),
				"staff book leave open");
		return applyTransition(request, request.getState(), actor, "staff book leave open", reason, null);
	}

	public SchedulingRequest viewStatus(SchedulingRequest request, String actor) {
		validateState(request, Set.of(RequestState.WITH_STAFF), "view status");
		return applyTransition(request, RequestState.WITH_STAFF, actor, "view status", null, null);
	}

	private void validateState(SchedulingRequest request, Set<RequestState> allowedStates, String action) {
		if (request == null || !allowedStates.contains(request.getState())) {
			RequestState state = request != null ? request.getState() : null;
			throw new IllegalRequestTransitionException(state, action);
		}
	}

	private SchedulingRequest applyTransition(SchedulingRequest request, RequestState targetState, String actor,
			String action, String reason, String payload) {
		RequestState fromState = request.getState();
		ZonedDateTime now = ZonedDateTime.now(this.clock);
		logger.info("Scheduling request transition requestId={} actor={} action={} state={} -> {} reason={} payload={}",
				request.getId(), actor, action, fromState, targetState, reason, payload);

		request.setState(targetState);
		request.setUpdatedAt(now);
		if (targetState.isTerminal()) {
			request.setActivePetId(null);
			request.clearHold();
		}

		SchedulingRequest saved = this.requestRepository.save(request);

		SchedulingRequestEvent event = new SchedulingRequestEvent();
		event.setRequest(saved);
		event.setFromState(fromState);
		event.setToState(targetState);
		event.setActor(actor);
		event.setAction(action);
		event.setReason(reason);
		event.setPayload(payload);
		event.setTimestamp(now);
		this.eventRepository.save(event);

		return saved;
	}

}
