package org.springframework.samples.petclinic.scheduling.matching;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimefoldMatchingServiceTests {

	@Test
	void preferredOnlySelectsPreferredSlotRepeatably() {
		Instant start = Instant.parse("2026-03-16T15:00:00Z");
		Instant preferred = Instant.parse("2026-03-16T16:00:00Z");
		SlotSelectionSnapshot snapshot = snapshot(MatchingMode.PREFERRED_ONLY, start, preferred);
		TimefoldSlotSolver solver = new TimefoldSlotSolver(
				new TimefoldSolverConfiguration().slotSelectionSolverFactory());
		Instant deadline = Instant.now().plusSeconds(4);
		SlotSelectionResult first = solver.solve(snapshot, deadline);
		SlotSelectionResult second = solver.solve(snapshot, deadline);
		assertThat(first.outcome()).isEqualTo("SELECTED");
		assertThat(first.selectedCandidate().startAt()).isEqualTo(preferred);
		assertThat(second.selectedCandidate().id()).isEqualTo(first.selectedCandidate().id());
		assertThat(first.completedNaturally()).isTrue();
	}

	@Test
	void pastDeadlineIsRejectedWithoutCandidate() {
		Instant start = Instant.parse("2026-03-16T15:00:00Z");
		SlotSelectionSnapshot snapshot = snapshot(MatchingMode.PREFERRED_ONLY, start, start.plusSeconds(3600));
		TimefoldSlotSolver solver = new TimefoldSlotSolver(
				new TimefoldSolverConfiguration().slotSelectionSolverFactory());
		SlotSelectionResult result = solver.solve(snapshot, Instant.now().minusSeconds(5));
		assertThat(result.outcome()).isEqualTo("TIMEOUT");
		assertThat(result.selectedCandidate()).isNull();
		assertThat(result.completedNaturally()).isFalse();
	}

	@Test
	void preferredOnlyWithoutPreferredSlotReturnsNoPreferredMatch() {
		Instant start = Instant.parse("2026-03-16T15:00:00Z");
		SlotSelectionSnapshot snapshot = snapshot(MatchingMode.PREFERRED_ONLY, start, start.plusSeconds(7200));
		snapshot = new SlotSelectionSnapshot(snapshot.snapshotSchemaVersion(), snapshot.solverConfigurationVersion(),
				snapshot.requestId(), snapshot.requestRevisionId(), snapshot.requestRevisionVersion(), snapshot.mode(),
				snapshot.clinicZone(), snapshot.now(), snapshot.noticeBoundary(), snapshot.horizonEnd(),
				snapshot.durationMinutes(), snapshot.gridMinutes(), snapshot.configurationVersion(), snapshot.petId(),
				snapshot.requiredSpecialtyId(), snapshot.preferredVeterinarianId(),
				snapshot.veterinarianPreferenceStrength(), snapshot.allowedWindows(),
				List.of(new TimeWindow(start.plusSeconds(10 * 3600), start.plusSeconds(11 * 3600), false)),
				snapshot.excludedWindows(), snapshot.exclusionKeys(), snapshot.veterinarians(), snapshot.clinicHours(),
				snapshot.veterinarianHours(), snapshot.occupancies(),
				List.of(new CandidateSlot("1@" + start, 1, start, start.plusSeconds(1800), "FALLBACK", 0)));
		TimefoldSlotSolver solver = new TimefoldSlotSolver(
				new TimefoldSolverConfiguration().slotSelectionSolverFactory());
		SlotSelectionResult result = solver.solve(snapshot, Instant.now().plusSeconds(4));
		assertThat(result.outcome()).isEqualTo("NO_PREFERRED_MATCH");
		assertThat(result.selectedCandidate()).isNull();
	}

	private SlotSelectionSnapshot snapshot(MatchingMode mode, Instant start, Instant preferredStart) {
		Instant now = Instant.parse("2026-03-16T14:00:00Z");
		List<CandidateSlot> candidates = List
			.of(new CandidateSlot("1@" + start, 1, start, start.plusSeconds(1800), "STANDARD", 0), new CandidateSlot(
					"1@" + preferredStart, 1, preferredStart, preferredStart.plusSeconds(1800), "STANDARD", 1));
		return new SlotSelectionSnapshot("1.0", "slot-selection-1", 1L, 1L, 0, mode, "UTC", now,
				now.plusSeconds(15 * 60), now.plusSeconds(7 * 24 * 3600), 30, 15, 1L, 7, null, 1, "PREFERRED",
				List.of(new TimeWindow(start, preferredStart.plusSeconds(3600), false)),
				List.of(new TimeWindow(preferredStart, preferredStart.plusSeconds(3600), false)), List.of(), Set.of(),
				List.of(new VetFact(1, Set.of())),
				List.of(new HoursFact(null, DayOfWeek.MONDAY, LocalTime.of(0, 0), LocalTime.of(23, 59))), List.of(),
				List.of(), candidates);
	}

}
