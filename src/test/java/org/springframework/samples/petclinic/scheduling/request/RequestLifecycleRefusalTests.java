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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.TestClockConfig;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Refusal and transition tests for RequestLifecycleService (RULE-15, AC-123, AC-136).
 */
@SpringBootTest
@Import(TestClockConfig.class)
@Transactional
class RequestLifecycleRefusalTests {

	@Autowired
	private RequestLifecycleService lifecycleService;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private SchedulingRequestEventRepository eventRepository;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private VetRepository vetRepository;

	@Autowired
	private Clock clock;

	private Owner testOwner;

	private Pet testPet;

	private Vet testVet;

	@BeforeEach
	void setUp() {
		this.testOwner = this.ownerRepository.findById(1).orElseThrow();
		this.testPet = this.testOwner.getPet(1);
		this.testVet = this.vetRepository.findAll().iterator().next();
	}

	private SchedulingRequest createPersistedRequest(RequestState state) {
		ZonedDateTime now = ZonedDateTime.now(this.clock);
		SchedulingRequest req = new SchedulingRequest();
		req.setOwner(this.testOwner);
		req.setPet(this.testPet);
		req.setState(state);
		req.setReasonText("General checkup");
		req.setAvailabilityText("Monday mornings");
		if (state.isActive()) {
			req.setActivePetId(this.testPet.getId());
		}
		req.setCreatedAt(now.minusHours(1));
		req.setUpdatedAt(now.minusHours(1));
		if (state == RequestState.SUGGESTION_OFFERED) {
			req.setHold(this.testVet, now.plusDays(1), 30);
		}
		return this.requestRepository.saveAndFlush(req);
	}

	@Test
	void awaitingConsentRefusesInvalidTransitions() {
		SchedulingRequest request = createPersistedRequest(RequestState.AWAITING_CONSENT);
		int initialEventsCount = this.eventRepository.findByRequestIdOrderByTimestampAsc(request.getId()).size();

		assertThatThrownBy(() -> this.lifecycleService.interpretationUsable(request, "system"))
			.isInstanceOf(IllegalRequestTransitionException.class);
		assertThatThrownBy(() -> this.lifecycleService.interpretationFailed(request, "system", "failed"))
			.isInstanceOf(IllegalRequestTransitionException.class);
		assertThatThrownBy(() -> this.lifecycleService.confirmFeasible(request, "system", this.testVet,
				ZonedDateTime.now(this.clock).plusDays(1), 30))
			.isInstanceOf(IllegalRequestTransitionException.class);
		assertThatThrownBy(() -> this.lifecycleService.acceptSuggestion(request, "owner"))
			.isInstanceOf(IllegalRequestTransitionException.class);
		assertThatThrownBy(() -> this.lifecycleService.routeToStaff(request, "owner", "help"))
			.isInstanceOf(IllegalRequestTransitionException.class);
		assertThatThrownBy(() -> this.lifecycleService.staffReleaseHold(request, "staff", "release"))
			.isInstanceOf(IllegalRequestTransitionException.class);

		SchedulingRequest current = this.requestRepository.findById(request.getId()).orElseThrow();
		assertThat(current.getState()).isEqualTo(RequestState.AWAITING_CONSENT);
		List<SchedulingRequestEvent> events = this.eventRepository.findByRequestIdOrderByTimestampAsc(request.getId());
		assertThat(events).hasSize(initialEventsCount);
	}

	@Test
	void interpretingRefusesInvalidTransitions() {
		SchedulingRequest request = createPersistedRequest(RequestState.INTERPRETING);
		int initialEventsCount = this.eventRepository.findByRequestIdOrderByTimestampAsc(request.getId()).size();

		assertThatThrownBy(() -> this.lifecycleService.consent(request, "owner"))
			.isInstanceOf(IllegalRequestTransitionException.class);
		assertThatThrownBy(() -> this.lifecycleService.declineConsent(request, "owner"))
			.isInstanceOf(IllegalRequestTransitionException.class);
		assertThatThrownBy(() -> this.lifecycleService.editText(request, "owner", "new reason", "new avail"))
			.isInstanceOf(IllegalRequestTransitionException.class);
		assertThatThrownBy(() -> this.lifecycleService.confirmFeasible(request, "system", this.testVet,
				ZonedDateTime.now(this.clock).plusDays(1), 30))
			.isInstanceOf(IllegalRequestTransitionException.class);
		assertThatThrownBy(() -> this.lifecycleService.acceptSuggestion(request, "owner"))
			.isInstanceOf(IllegalRequestTransitionException.class);
		assertThatThrownBy(() -> this.lifecycleService.routeToStaff(request, "owner", "help"))
			.isInstanceOf(IllegalRequestTransitionException.class);

		SchedulingRequest current = this.requestRepository.findById(request.getId()).orElseThrow();
		assertThat(current.getState()).isEqualTo(RequestState.INTERPRETING);
		List<SchedulingRequestEvent> events = this.eventRepository.findByRequestIdOrderByTimestampAsc(request.getId());
		assertThat(events).hasSize(initialEventsCount);
	}

