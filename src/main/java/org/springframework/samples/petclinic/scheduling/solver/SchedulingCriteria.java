/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.scheduling.solver;

import java.util.List;

public record SchedulingCriteria(int durationMin, String requiredSpecialty, Integer preferredVetId,
		List<SchedulingWindow> preferredWindows, List<SchedulingWindow> allowedWindows,
		List<SchedulingWindow> excludedWindows) {

	public SchedulingCriteria {
		preferredWindows = preferredWindows == null ? List.of() : List.copyOf(preferredWindows);
		allowedWindows = allowedWindows == null ? List.of() : List.copyOf(allowedWindows);
		excludedWindows = excludedWindows == null ? List.of() : List.copyOf(excludedWindows);
	}

}
