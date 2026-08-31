package org.springframework.samples.petclinic.scheduling.matching;

import java.time.Instant;
import java.util.Objects;

public class CandidateSlot {

	private final Integer vetId;

	private final String vetName;

	private final Instant startAt;

	private final Instant endAt;

	private final String zoneId;

	private final Integer durationMinutes;

	private final boolean inPreferredWindow;

	private final boolean preferredVet;

	private final boolean inFallbackWindow;

	private final int minutesFromReference;

	private final int gapMinutes;

	private final String tieBreakKey;

	public CandidateSlot(Integer vetId, String vetName, Instant startAt, Instant endAt, String zoneId,
			Integer durationMinutes, boolean inPreferredWindow, boolean preferredVet, boolean inFallbackWindow,
			int minutesFromReference, int gapMinutes, String tieBreakKey) {
		this.vetId = Objects.requireNonNull(vetId, "vetId must not be null");
		this.vetName = Objects.requireNonNull(vetName, "vetName must not be null");
		this.startAt = Objects.requireNonNull(startAt, "startAt must not be null");
		this.endAt = Objects.requireNonNull(endAt, "endAt must not be null");
		this.zoneId = Objects.requireNonNull(zoneId, "zoneId must not be null");
		this.durationMinutes = Objects.requireNonNull(durationMinutes, "durationMinutes must not be null");
		this.inPreferredWindow = inPreferredWindow;
		this.preferredVet = preferredVet;
		this.inFallbackWindow = inFallbackWindow;
		this.minutesFromReference = minutesFromReference;
		this.gapMinutes = gapMinutes;
		this.tieBreakKey = (tieBreakKey != null) ? tieBreakKey : (vetId + ":" + startAt);
	}

	public Integer getVetId() {
		return this.vetId;
	}

	public String getVetName() {
		return this.vetName;
	}

	public Instant getStartAt() {
		return this.startAt;
	}

	public Instant getEndAt() {
		return this.endAt;
	}

	public String getZoneId() {
		return this.zoneId;
	}

	public Integer getDurationMinutes() {
		return this.durationMinutes;
	}

	public boolean isInPreferredWindow() {
		return this.inPreferredWindow;
	}

	public boolean isPreferredVet() {
		return this.preferredVet;
	}

	public boolean isInFallbackWindow() {
		return this.inFallbackWindow;
	}

	public int getMinutesFromReference() {
		return this.minutesFromReference;
	}

	public int getGapMinutes() {
		return this.gapMinutes;
	}

	public String getTieBreakKey() {
		return this.tieBreakKey;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		CandidateSlot that = (CandidateSlot) o;
		return Objects.equals(this.vetId, that.vetId) && Objects.equals(this.startAt, that.startAt);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.vetId, this.startAt);
	}

	@Override
	public String toString() {
		return "CandidateSlot{" + "vetId=" + this.vetId + ", startAt=" + this.startAt + ", endAt=" + this.endAt + '}';
	}

}
