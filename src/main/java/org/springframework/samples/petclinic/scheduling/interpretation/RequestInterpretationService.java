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
import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.samples.petclinic.scheduling.request.RequestLifecycleService;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestInterpretationService {

	private final RequestInterpreter interpreter;

	private final InterpretationRepository interpretationRepository;

	private final VetRepository vetRepository;

	private final RequestLifecycleService lifecycleService;

	private final Clock clock;

	public RequestInterpretationService(RequestInterpreter interpreter,
			InterpretationRepository interpretationRepository, VetRepository vetRepository,
			RequestLifecycleService lifecycleService, Clock clock) {
		this.interpreter = interpreter;
		this.interpretationRepository = interpretationRepository;
		this.vetRepository = vetRepository;
		this.lifecycleService = lifecycleService;
		this.clock = clock;
	}

	@Transactional
	public Interpretation interpret(SchedulingRequest request, String actor) {
		InterpretationResult result = this.interpreter.interpret(request.getReasonText(),
				request.getAvailabilityText());
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
		if (result.cannotInterpret()) {
			this.lifecycleService.interpretationFailed(request, actor, "cannotInterpret");
		}
		else {
			this.lifecycleService.interpretationUsable(request, actor);
		}
		return saved;
	}

	@Transactional(readOnly = true)
	public Optional<Interpretation> latest(Integer requestId) {
		return this.interpretationRepository.findTopByRequestIdOrderByVersionDesc(requestId);
	}

}
