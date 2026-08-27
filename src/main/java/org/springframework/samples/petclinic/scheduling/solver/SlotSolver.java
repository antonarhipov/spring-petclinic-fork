/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.scheduling.solver;

import java.util.List;
import java.util.Optional;

public interface SlotSolver {

	Optional<RankedSlot> solve(List<RankedSlot> candidates);

}
