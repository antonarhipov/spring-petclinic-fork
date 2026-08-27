package org.springframework.samples.petclinic.scheduling.service;

import java.time.Instant;
import java.util.List;

import org.springframework.samples.petclinic.scheduling.model.AvailabilityWindowKind;
import org.springframework.samples.petclinic.scheduling.model.RequestAvailabilityInterval;

public final class OwnerWindowPolicy {

	private OwnerWindowPolicy() {
	}

	public static Evaluation evaluate(List<RequestAvailabilityInterval> intervals, Instant start, Instant end) {
		boolean hasAllowed = intervals.stream().anyMatch(i -> i.getKind() == AvailabilityWindowKind.ALLOWED);
		boolean allowed = !hasAllowed || intervals.stream()
			.filter(i -> i.getKind() == AvailabilityWindowKind.ALLOWED)
			.anyMatch(i -> contains(i, start, end));
		boolean excluded = intervals.stream()
			.filter(i -> i.getKind() == AvailabilityWindowKind.EXCLUDED)
			.anyMatch(i -> overlaps(i, start, end));
		boolean preferred = intervals.stream()
			.filter(i -> i.getKind() == AvailabilityWindowKind.PREFERRED)
			.anyMatch(i -> contains(i, start, end));
		return new Evaluation(allowed, excluded, preferred);
	}

	private static boolean contains(RequestAvailabilityInterval interval, Instant start, Instant end) {
		return !start.isBefore(interval.getStartInstant()) && !end.isAfter(interval.getEndInstant());
	}

	private static boolean overlaps(RequestAvailabilityInterval interval, Instant start, Instant end) {
		return start.isBefore(interval.getEndInstant()) && interval.getStartInstant().isBefore(end);
	}

	public record Evaluation(boolean allowed, boolean excluded, boolean preferred) {
	}

}
