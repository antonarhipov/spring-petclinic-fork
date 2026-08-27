/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.scheduling.interpretation;

import java.util.List;

public record AppointmentInterpretation(String careType, String requiredSpecialty, Integer estimatedDurationMin,
		List<InterpretationWindow> preferred, List<InterpretationWindow> allowed, List<InterpretationWindow> excluded,
		Integer preferredVetId, Urgency urgency) {

	public AppointmentInterpretation {
		preferred = preferred == null ? List.of() : List.copyOf(preferred);
		allowed = allowed == null ? List.of() : List.copyOf(allowed);
		excluded = excluded == null ? List.of() : List.copyOf(excluded);
	}

}
