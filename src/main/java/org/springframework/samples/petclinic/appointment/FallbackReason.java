/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.appointment;

/**
 * Why an appointment request needs staff assistance.
 */
public enum FallbackReason {

	DECLINED_CONSENT, AI_UNAVAILABLE, INCOMPLETE_INTERPRETATION, SOLVER_UNAVAILABLE, NO_MATCHING_SPECIALTY,
	NO_FEASIBLE_SLOT

}
