package org.springframework.samples.petclinic.scheduling.matching;

import java.time.Instant;
import java.util.Objects;

public final class CandidateSlot {

	private final String id;

	private final int veterinarianId;

	private final Instant startAt;

	private final Instant endAt;

	private final String preferenceClass;

	private final int stableOrdinal;

	public CandidateSlot(String id, int veterinarianId, Instant startAt, Instant endAt, String preferenceClass,
			int stableOrdinal) {
		this.id = id;
		this.veterinarianId = veterinarianId;
		this.startAt = startAt;
		this.endAt = endAt;
		this.preferenceClass = preferenceClass;
		this.stableOrdinal = stableOrdinal;
	}

	public String id() {
		return this.id;
	}

	public int veterinarianId() {
		return this.veterinarianId;
	}

	public Instant startAt() {
		return this.startAt;
	}

	public Instant endAt() {
		return this.endAt;
	}

	public String preferenceClass() {
		return this.preferenceClass;
	}

	public int stableOrdinal() {
		return this.stableOrdinal;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof CandidateSlot other)) {
			return false;
		}
		return Objects.equals(this.id, other.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.id);
	}

}
