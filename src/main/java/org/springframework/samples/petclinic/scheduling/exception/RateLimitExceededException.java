package org.springframework.samples.petclinic.scheduling.exception;

import java.time.Instant;

public class RateLimitExceededException extends RuntimeException {

	private final Instant retryAt;

	public RateLimitExceededException(Instant retryAt) {
		super("Rate limit exceeded for AI interpretation dispatches. Retry available at " + retryAt);
		this.retryAt = retryAt;
	}

	public Instant getRetryAt() {
		return retryAt;
	}

}
