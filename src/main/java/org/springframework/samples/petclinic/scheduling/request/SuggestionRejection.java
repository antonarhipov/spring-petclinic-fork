/* Copyright 2012-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package org.springframework.samples.petclinic.scheduling.request;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Immutable rejection payload persisted in the request event log (RULE-28).
 */
public record SuggestionRejection(int interpretationVersion, int vetId, ZonedDateTime start, RejectionScope scope) {

	public static final String EVENT_ACTION = "REJECT_SUGGESTION";

	public boolean excludes(int candidateVetId, ZonedDateTime candidateStart, int currentInterpretationVersion) {
		if (this.interpretationVersion != currentInterpretationVersion) {
			return false;
		}
		return switch (this.scope) {
			case NOT_THIS_TIME -> this.vetId == candidateVetId && this.start.equals(candidateStart);
			case NOT_THIS_DAY -> this.start.toLocalDate().equals(candidateStart.toLocalDate());
			case NOT_THIS_VET -> this.vetId == candidateVetId;
		};
	}

	public String payload() {
		return "version=" + this.interpretationVersion + ";vetId=" + this.vetId + ";start=" + this.start + ";scope="
				+ this.scope;
	}

	public static Optional<SuggestionRejection> fromEvent(SchedulingRequestEvent event) {
		if (!EVENT_ACTION.equals(event.getAction()) || event.getPayload() == null) {
			return Optional.empty();
		}
		String[] fields = event.getPayload().split(";");
		if (fields.length != 4) {
			return Optional.empty();
		}
		try {
			int version = Integer.parseInt(value(fields[0], "version"));
			int vetId = Integer.parseInt(value(fields[1], "vetId"));
			ZonedDateTime start = ZonedDateTime.parse(value(fields[2], "start"));
			RejectionScope scope = RejectionScope.parse(value(fields[3], "scope"));
			return Optional.of(new SuggestionRejection(version, vetId, start, scope));
		}
		catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

	public static List<SuggestionRejection> activeFor(List<SchedulingRequestEvent> events, int interpretationVersion) {
		return events.stream()
			.map(SuggestionRejection::fromEvent)
			.flatMap(Optional::stream)
			.filter(rejection -> rejection.interpretationVersion() == interpretationVersion)
			.toList();
	}

	private static String value(String field, String expectedName) {
		String prefix = expectedName + "=";
		if (!field.startsWith(prefix)) {
			throw new IllegalArgumentException("Missing rejection field " + expectedName);
		}
		return field.substring(prefix.length());
	}

}
