/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 */

package org.springframework.samples.petclinic.scheduling.request;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.vet.Vet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** Exhaustive negative matrix for every request action in every lifecycle state. */
class RequestLifecycleMatrixTests {

	private static final ZoneId ZONE = ZoneId.of("Europe/Amsterdam");

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-07T07:00:00Z"), ZONE);

	private static final ZonedDateTime SLOT = ZonedDateTime.now(CLOCK).plusDays(1);

	private static final Map<RequestState, Set<Action>> ALLOWED = allowedActions();

	@Test
	@org.junit.jupiter.api.DisplayName("AC-123: every disallowed action refuses before mutation or event persistence")
	void everyDisallowedActionRefusesBeforeMutationOrEventPersistence() {
		for (RequestState state : RequestState.values()) {
			for (Action action : Action.values()) {
				if (ALLOWED.get(state).contains(action)) {
					continue;
				}
				SchedulingRequestRepository requests = mock(SchedulingRequestRepository.class);
				SchedulingRequestEventRepository events = mock(SchedulingRequestEventRepository.class);
				RequestLifecycleService service = new RequestLifecycleService(requests, events, CLOCK);
				SchedulingRequest request = requestIn(state);
				RequestSnapshot before = RequestSnapshot.of(request);

				assertThatThrownBy(() -> action.invoke(service, request)).as("%s must be refused in %s", action, state)
					.isExactlyInstanceOf(IllegalRequestTransitionException.class);
				assertThat(RequestSnapshot.of(request)).isEqualTo(before);
				verifyNoInteractions(requests, events);
			}
		}
	}

	private static SchedulingRequest requestIn(RequestState state) {
		Vet vet = new Vet();
		vet.setId(1);
		SchedulingRequest request = new SchedulingRequest();
		request.setState(state);
		request.setReasonText("reason");
		request.setAvailabilityText("availability");
		request.setActivePetId(state.isActive() ? 1 : null);
		request.setCreatedAt(ZonedDateTime.now(CLOCK).minusDays(1));
		request.setUpdatedAt(ZonedDateTime.now(CLOCK).minusHours(1));
		request.setHold(vet, SLOT, 30);
		return request;
	}

	private static Map<RequestState, Set<Action>> allowedActions() {
		Map<RequestState, Set<Action>> allowed = new EnumMap<>(RequestState.class);
		allowed.put(RequestState.AWAITING_CONSENT, EnumSet.of(Action.CONSENT, Action.DECLINE, Action.EDIT,
				Action.ABANDON, Action.STAFF_ATTACH, Action.STAFF_LEAVE));
		allowed.put(RequestState.INTERPRETING, EnumSet.of(Action.ABANDON, Action.USABLE, Action.FAILED,
				Action.MODEL_UNAVAILABLE, Action.RESTART, Action.STAFF_ATTACH, Action.STAFF_LEAVE));
		allowed.put(RequestState.INTERPRETATION_FAILED,
				EnumSet.of(Action.EDIT, Action.ROUTE, Action.ABANDON, Action.STAFF_ATTACH, Action.STAFF_LEAVE));
		allowed.put(RequestState.INTERPRETED, EnumSet.of(Action.CONFIRM_FEASIBLE, Action.CONFIRM_NO_SLOTS, Action.EDIT,
				Action.ROUTE, Action.ABANDON, Action.STAFF_ATTACH, Action.STAFF_LEAVE));
		allowed.put(RequestState.SUGGESTION_OFFERED,
				EnumSet.of(Action.ACCEPT, Action.ACCEPT_NEXT, Action.ACCEPT_NONE, Action.ASK_NEXT, Action.ASK_NONE,
						Action.VIEW_NEXT, Action.VIEW_NONE, Action.ROUTE, Action.EDIT, Action.ABANDON, Action.RELEASE,
						Action.STAFF_SUGGEST, Action.STAFF_ATTACH, Action.STAFF_LEAVE));
		allowed.put(RequestState.WITH_STAFF, EnumSet.of(Action.VIEW_STATUS, Action.ABANDON, Action.STAFF_EDIT,
				Action.STAFF_SUGGEST, Action.STAFF_ATTACH, Action.STAFF_LEAVE));
		allowed.put(RequestState.ACCEPTED, EnumSet.noneOf(Action.class));
		allowed.put(RequestState.ABANDONED, EnumSet.noneOf(Action.class));
		return allowed;
	}

