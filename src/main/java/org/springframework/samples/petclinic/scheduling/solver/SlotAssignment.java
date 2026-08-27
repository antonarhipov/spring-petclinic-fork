/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.scheduling.solver;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;

@PlanningEntity
public class SlotAssignment {

	@PlanningVariable(valueRangeProviderRefs = "slotRange")
	private RankedSlot slot;

	public SlotAssignment() {
	}

	public RankedSlot getSlot() {
		return this.slot;
	}

	public void setSlot(RankedSlot slot) {
		this.slot = slot;
	}

}
