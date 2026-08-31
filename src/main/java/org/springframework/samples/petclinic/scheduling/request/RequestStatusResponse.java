package org.springframework.samples.petclinic.scheduling.request;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record RequestStatusResponse(Long requestId, Integer version, String displayState, String canonicalUrl,
		PrimaryAction primaryAction, String serverNow, String idleExpiresAt, String warningAt, String offerExpiresAt,
		boolean urgentGuidance, boolean terminal, int pollAfterMillis) {

	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record PrimaryAction(String label, String url) {
	}

}
