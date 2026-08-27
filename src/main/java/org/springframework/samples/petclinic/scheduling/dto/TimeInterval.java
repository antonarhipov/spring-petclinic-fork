package org.springframework.samples.petclinic.scheduling.dto;

import java.time.Instant;
import java.util.Objects;

public class TimeInterval implements Comparable<TimeInterval> {

	private final Instant start;

	private final Instant end;

	private final Integer vetId;

	public TimeInterval(Instant start, Instant end) {
		this(start, end, null);
	}

	public TimeInterval(Instant start, Instant end, Integer vetId) {
		if (start == null || end == null) {
			throw new IllegalArgumentException("Start and end times must not be null");
		}
		if (!start.isBefore(end)) {
			throw new IllegalArgumentException("Start time must be strictly before end time");
		}
		this.start = start;
		this.end = end;
		this.vetId = vetId;
	}

	public Instant getStart() {
		return start;
	}

	public Instant getEnd() {
		return end;
	}

	public Integer getVetId() {
		return vetId;
	}

	public boolean overlaps(TimeInterval other) {
		return this.start.isBefore(other.end) && other.start.isBefore(this.end);
	}

	public boolean overlaps(Instant otherStart, Instant otherEnd) {
		return this.start.isBefore(otherEnd) && otherStart.isBefore(this.end);
	}

	public boolean contains(Instant instant) {
		return !instant.isBefore(this.start) && instant.isBefore(this.end);
	}

	public boolean contains(Instant intervalStart, Instant intervalEnd) {
		return !intervalStart.isBefore(this.start) && !intervalEnd.isAfter(this.end);
	}

	@Override
	public int compareTo(TimeInterval other) {
		int cmp = this.start.compareTo(other.start);
		if (cmp != 0) {
			return cmp;
		}
		return this.end.compareTo(other.end);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof TimeInterval that)) {
			return false;
		}
		return Objects.equals(start, that.start) && Objects.equals(end, that.end) && Objects.equals(vetId, that.vetId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(start, end, vetId);
	}

	@Override
	public String toString() {
		return "TimeInterval{" + "start=" + start + ", end=" + end + (vetId != null ? ", vetId=" + vetId : "") + '}';
	}

}
