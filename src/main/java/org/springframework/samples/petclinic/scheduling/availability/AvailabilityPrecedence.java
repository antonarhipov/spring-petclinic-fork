package org.springframework.samples.petclinic.scheduling.availability;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

public final class AvailabilityPrecedence {

	private AvailabilityPrecedence() {
	}

	public record Interval(LocalTime start, LocalTime end) {
	}

	public static boolean isAvailable(boolean clinicClosed, boolean veterinarianOnLeave, boolean hasDateException,
			List<Interval> dateExceptionIntervals, List<Interval> recurringShiftIntervals, ZoneId zone, Instant startAt,
			Instant endAt) {
		if (clinicClosed || veterinarianOnLeave) {
			return false;
		}
		List<Interval> intervals = hasDateException ? dateExceptionIntervals : recurringShiftIntervals;
		return covered(intervals, zone, startAt, endAt);
	}

	public static boolean covered(List<Interval> intervals, ZoneId zone, Instant startAt, Instant endAt) {
		if (intervals.isEmpty()) {
			return false;
		}
		Instant cursor = startAt;
		while (cursor.isBefore(endAt)) {
			LocalTime local = cursor.atZone(zone).toLocalTime();
			boolean open = intervals.stream()
				.anyMatch(interval -> !local.isBefore(interval.start()) && local.isBefore(interval.end()));
			if (!open) {
				return false;
			}
			cursor = cursor.plusSeconds(15 * 60);
		}
		return true;
	}

}
