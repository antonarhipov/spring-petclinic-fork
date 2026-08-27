/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.scheduling.solver;

import java.time.Instant;

public record SuggestionResult(Status status, Integer requestId, Integer vetId, Instant startInstant, Instant expiresAt,
		String message) {

	public enum Status {

		HELD, QUEUED_FOR_STAFF

	}

	public static SuggestionResult held(Integer requestId, Integer vetId, Instant start, Instant expiresAt,
			String message) {
		return new SuggestionResult(Status.HELD, requestId, vetId, start, expiresAt, message);
	}

	public static SuggestionResult queued(Integer requestId, String message) {
		return new SuggestionResult(Status.QUEUED_FOR_STAFF, requestId, null, null, null, message);
	}

}
