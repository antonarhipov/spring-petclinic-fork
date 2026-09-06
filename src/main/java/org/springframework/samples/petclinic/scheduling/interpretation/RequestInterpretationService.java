/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 */

package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicConfigRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestLifecycleService;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
public class RequestInterpretationService {

	private static final Logger logger = LoggerFactory.getLogger(RequestInterpretationService.class);

	private final RequestInterpreter interpreter;

	private final InterpretationRepository interpretationRepository;

	private final VetRepository vetRepository;

	private final RequestLifecycleService lifecycleService;

	private final SchedulingRequestRepository requestRepository;

	private final InterpretationNormalizer normalizer;

	private final ClinicConfigRepository configRepository;

	private final Clock clock;

	public RequestInterpretationService(RequestInterpreter interpreter,
			InterpretationRepository interpretationRepository, VetRepository vetRepository,
			RequestLifecycleService lifecycleService, SchedulingRequestRepository requestRepository,
			InterpretationNormalizer normalizer, ClinicConfigRepository configRepository, Clock clock) {
		this.interpreter = interpreter;
		this.interpretationRepository = interpretationRepository;
		this.vetRepository = vetRepository;
		this.lifecycleService = lifecycleService;
		this.requestRepository = requestRepository;
		this.normalizer = normalizer;
		this.configRepository = configRepository;
		this.clock = clock;
	}

	@Transactional
	public Interpretation interpret(SchedulingRequest request, String actor) {
		InterpretationResult result = this.interpreter.interpret(request.getReasonText(),
				request.getAvailabilityText());
		return persist(request, result, actor);
	}

	@Transactional(readOnly = true)
	public Optional<InterpretationInput> inputFor(Integer requestId) {
		return this.requestRepository.findById(requestId)
			.filter(request -> request.getState() == RequestState.INTERPRETING)
			.map(request -> new InterpretationInput(request.getReasonText(), request.getAvailabilityText()));
	}

