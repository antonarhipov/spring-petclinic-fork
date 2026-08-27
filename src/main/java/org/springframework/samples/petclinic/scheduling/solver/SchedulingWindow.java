/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.scheduling.solver;

import java.time.Instant;
import java.util.Objects;

public record SchedulingWindow(Instant start, Instant end) {

	public SchedulingWindow {
		Objects.requireNonNull(start, "start must not be null");
		Objects.requireNonNull(end, "end must not be null");
		if (!start.isBefore(end)) {
			throw new IllegalArgumentException("start must be before end");
		}
	}

	public boolean contains(Instant instant) {
		return !instant.isBefore(this.start) && instant.isBefore(this.end);
	}

}
