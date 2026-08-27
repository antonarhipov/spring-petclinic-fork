package org.springframework.samples.petclinic.scheduling.matching;

import java.time.Instant;
import java.util.Objects;

public class CandidateSlot {

	private final Integer veterinarianId;

	private final Integer ownerId;

	private final Integer petId;

	private final Instant startInstant;

	private final Instant endInstant;

	private final boolean validDstOffset;

	private final boolean startOnOrAfterNextGrid;

	private final boolean startBeforeHorizonEnd;

	private final boolean inContiguousAvailability;

	private final boolean overlapsClosureOrLeave;

	private final boolean vetActiveAndEligible;

	private final boolean matchesAllowedWindows;

	private final boolean matchesExcludedWindows;

	private final boolean exactRejected;

	private final boolean preferredWindow;

	private final boolean preferredVet;

	private final long committedWorkloadMinutes;

	public CandidateSlot(Integer veterinarianId, Integer ownerId, Integer petId, Instant startInstant,
			Instant endInstant, boolean validDstOffset, boolean startOnOrAfterNextGrid, boolean startBeforeHorizonEnd,
			boolean inContiguousAvailability, boolean overlapsClosureOrLeave, boolean vetActiveAndEligible,
			boolean matchesAllowedWindows, boolean matchesExcludedWindows, boolean exactRejected,
			boolean preferredWindow, boolean preferredVet, long committedWorkloadMinutes) {
		this.veterinarianId = veterinarianId;
		this.ownerId = ownerId;
		this.petId = petId;
		this.startInstant = startInstant;
		this.endInstant = endInstant;
		this.validDstOffset = validDstOffset;
		this.startOnOrAfterNextGrid = startOnOrAfterNextGrid;
		this.startBeforeHorizonEnd = startBeforeHorizonEnd;
		this.inContiguousAvailability = inContiguousAvailability;
		this.overlapsClosureOrLeave = overlapsClosureOrLeave;
		this.vetActiveAndEligible = vetActiveAndEligible;
		this.matchesAllowedWindows = matchesAllowedWindows;
		this.matchesExcludedWindows = matchesExcludedWindows;
		this.exactRejected = exactRejected;
		this.preferredWindow = preferredWindow;
		this.preferredVet = preferredVet;
		this.committedWorkloadMinutes = committedWorkloadMinutes;
	}

	public Integer getVeterinarianId() {
		return veterinarianId;
	}

	public Integer getOwnerId() {
		return ownerId;
	}

	public Integer getPetId() {
		return petId;
	}

	public Instant getStartInstant() {
		return startInstant;
	}

	public Instant getEndInstant() {
		return endInstant;
	}

	public boolean isValidDstOffset() {
		return validDstOffset;
	}

	public boolean isStartOnOrAfterNextGrid() {
		return startOnOrAfterNextGrid;
	}

	public boolean isStartBeforeHorizonEnd() {
		return startBeforeHorizonEnd;
	}

	public boolean isInContiguousAvailability() {
		return inContiguousAvailability;
	}

	public boolean isOverlapsClosureOrLeave() {
		return overlapsClosureOrLeave;
	}

	public boolean isVetActiveAndEligible() {
		return vetActiveAndEligible;
	}

	public boolean isMatchesAllowedWindows() {
		return matchesAllowedWindows;
	}

	public boolean isMatchesExcludedWindows() {
		return matchesExcludedWindows;
	}

	public boolean isExactRejected() {
		return exactRejected;
	}

	public boolean isPreferredWindow() {
		return preferredWindow;
	}

	public boolean isPreferredVet() {
		return preferredVet;
	}

	public long getCommittedWorkloadMinutes() {
		return committedWorkloadMinutes;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		CandidateSlot that = (CandidateSlot) o;
		return Objects.equals(veterinarianId, that.veterinarianId) && Objects.equals(startInstant, that.startInstant)
				&& Objects.equals(endInstant, that.endInstant);
	}

	@Override
	public int hashCode() {
		return Objects.hash(veterinarianId, startInstant, endInstant);
	}

	@Override
	public String toString() {
		return "CandidateSlot{" + "vetId=" + veterinarianId + ", start=" + startInstant + ", end=" + endInstant + '}';
	}

}