	public InterpretationResult interpret(InterpretationInput input) {
		return this.interpreter.interpret(input.reasonText(), input.availabilityText());
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Optional<Interpretation> applyResult(Integer requestId, InterpretationResult result, String actor) {
		Optional<SchedulingRequest> request = this.requestRepository.findById(requestId)
			.filter(candidate -> candidate.getState() == RequestState.INTERPRETING);
		if (request.isEmpty()) {
			logger.info("Discarding interpretation result requestId={}; request is missing or no longer INTERPRETING",
					requestId);
			return Optional.empty();
		}
		return Optional.of(persist(request.orElseThrow(), result, actor));
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void applyModelUnavailable(Integer requestId, String reason, String actor) {
		this.requestRepository.findById(requestId)
			.filter(request -> request.getState() == RequestState.INTERPRETING)
			.ifPresent(request -> this.lifecycleService.interpretationModelUnavailable(request, actor, reason));
	}

	private Interpretation persist(SchedulingRequest request, InterpretationResult result, String actor) {
		logger.debug("Mapping interpretation result to domain requestId={} modelResult={}", request.getId(), result);
		result = this.normalizer.normalize(result);
		logger.debug("Normalized interpretation requestId={} normalizedResult={}", request.getId(), result);
		Interpretation interpretation = new Interpretation();
		interpretation.setRequest(request);
		interpretation
			.setVersion(this.interpretationRepository.findByRequestIdOrderByVersionDesc(request.getId()).size() + 1);
		interpretation.setProvenance(Provenance.AI);
		interpretation.setReasonSummary(result.reasonSummary());
		interpretation.setEstimatedMinutes(result.estimatedMinutes());
		interpretation.setCareType(result.careType());
		interpretation.setSpecialty(result.specialty());
		if (result.preferredVetId() != null) {
			interpretation.setPreferredVet(this.vetRepository.findById(result.preferredVetId()).orElse(null));
		}
		interpretation.setCannotInterpret(result.cannotInterpret());
		interpretation.setRawResponse(result.rawResponse());
		interpretation.setModelTag(result.modelTag());
		interpretation.setPromptVersion(result.promptVersion());
		interpretation.setCreatedAt(ZonedDateTime.now(this.clock));
		for (AvailabilityWindow window : result.windows()) {
			InterpretationWindow persisted = new InterpretationWindow();
			persisted.setKind(window.kind());
			persisted.setDateVal(window.dateVal());
			persisted.setStartDate(window.startDate());
			persisted.setEndDate(window.endDate());
			persisted.setDayOfWeek(window.dayOfWeek());
			persisted.setStartTime(window.startTime());
			persisted.setEndTime(window.endTime());
			persisted.setTokens(window.tokens());
			interpretation.addWindow(persisted);
		}
		Interpretation saved = this.interpretationRepository.save(interpretation);
		logger.debug(
				"Persisted interpretation domain object requestId={} interpretationId={} version={} provenance={} reasonSummary={} estimatedMinutes={} careType={} specialty={} preferredVetId={} cannotInterpret={} windows={}",
				request.getId(), saved.getId(), saved.getVersion(), saved.getProvenance(), saved.getReasonSummary(),
				saved.getEstimatedMinutes(), saved.getCareType(), saved.getSpecialty(),
				saved.getPreferredVet() == null ? null : saved.getPreferredVet().getId(), saved.isCannotInterpret(),
				result.windows());
		if (isUnmatchedOtherSpecialty(result.specialty())) {
			logger.info("Interpretation outcome requestId={} route=WITH_STAFF reason=unmatched-specialty specialty={}",
					request.getId(), result.specialty());
			this.lifecycleService.interpretationUnmatchedSpecialty(request, actor, result.specialty());
		}
		else {
			boolean contradictory = !result.cannotInterpret() && isContradictory(result.windows());
			if (result.cannotInterpret() || contradictory) {
				String reason = result.cannotInterpret() ? "cannotInterpret" : "contradictory windows";
				logger.info("Interpretation outcome requestId={} route=INTERPRETATION_FAILED reason={}",
						request.getId(), reason);
				this.lifecycleService.interpretationFailed(request, actor, reason);
			}
			else {
				logger.info("Interpretation outcome requestId={} route=INTERPRETED", request.getId());
				this.lifecycleService.interpretationUsable(request, actor);
			}
		}
		return saved;
	}

	private boolean isUnmatchedOtherSpecialty(String specialty) {
		if (specialty == null || !specialty.startsWith("OTHER:")) {
			return false;
		}
		String requested = specialty.substring("OTHER:".length()).trim();
		return this.vetRepository.findAll()
			.stream()
			.flatMap(vet -> vet.getSpecialties().stream())
			.noneMatch(offered -> offered.getName().equalsIgnoreCase(requested));
	}

	/**
	 * Base outcome check for RULE-19. Window normalization and ranking remain separate
	 * concerns; this check only proves that at least one minute survives the positive
	 * window union and exclusions inside the configured horizon.
	 */
	private boolean isContradictory(List<AvailabilityWindow> windows) {
		List<AvailabilityWindow> positives = windows.stream()
			.filter(window -> window.kind() != WindowKind.EXCLUDED)
			.toList();
		List<AvailabilityWindow> exclusions = windows.stream()
			.filter(window -> window.kind() == WindowKind.EXCLUDED)
			.toList();
		LocalDate firstDate = LocalDate.now(this.clock);
		LocalDate lastDate = firstDate
			.plusDays(this.configRepository.findById(1).orElseThrow().getBookingHorizonDays());

		for (LocalDate date = firstDate; !date.isAfter(lastDate); date = date.plusDays(1)) {
			List<MinuteRange> allowed = positives.isEmpty() ? List.of(new MinuteRange(0, 1440))
					: rangesFor(positives, date);
			if (hasUnexcludedMinute(allowed, rangesFor(exclusions, date))) {
				return false;
			}
		}
		return true;
	}

	private static List<MinuteRange> rangesFor(List<AvailabilityWindow> windows, LocalDate date) {
		return windows.stream()
			.filter(window -> appliesOn(window, date))
			.map(window -> new MinuteRange(toStartMinute(window.startTime()), toEndMinute(window.endTime())))
			.filter(range -> range.start() < range.end())
			.toList();
	}

	private static boolean appliesOn(AvailabilityWindow window, LocalDate date) {
		if (window.dateVal() != null) {
			return window.dateVal().equals(date);
		}
		if (window.startDate() != null || window.endDate() != null) {
			boolean afterStart = window.startDate() == null || !date.isBefore(window.startDate());
			boolean beforeEnd = window.endDate() == null || !date.isAfter(window.endDate());
			return afterStart && beforeEnd;
		}
		return window.dayOfWeek() == null || window.dayOfWeek() == date.getDayOfWeek();
	}

	private static int toStartMinute(LocalTime time) {
		return time == null ? 0 : time.getHour() * 60 + time.getMinute();
	}

	private static int toEndMinute(LocalTime time) {
		return time == null || time.equals(LocalTime.MAX) ? 1440 : time.getHour() * 60 + time.getMinute();
	}

	private static boolean hasUnexcludedMinute(List<MinuteRange> allowed, List<MinuteRange> exclusions) {
		List<MinuteRange> orderedExclusions = new ArrayList<>(exclusions);
		orderedExclusions.sort(Comparator.comparingInt(MinuteRange::start));
		for (MinuteRange candidate : allowed) {
			int cursor = candidate.start();
			for (MinuteRange exclusion : orderedExclusions) {
				if (exclusion.end() <= cursor || exclusion.start() >= candidate.end()) {
					continue;
				}
				if (exclusion.start() > cursor) {
					return true;
				}
				cursor = Math.max(cursor, exclusion.end());
				if (cursor >= candidate.end()) {
					break;
				}
			}
			if (cursor < candidate.end()) {
				return true;
			}
		}
		return false;
	}

	private record MinuteRange(int start, int end) {
	}

	@Transactional(readOnly = true)
	public Optional<Interpretation> latest(Integer requestId) {
		return this.interpretationRepository.findTopByRequestIdOrderByVersionDesc(requestId);
	}

	public record InterpretationInput(String reasonText, String availabilityText) {
	}

}
