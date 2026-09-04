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

package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.samples.petclinic.scheduling.request.HoldService;
import org.springframework.samples.petclinic.scheduling.request.RequestLifecycleService;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEvent;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEventRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.solver.SlotRanker;
import org.springframework.samples.petclinic.scheduling.web.StaffInterpretationForm;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing staff-authored interpretations, versioning, provenance, and solver
 * suggestions (AC-55, AC-56, AC-89, AC-121).
 */
@Service
@Transactional
public class StaffInterpretationService {

	private static final Pattern TIME_RANGE_PATTERN = Pattern.compile("(\\d{1,2}:\\d{2})\\s*-\\s*(\\d{1,2}:\\d{2})");

	private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");

	private static final Pattern DATE_RANGE_PATTERN = Pattern
		.compile("(\\d{4}-\\d{2}-\\d{2})\\s*(?:\\.\\.|/)\\s*(\\d{4}-\\d{2}-\\d{2})");

	private final InterpretationRepository interpretationRepository;

	private final SchedulingRequestRepository requestRepository;

	private final SchedulingRequestEventRepository eventRepository;

	private final VetRepository vetRepository;

	private final RequestLifecycleService lifecycleService;

	private final SlotRanker slotRanker;

	private final HoldService holdService;

	private final Clock clock;

	public StaffInterpretationService(InterpretationRepository interpretationRepository,
			SchedulingRequestRepository requestRepository, SchedulingRequestEventRepository eventRepository,
			VetRepository vetRepository, RequestLifecycleService lifecycleService, SlotRanker slotRanker,
			HoldService holdService, Clock clock) {
		this.interpretationRepository = interpretationRepository;
		this.requestRepository = requestRepository;
		this.eventRepository = eventRepository;
		this.vetRepository = vetRepository;
		this.lifecycleService = lifecycleService;
		this.slotRanker = slotRanker;
		this.holdService = holdService;
		this.clock = clock;
	}

	public Interpretation saveInterpretation(Integer requestId, StaffInterpretationForm form, String actor) {
		Objects.requireNonNull(requestId, "requestId must not be null");
		Objects.requireNonNull(form, "form must not be null");

		SchedulingRequest request = this.requestRepository.findById(requestId)
			.orElseThrow(() -> new IllegalArgumentException("SchedulingRequest not found: " + requestId));

		List<Interpretation> existing = this.interpretationRepository.findByRequestIdOrderByVersionDesc(requestId);
		int nextVersion = existing.isEmpty() ? 1 : existing.get(0).getVersion() + 1;

		Interpretation interpretation = new Interpretation();
		interpretation.setRequest(request);
		interpretation.setVersion(nextVersion);
		interpretation.setProvenance(Provenance.STAFF);
		interpretation.setReasonSummary(form.getReasonSummary());
		interpretation.setEstimatedMinutes(form.getEstimatedMinutes() != null ? form.getEstimatedMinutes() : 30);

		if (form.getCareType() != null && !form.getCareType().isBlank()) {
			try {
				interpretation.setCareType(CareType.valueOf(form.getCareType().trim().toUpperCase()));
			}
			catch (IllegalArgumentException ex) {
				interpretation.setCareType(CareType.GENERAL);
			}
		}
		else {
			interpretation.setCareType(CareType.GENERAL);
		}

		interpretation.setSpecialty(
				form.getSpecialty() != null && !form.getSpecialty().isBlank() ? form.getSpecialty().trim() : null);

		if (form.getPreferredVetId() != null) {
			interpretation.setPreferredVet(this.vetRepository.findById(form.getPreferredVetId()).orElse(null));
		}

		interpretation.setCannotInterpret(form.isCannotInterpret());
		interpretation.setRawResponse(null);
		interpretation.setModelTag(null);
		interpretation.setPromptVersion(null);
		interpretation.setCreatedAt(ZonedDateTime.now(this.clock));

		parseAndAddWindows(form.getPreferredWindows(), WindowKind.PREFERRED, interpretation);
		parseAndAddWindows(form.getAllowedWindows(), WindowKind.ALLOWED, interpretation);
		parseAndAddWindows(form.getExcludedWindows(), WindowKind.EXCLUDED, interpretation);

		Interpretation saved = this.interpretationRepository.save(interpretation);

		if (request.getState() == RequestState.WITH_STAFF) {
			String effectiveActor = (actor != null && !actor.isBlank()) ? actor : "staff";
			this.lifecycleService.staffEditInterpretation(request, effectiveActor);
		}

		return saved;
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
				return this.lifecycleService.staffPlaceSuggestion(request, effectiveActor, lockedVet,
						candidate.startTime(), candidate.duration());
			}
		}

		return request;
	}

	@Transactional(readOnly = true)
	public Interpretation getLatestInterpretation(Integer requestId) {
		return this.interpretationRepository.findTopByRequestIdOrderByVersionDesc(requestId).orElse(null);
	}

	@Transactional(readOnly = true)
	public List<Interpretation> getAllInterpretations(Integer requestId) {
		return this.interpretationRepository.findByRequestIdOrderByVersionDesc(requestId);
	}

	@Transactional(readOnly = true)
	public List<SchedulingRequestEvent> getTimeline(Integer requestId) {
		return this.eventRepository.findByRequestIdOrderByTimestampAsc(requestId);
	}

	private void parseAndAddWindows(String input, WindowKind kind, Interpretation interpretation) {
		if (input == null || input.isBlank()) {
			return;
		}
		String[] tokens = input.split("[;,\n\r]+");
		for (String raw : tokens) {
			String token = raw.trim();
			if (token.isEmpty()) {
				continue;
			}
			InterpretationWindow window = new InterpretationWindow();
			window.setKind(kind);
			window.setTokens(token);
			parseTokenIntoWindow(token, window);
			interpretation.addWindow(window);
		}
	}

	private void parseTokenIntoWindow(String token, InterpretationWindow window) {
		String upper = token.toUpperCase();

		// Check for day of week
		for (DayOfWeek dow : DayOfWeek.values()) {
			if (upper.contains(dow.name())) {
				window.setDayOfWeek(dow);
				break;
			}
		}

		// Check for date range
		Matcher rangeMatcher = DATE_RANGE_PATTERN.matcher(token);
		if (rangeMatcher.find()) {
			window.setStartDate(LocalDate.parse(rangeMatcher.group(1)));
			window.setEndDate(LocalDate.parse(rangeMatcher.group(2)));
		}
		else {
			// Check for single date
			Matcher dateMatcher = DATE_PATTERN.matcher(token);
			if (dateMatcher.find()) {
				window.setDateVal(LocalDate.parse(dateMatcher.group(1)));
			}
		}

		// Check for time range
		Matcher timeMatcher = TIME_RANGE_PATTERN.matcher(token);
		if (timeMatcher.find()) {
			String startStr = timeMatcher.group(1);
			String endStr = timeMatcher.group(2);
			if (startStr.length() == 4) {
				startStr = "0" + startStr;
			}
			if (endStr.length() == 4) {
				endStr = "0" + endStr;
			}
			window.setStartTime(LocalTime.parse(startStr));
			window.setEndTime(LocalTime.parse(endStr));
		}
	}

}
