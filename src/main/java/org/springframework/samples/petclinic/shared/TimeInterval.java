package org.springframework.samples.petclinic.shared;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable half-open time interval [start, end).
 */
public final class TimeInterval implements Comparable<TimeInterval> {

	private final Instant start;

	private final Instant end;

	public TimeInterval(Instant start, Instant end) {
		Objects.requireNonNull(start, "start must not be null");
		Objects.requireNonNull(end, "end must not be null");
		if (end.isBefore(start)) {
			throw new IllegalArgumentException("end (" + end + ") must not be before start (" + start + ")");
		}
		this.start = start;
		this.end = end;
	}

	public static TimeInterval of(Instant start, Instant end) {
		return new TimeInterval(start, end);
	}

	public static TimeInterval ofDuration(Instant start, Duration duration) {
		Objects.requireNonNull(start, "start must not be null");
		Objects.requireNonNull(duration, "duration must not be null");
		return new TimeInterval(start, start.plus(duration));
	}

	public Instant getStart() {
		return this.start;
	}

	public Instant getStartAt() {
		return this.start;
	}

	public Instant getEnd() {
		return this.end;
	}

	public Instant getEndAt() {
		return this.end;
	}

	public Duration getDuration() {
		return Duration.between(this.start, this.end);
	}

	public long getDurationMinutes() {
		return Duration.between(this.start, this.end).toMinutes();
	}

	public boolean isEmpty() {
		return this.start.equals(this.end);
	}

	public boolean contains(Instant instant) {
		Objects.requireNonNull(instant, "instant must not be null");
		return !instant.isBefore(this.start) && instant.isBefore(this.end);
	}

	public boolean encloses(TimeInterval other) {
		Objects.requireNonNull(other, "other must not be null");
		return !other.start.isBefore(this.start) && !other.end.isAfter(this.end);
	}

	public boolean overlaps(TimeInterval other) {
		Objects.requireNonNull(other, "other must not be null");
		return this.start.isBefore(other.end) && other.start.isBefore(this.end);
	}

	public boolean isAdjacent(TimeInterval other) {
		Objects.requireNonNull(other, "other must not be null");
		return this.end.equals(other.start) || this.start.equals(other.end);
	}

	public Optional<TimeInterval> intersection(TimeInterval other) {
		Objects.requireNonNull(other, "other must not be null");
		Instant maxStart = this.start.isAfter(other.start) ? this.start : other.start;
		Instant minEnd = this.end.isBefore(other.end) ? this.end : other.end;
		if (maxStart.isBefore(minEnd)) {
			return Optional.of(new TimeInterval(maxStart, minEnd));
		}
		return Optional.empty();
	}

	@Override
	public int compareTo(TimeInterval other) {
		int startComp = this.start.compareTo(other.start);
		if (startComp != 0) {
			return startComp;
		}
		return this.end.compareTo(other.end);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		TimeInterval that = (TimeInterval) o;
		return this.start.equals(that.start) && this.end.equals(that.end);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.start, this.end);
	}

	@Override
	public String toString() {
		return "[" + this.start + ", " + this.end + ")";
	}

}
