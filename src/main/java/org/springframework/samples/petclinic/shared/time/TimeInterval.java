package org.springframework.samples.petclinic.shared.time;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable value object representing a half-open time interval [start, end).
 */
public final class TimeInterval implements Serializable, Comparable<TimeInterval> {

	private static final long serialVersionUID = 1L;

	private final Instant start;

	private final Instant end;

	public TimeInterval(Instant start, Instant end) {
		Objects.requireNonNull(start, "start must not be null");
		Objects.requireNonNull(end, "end must not be null");
		if (!start.isBefore(end)) {
			throw new IllegalArgumentException(
					"Interval start (" + start + ") must be strictly before end (" + end + ")");
		}
		this.start = start;
		this.end = end;
	}

	public static TimeInterval of(Instant start, Instant end) {
		return new TimeInterval(start, end);
	}

	public static TimeInterval of(Instant start, Duration duration) {
		Objects.requireNonNull(start, "start must not be null");
		Objects.requireNonNull(duration, "duration must not be null");
		if (duration.isNegative() || duration.isZero()) {
			throw new IllegalArgumentException("Duration must be positive");
		}
		return new TimeInterval(start, start.plus(duration));
	}

	public Instant getStart() {
		return this.start;
	}

	public Instant getEnd() {
		return this.end;
	}

	public Duration getDuration() {
		return Duration.between(this.start, this.end);
	}

	public boolean contains(Instant instant) {
		Objects.requireNonNull(instant, "instant must not be null");
		return !instant.isBefore(this.start) && instant.isBefore(this.end);
	}

	public boolean overlaps(TimeInterval other) {
		Objects.requireNonNull(other, "other must not be null");
		return this.start.isBefore(other.end) && other.start.isBefore(this.end);
	}

	public boolean isAdjacent(TimeInterval other) {
		Objects.requireNonNull(other, "other must not be null");
		return this.start.equals(other.end) || this.end.equals(other.start);
	}

	public boolean encloses(TimeInterval other) {
		Objects.requireNonNull(other, "other must not be null");
		return !this.start.isAfter(other.start) && !this.end.isBefore(other.end);
	}

	@Override
	public int compareTo(TimeInterval other) {
		int startComparison = this.start.compareTo(other.start);
		if (startComparison != 0) {
			return startComparison;
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
		return Objects.equals(this.start, that.start) && Objects.equals(this.end, that.end);
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