	private enum Action {

		CONSENT((s, r) -> s.consent(r, "owner")), DECLINE((s, r) -> s.declineConsent(r, "owner")),
		EDIT((s, r) -> s.editText(r, "owner", "new reason", "new availability")),
		ABANDON((s, r) -> s.abandon(r, "owner", "reason")), USABLE((s, r) -> s.interpretationUsable(r, "system")),
		FAILED((s, r) -> s.interpretationFailed(r, "system", "failed")),
		MODEL_UNAVAILABLE((s, r) -> s.interpretationModelUnavailable(r, "system", "unavailable")),
		RESTART((s, r) -> s.systemRestartInterrupted(r, "system")),
		CONFIRM_FEASIBLE((s, r) -> s.confirmFeasible(r, "owner", r.getHeldVet(), SLOT, 30)),
		CONFIRM_NO_SLOTS((s, r) -> s.confirmNoFeasibleSlots(r, "owner", "none")),
		ACCEPT((s, r) -> s.acceptSuggestion(r, "owner")),
		ACCEPT_NEXT((s, r) -> s.acceptSlotLostNextExists(r, "owner", r.getHeldVet(), SLOT, 30)),
		ACCEPT_NONE((s, r) -> s.acceptSlotLostNoneLeft(r, "owner", "none")),
		ASK_NEXT((s, r) -> s.askForAnotherOptionSlotsRemain(r, "owner", r.getHeldVet(), SLOT, 30)),
		ASK_NONE((s, r) -> s.askForAnotherOptionExhausted(r, "owner", "none")),
		VIEW_NEXT((s, r) -> s.viewRevalidateNextExists(r, "system", r.getHeldVet(), SLOT, 30)),
		VIEW_NONE((s, r) -> s.viewRevalidateNoneLeft(r, "system", "none")),
		ROUTE((s, r) -> s.routeToStaff(r, "owner", "help")),
		RELEASE((s, r) -> s.staffReleaseHold(r, "staff", "release")),
		STAFF_SUGGEST((s, r) -> s.staffPlaceSuggestion(r, "staff", r.getHeldVet(), SLOT, 30)),
		STAFF_EDIT((s, r) -> s.staffEditInterpretation(r, "staff")),
		STAFF_ATTACH((s, r) -> s.staffBookAttach(r, "staff", "book")),
		STAFF_LEAVE((s, r) -> s.staffBookLeaveOpen(r, "staff", "book")),
		VIEW_STATUS((s, r) -> s.viewStatus(r, "owner"));

		private final Invocation invocation;

		Action(Invocation invocation) {
			this.invocation = invocation;
		}

		void invoke(RequestLifecycleService service, SchedulingRequest request) {
			this.invocation.invoke(service, request);
		}

	}

	@FunctionalInterface
	private interface Invocation {

		void invoke(RequestLifecycleService service, SchedulingRequest request);

	}

	private record RequestSnapshot(RequestState state, String reason, String availability, Integer activePet,
			Integer vetId, ZonedDateTime heldStart, Integer heldDuration, int failedAttempts, ZonedDateTime updatedAt) {

		static RequestSnapshot of(SchedulingRequest request) {
			return new RequestSnapshot(request.getState(), request.getReasonText(), request.getAvailabilityText(),
					request.getActivePetId(), request.getHeldVet() == null ? null : request.getHeldVet().getId(),
					request.getHeldStart(), request.getHeldDuration(), request.getFailedAttempts(),
					request.getUpdatedAt());
		}
	}

}
