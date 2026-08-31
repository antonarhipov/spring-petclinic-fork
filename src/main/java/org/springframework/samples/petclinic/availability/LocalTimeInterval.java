package org.springframework.samples.petclinic.availability;

import java.time.LocalTime;
import java.util.Objects;
import java.util.Optional;

public record LocalTimeInterval(LocalTime start, LocalTime end) implements Comparable<LocalTimeInterval> {

	public LocalTimeInterval {
		Objects.requireNonNull(start, "start must not be null");
		Objects.requireNonNull(end, "end must not be null");
		if (end.isBefore(start)) {
			throw new IllegalArgumentException("end (" + end + ") must not be before start (" + start + ")");
		}
	}

	public boolean isEmpty() {
		return this.start.equals(this.end);
	}

	public boolean contains(LocalTime time) {
		Objects.requireNonNull(time, "time must not be null");
		return !time.isBefore(this.start) && time.isBefore(this.end);
	}

	public boolean encloses(LocalTimeInterval other) {
		Objects.requireNonNull(other, "other must not be null");
		return !other.start.isBefore(this.start) && !other.end.isAfter(this.end);
	}

	public boolean overlaps(LocalTimeInterval other) {
		Objects.requireNonNull(other, "other must not be null");
		return this.start.isBefore(other.end) && other.start.isBefore(this.end);
	}

	public Optional<LocalTimeInterval> intersection(LocalTimeInterval other) {
		Objects.requireNonNull(other, "other must not be null");
		LocalTime maxStart = this.start.isAfter(other.start) ? this.start : other.start;
		LocalTime minEnd = this.end.isBefore(other.end) ? this.end : other.end;
		if (maxStart.isBefore(minEnd)) {
			return Optional.of(new LocalTimeInterval(maxStart, minEnd));
		}
		return Optional.empty();
	}

	@Override
	public int compareTo(LocalTimeInterval other) {
		int startComp = this.start.compareTo(other.start);
		if (startComp != 0) {
			return startComp;
		}
		return this.end.compareTo(other.end);
	}

}
