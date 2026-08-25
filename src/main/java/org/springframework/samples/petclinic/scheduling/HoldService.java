/*
 * Copyright 2012-2025 the original author or authors.
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

package org.springframework.samples.petclinic.scheduling;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.samples.petclinic.model.NamedEntity;
import org.springframework.samples.petclinic.scheduling.model.Hold;
import org.springframework.samples.petclinic.scheduling.model.HoldRepository;
import org.springframework.samples.petclinic.scheduling.model.HoldStatus;
import org.springframework.samples.petclinic.scheduling.model.OccupancyType;
import org.springframework.samples.petclinic.scheduling.model.RequestExclusion;
import org.springframework.samples.petclinic.scheduling.model.RequestExclusionRepository;
import org.springframework.samples.petclinic.scheduling.model.RequestState;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.model.SlotOccupancy;
import org.springframework.samples.petclinic.scheduling.model.SlotOccupancyRepository;
import org.springframework.samples.petclinic.scheduling.model.SuggestionView;
import org.springframework.samples.petclinic.vet.Specialty;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HoldService {

	private static final Logger log = LoggerFactory.getLogger(HoldService.class);

	public static final Duration DEFAULT_HOLD_DURATION = Duration.ofMinutes(10);

	private final HoldRepository holds;

	private final SlotOccupancyRepository slotOccupancies;

	private final SchedulingRequestRepository schedulingRequests;

	private final RequestExclusionRepository requestExclusions;

	private final ApplicationEventPublisher eventPublisher;

	public HoldService(HoldRepository holds, SlotOccupancyRepository slotOccupancies,
			SchedulingRequestRepository schedulingRequests, RequestExclusionRepository requestExclusions,
			ApplicationEventPublisher eventPublisher) {
		this.holds = holds;
		this.slotOccupancies = slotOccupancies;
		this.schedulingRequests = schedulingRequests;
		this.requestExclusions = requestExclusions;
		this.eventPublisher = eventPublisher;
	}

	@Scheduled(fixedDelayString = "${petclinic.scheduling.hold-sweep-delay-ms:30000}")
	@Transactional
	public void sweepExpiredHolds() {
		Instant now = Instant.now();
		List<Hold> expiredHolds = this.holds.findByStatusAndExpiresAtBefore(HoldStatus.ACTIVE, now);
		for (Hold hold : expiredHolds) {
			expireHold(hold);
		}
	}

	@Transactional
	public void recordExclusion(SchedulingRequest request, Vet vet, LocalDateTime startTime) {
		RequestExclusion exclusion = new RequestExclusion(request, vet, startTime);
		this.requestExclusions.saveAndFlush(exclusion);
	}

	@Transactional
	public Hold placeHold(SchedulingRequest request, Vet vet, LocalDateTime startTime, LocalDateTime endTime,
			Duration holdDuration) {
		Duration duration = (holdDuration != null) ? holdDuration : DEFAULT_HOLD_DURATION;
		Instant expiresAt = Instant.now().plus(duration);

		Hold hold = new Hold();
		hold.setSchedulingRequest(request);
		hold.setVet(vet);
		hold.setStartTime(startTime);
		hold.setEndTime(endTime);
		hold.setExpiresAt(expiresAt);
		hold.setStatus(HoldStatus.ACTIVE);
		Hold savedHold = this.holds.saveAndFlush(hold);

		SlotOccupancy occupancy = new SlotOccupancy();
		occupancy.setVet(vet);
		occupancy.setStartTime(startTime);
		occupancy.setEndTime(endTime);
		occupancy.setOccupancyType(OccupancyType.HOLD);
		occupancy.setReferenceId(savedHold.getId());
		this.slotOccupancies.saveAndFlush(occupancy);

		request.transitionTo(RequestState.SLOT_HELD, null);
		this.schedulingRequests.saveAndFlush(request);

		return savedHold;
	}

	@Transactional
	public Optional<Hold> getActiveHold(Integer requestId) {
		Optional<Hold> activeHold = this.holds.findBySchedulingRequestIdAndStatus(requestId, HoldStatus.ACTIVE);
		if (activeHold.isEmpty()) {
			return Optional.empty();
		}

		Hold hold = activeHold.get();
		if (hold.isExpired(Instant.now())) {
			expireHold(hold);
			return Optional.empty();
		}

		return Optional.of(hold);
	}

	@Transactional
	public void expireHold(Hold hold) {
		hold.setStatus(HoldStatus.EXPIRED);
		this.holds.saveAndFlush(hold);
		this.slotOccupancies.deleteByVetIdAndStartTime(hold.getVet().getId(), hold.getStartTime());

		SchedulingRequest req = hold.getSchedulingRequest();
		if (req != null) {
			SchedulingRequest fresh = this.schedulingRequests.findById(req.getId()).orElse(null);
			if (fresh != null && fresh.getState() == RequestState.SLOT_HELD) {
				fresh.transitionTo(RequestState.SUGGESTING, null);
				this.schedulingRequests.saveAndFlush(fresh);
				if (this.eventPublisher != null) {
					this.eventPublisher.publishEvent(new HoldExpiredEvent(fresh.getId()));
				}
			}
		}
	}

	@Transactional
	public void releaseHold(Hold hold) {
		hold.setStatus(HoldStatus.RELEASED);
		this.holds.saveAndFlush(hold);
		this.slotOccupancies.deleteByVetIdAndStartTime(hold.getVet().getId(), hold.getStartTime());
	}

	public SuggestionView toSuggestionView(Hold hold) {
		if (hold == null) {
			return null;
		}

		Vet vet = hold.getVet();
		String vetName = (vet != null) ? (vet.getFirstName() + " " + vet.getLastName()) : "Assigned Veterinarian";
		String specialty = "General Care";
		if (vet != null && !vet.getSpecialties().isEmpty()) {
			specialty = vet.getSpecialties().stream().map(NamedEntity::getName).collect(Collectors.joining(", "));
		}

		int durationMinutes = (int) Duration.between(hold.getStartTime(), hold.getEndTime()).toMinutes();
		long secondsRemaining = hold.getSecondsRemaining(Instant.now());

		return new SuggestionView(hold.getId(), vetName, specialty, hold.getStartTime().toLocalDate(),
				hold.getStartTime().toLocalTime(), durationMinutes, secondsRemaining);
	}

}
