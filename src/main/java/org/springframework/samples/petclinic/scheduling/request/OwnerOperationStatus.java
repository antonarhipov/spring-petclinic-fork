package org.springframework.samples.petclinic.scheduling.request;

import java.time.Instant;
import java.util.UUID;

public record OwnerOperationStatus(UUID operationId, String type, String state, String statusText, long requestVersion,
		Instant serverTime, Integer pollAfterMs, String nextUrl) {
}
