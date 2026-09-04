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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.samples.petclinic.scheduling.interpretation.Interpretation;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationRepository;
import org.springframework.samples.petclinic.scheduling.solver.SlotRanker;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing staff solver suggestions and manual calendar picking holds (AC-90,
 * AC-91, AC-97).
 */
@Service
@Transactional
public class StaffSuggestionService {

	private final SchedulingRequestRepository requestRepository;

	private final InterpretationRepository interpretationRepository;

	private final VetRepository vetRepository;

	private final RequestLifecycleService lifecycleService;

	private final SlotRanker slotRanker;

	private final HoldService holdService;

	private final Clock clock;

	public StaffSuggestionService(SchedulingRequestRepository requestRepository,
			InterpretationRepository interpretationRepository, VetRepository vetRepository,
			RequestLifecycleService lifecycleService, SlotRanker slotRanker, HoldService holdService, Clock clock) {
		this.requestRepository = requestRepository;
		this.interpretationRepository = interpretationRepository;
		this.vetRepository = vetRepository;
		this.lifecycleService = lifecycleService;
		this.slotRanker = slotRanker;
		this.holdService = holdService;
		this.clock = clock;
	}

	public SchedulingRequest suggest(Integer requestId, String actor) {
		Objects.requireNonNull(requestId, "requestId must not be null");
		SchedulingRequest request = this.requestRepository.findById(requestId)
			.orElseThrow(() -> new IllegalArgumentException("SchedulingRequest not found: " + requestId));

		Interpretation interpretation = this.interpretationRepository.findTopByRequestIdOrderByVersionDesc(requestId)
			.orElse(null);

		if (interpretation == null || interpretation.isCannotInterpret()) {
			throw new IllegalStateException("Cannot suggest: request requires a complete staff interpretation.");
		}

		// Lock vets in stable order
		this.vetRepository.findAll()
			.stream()
			.sorted(Comparator.comparingInt(Vet::getId))
			.forEach(vet -> this.vetRepository.findByIdForUpdate(vet.getId()).orElseThrow());

		List<SlotRanker.RankedSlot> ranked = this.slotRanker.rankSlots(request, interpretation);
		for (SlotRanker.RankedSlot candidate : ranked) {
			if (candidate.vet() == null || candidate.vet().getId() == null) {
				continue;
			}
			Vet lockedVet = this.vetRepository.findByIdForUpdate(candidate.vet().getId()).orElseThrow();
			if (this.holdService.isAvailable(lockedVet.getId(), candidate.startTime(), candidate.duration(),
					requestId)) {
				String effectiveActor = (actor != null && !actor.isBlank()) ? actor : "staff";
				return placeHold(request, lockedVet, candidate.startTime(), candidate.duration(), effectiveActor);
			}
		}

		return request;
	}

	public SchedulingRequest placeHold(Integer requestId, Vet vet, ZonedDateTime start, int duration, String actor) {
		Objects.requireNonNull(requestId, "requestId must not be null");
		SchedulingRequest request = this.requestRepository.findById(requestId)
			.orElseThrow(() -> new IllegalArgumentException("SchedulingRequest not found: " + requestId));
		return placeHold(request, vet, start, duration, actor);
	}

	public SchedulingRequest placeHold(SchedulingRequest request, Vet vet, ZonedDateTime start, int duration,
			String actor) {
		Objects.requireNonNull(request, "request must not be null");
		Objects.requireNonNull(vet, "vet must not be null");
		Objects.requireNonNull(start, "start must not be null");
		String effectiveActor = (actor != null && !actor.isBlank()) ? actor : "staff";
		return this.lifecycleService.staffPlaceSuggestion(request, effectiveActor, vet, start, duration);
	}

}
