/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.scheduling.solver;

import java.time.Instant;
import java.util.Objects;

public record RankedSlot(Integer vetId, Instant startInstant, int windowRank, boolean preferredVet) {

	public RankedSlot {
		Objects.requireNonNull(vetId, "vetId must not be null");
		Objects.requireNonNull(startInstant, "startInstant must not be null");
	}

}
