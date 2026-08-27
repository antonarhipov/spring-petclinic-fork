package org.springframework.samples.petclinic.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.scheduling.model.AvailabilityWindowKind;
import org.springframework.samples.petclinic.scheduling.model.RequestAvailabilityInterval;

class OwnerWindowPolicyTests {

	@Test
	void enforcesAllowedContainmentExcludedOverlapAndPreferredContainment() {
		List<RequestAvailabilityInterval> windows = List.of(
				interval(AvailabilityWindowKind.ALLOWED, "2026-09-03T12:00:00Z", "2026-09-03T17:00:00Z"),
				interval(AvailabilityWindowKind.PREFERRED, "2026-09-03T13:00:00Z", "2026-09-03T15:00:00Z"),
				interval(AvailabilityWindowKind.EXCLUDED, "2026-09-03T14:00:00Z", "2026-09-03T14:30:00Z"));

		assertThat(evaluate(windows, "2026-09-03T13:15:00Z", "2026-09-03T13:45:00Z"))
			.isEqualTo(new OwnerWindowPolicy.Evaluation(true, false, true));
		assertThat(evaluate(windows, "2026-09-03T14:15:00Z", "2026-09-03T14:45:00Z"))
			.isEqualTo(new OwnerWindowPolicy.Evaluation(true, true, true));
		assertThat(evaluate(windows, "2026-09-03T10:45:00Z", "2026-09-03T11:15:00Z"))
			.isEqualTo(new OwnerWindowPolicy.Evaluation(false, false, false));
	}

	@Test
	void treatsHalfOpenExcludedAdjacencyAsNonOverlapping() {
		List<RequestAvailabilityInterval> windows = List
			.of(interval(AvailabilityWindowKind.EXCLUDED, "2026-09-03T14:00:00Z", "2026-09-03T14:30:00Z"));

		assertThat(evaluate(windows, "2026-09-03T13:30:00Z", "2026-09-03T14:00:00Z").excluded()).isFalse();
		assertThat(evaluate(windows, "2026-09-03T14:30:00Z", "2026-09-03T15:00:00Z").excluded()).isFalse();
	}

	@Test
	void defaultsToAllowedWhenNoHardAllowedWindowExists() {
		OwnerWindowPolicy.Evaluation result = evaluate(List.of(), "2026-09-03T10:45:00Z", "2026-09-03T11:15:00Z");

		assertThat(result).isEqualTo(new OwnerWindowPolicy.Evaluation(true, false, false));
	}

	private OwnerWindowPolicy.Evaluation evaluate(List<RequestAvailabilityInterval> windows, String start, String end) {
		return OwnerWindowPolicy.evaluate(windows, Instant.parse(start), Instant.parse(end));
	}

	private RequestAvailabilityInterval interval(AvailabilityWindowKind kind, String start, String end) {
		RequestAvailabilityInterval interval = new RequestAvailabilityInterval();
		interval.setKind(kind);
		interval.setStartInstant(Instant.parse(start));
		interval.setEndInstant(Instant.parse(end));
		return interval;
	}

}
