package org.springframework.samples.petclinic.scheduling.matching;

public record SlotSelectionResult(String outcome, CandidateSlot selectedCandidate, SlotScoreComponents score,
		boolean completedNaturally) {
}
