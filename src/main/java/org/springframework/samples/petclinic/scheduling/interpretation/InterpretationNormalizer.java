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

import org.springframework.samples.petclinic.scheduling.clinic.ClinicConfig;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicConfigRepository;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;

/** Applies deterministic application-owned boundaries to model output. */
@Service
public class InterpretationNormalizer {

	private final ClinicConfigRepository configRepository;

	private final VetRepository vetRepository;

	public InterpretationNormalizer(ClinicConfigRepository configRepository, VetRepository vetRepository) {
		this.configRepository = configRepository;
		this.vetRepository = vetRepository;
	}

	public InterpretationResult normalize(InterpretationResult result) {
		ClinicConfig config = this.configRepository.findById(1).orElseThrow();
		int duration = result.estimatedMinutes() == null ? config.getDefaultDurationMinutes()
				: result.estimatedMinutes();
		duration = Math.max(config.getMinDurationMinutes(), Math.min(config.getMaxDurationMinutes(), duration));
		Integer preferredVetId = result.preferredVetId();
		if (preferredVetId != null && this.vetRepository.findById(preferredVetId).isEmpty()) {
			preferredVetId = null;
		}
		return new InterpretationResult(result.reasonSummary(), duration, result.careType(), result.specialty(),
				preferredVetId, result.cannotInterpret(), result.windows(), result.rawResponse(), result.modelTag(),
				result.promptVersion());
	}

}