	@Test
	void terminalStatesRefuseAllTransitions() {
		for (RequestState terminalState : List.of(RequestState.ACCEPTED, RequestState.ABANDONED)) {
			SchedulingRequest request = createPersistedRequest(terminalState);
			int initialEventsCount = this.eventRepository.findByRequestIdOrderByTimestampAsc(request.getId()).size();

			assertThatThrownBy(() -> this.lifecycleService.consent(request, "owner"))
				.isInstanceOf(IllegalRequestTransitionException.class);
			assertThatThrownBy(() -> this.lifecycleService.declineConsent(request, "owner"))
				.isInstanceOf(IllegalRequestTransitionException.class);
			assertThatThrownBy(() -> this.lifecycleService.editText(request, "owner", "a", "b"))
				.isInstanceOf(IllegalRequestTransitionException.class);
			assertThatThrownBy(() -> this.lifecycleService.abandon(request, "owner", "cancel"))
				.isInstanceOf(IllegalRequestTransitionException.class);
			assertThatThrownBy(() -> this.lifecycleService.interpretationUsable(request, "system"))
				.isInstanceOf(IllegalRequestTransitionException.class);
			assertThatThrownBy(() -> this.lifecycleService.acceptSuggestion(request, "owner"))
				.isInstanceOf(IllegalRequestTransitionException.class);
			assertThatThrownBy(() -> this.lifecycleService.staffBookAttach(request, "staff", "manual"))
				.isInstanceOf(IllegalRequestTransitionException.class);

			SchedulingRequest current = this.requestRepository.findById(request.getId()).orElseThrow();
			assertThat(current.getState()).isEqualTo(terminalState);
			List<SchedulingRequestEvent> events = this.eventRepository
				.findByRequestIdOrderByTimestampAsc(request.getId());
			assertThat(events).hasSize(initialEventsCount);
		}
	}

	@Test
	void validTransitionsSucceedAndRecordEvents() {
		SchedulingRequest request = createPersistedRequest(RequestState.AWAITING_CONSENT);

		SchedulingRequest interpreting = this.lifecycleService.consent(request, "owner_1");
		assertThat(interpreting.getState()).isEqualTo(RequestState.INTERPRETING);

		SchedulingRequest interpreted = this.lifecycleService.interpretationUsable(interpreting, "interpreter_ai");
		assertThat(interpreted.getState()).isEqualTo(RequestState.INTERPRETED);

		ZonedDateTime slotStart = ZonedDateTime.now(this.clock).plusDays(2);
		SchedulingRequest offered = this.lifecycleService.confirmFeasible(interpreted, "solver", this.testVet,
				slotStart, 30);
		assertThat(offered.getState()).isEqualTo(RequestState.SUGGESTION_OFFERED);
		assertThat(offered.getHeldVet()).isEqualTo(this.testVet);
		assertThat(offered.getHeldStart()).isEqualTo(slotStart);

		SchedulingRequest accepted = this.lifecycleService.acceptSuggestion(offered, "owner_1");
		assertThat(accepted.getState()).isEqualTo(RequestState.ACCEPTED);
		assertThat(accepted.getActivePetId()).isNull();
		assertThat(accepted.getHeldVet()).isNull();

		List<SchedulingRequestEvent> events = this.eventRepository.findByRequestIdOrderByTimestampAsc(request.getId());
		assertThat(events).hasSize(4);
		assertThat(events.get(0).getAction()).isEqualTo("consent");
		assertThat(events.get(1).getAction()).isEqualTo("interpretation usable");
		assertThat(events.get(2).getAction()).isEqualTo("confirm feasible");
		assertThat(events.get(3).getAction()).isEqualTo("accept");
	}

}
