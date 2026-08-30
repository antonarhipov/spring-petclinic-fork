package org.springframework.samples.petclinic.scheduling.matching;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.scheduling.appointment.ReservationResourceType;

import static org.assertj.core.api.Assertions.assertThat;

class SlotScorePolicyTests {

	@Test
	void preferredWindowAndVeterinarianOutrankEarliestStart() {
		Instant start = Instant.parse("2026-03-16T15:00:00Z");
		Instant preferredStart = Instant.parse("2026-03-16T16:00:00Z");
		SlotSelectionSnapshot snapshot = snapshot(MatchingMode.PREFERRED_ONLY,
				List.of(new TimeWindow(start, start.plusSeconds(4 * 3600), false)),
				List.of(new TimeWindow(preferredStart, preferredStart.plusSeconds(3600), false)), 1, "PREFERRED");
		CandidateSlot early = new CandidateSlot("1@" + start, 1, start, start.plusSeconds(1800), "STANDARD", 0);
		CandidateSlot preferred = new CandidateSlot("1@" + preferredStart, 1, preferredStart,
				preferredStart.plusSeconds(1800), "STANDARD", 1);
		assertThat(SlotScorePolicy.score(snapshot, preferred)
			.score()
			.compareTo(SlotScorePolicy.score(snapshot, early).score())).isPositive();
		assertThat(SlotScorePolicy.score(snapshot, preferred).publicExplanationCode())
			.isEqualTo("PREFERRED_TIME_AND_VETERINARIAN");
	}

	@Test
	void equalStartsPreferPackedVeterinarian() {
		Instant start = Instant.parse("2026-03-16T15:00:00Z");
		SlotSelectionSnapshot packed = snapshot(MatchingMode.PREFERRED_ONLY,
				List.of(new TimeWindow(start, start.plusSeconds(3600), false)), List.of(), 1, "NONE");
		packed = withOccupancy(packed,
				new OccupancyFact(ReservationResourceType.VETERINARIAN, 1, start.minusSeconds(900)));
		CandidateSlot slot = new CandidateSlot("1@" + start, 1, start, start.plusSeconds(1800), "STANDARD", 0);
		SlotSelectionSnapshot empty = snapshot(MatchingMode.PREFERRED_ONLY,
				List.of(new TimeWindow(start, start.plusSeconds(3600), false)), List.of(), 1, "NONE");
		assertThat(SlotScorePolicy.score(packed, slot).score().softScore(2))
			.isGreaterThan(SlotScorePolicy.score(empty, slot).score().softScore(2));
	}

	@Test
	void preferredOnlyRejectsFallbackClass() {
		Instant start = Instant.parse("2026-03-16T15:00:00Z");
		SlotSelectionSnapshot snapshot = snapshot(MatchingMode.PREFERRED_ONLY,
				List.of(new TimeWindow(start, start.plusSeconds(3600), false)),
				List.of(new TimeWindow(start.plusSeconds(7200), start.plusSeconds(10800), false)), 2, "PREFERRED");
		CandidateSlot fallback = new CandidateSlot("1@" + start, 1, start, start.plusSeconds(1800), "FALLBACK", 0);
		assertThat(SlotScorePolicy.score(snapshot, fallback).score().hardScore(0)).isNegative();
	}

	@Test
	void namedHardComponentsCoverEligibility() {
		Instant start = Instant.parse("2026-03-16T15:00:00Z");
		SlotSelectionSnapshot snapshot = snapshot(MatchingMode.PREFERRED_ONLY,
				List.of(new TimeWindow(start, start.plusSeconds(3600), false)), List.of(), 1, "NONE");
		CandidateSlot slot = new CandidateSlot("1@" + start, 1, start, start.plusSeconds(1800), "STANDARD", 0);
		assertThat(SlotScorePolicy.score(snapshot, slot).components())
			.extracting(SlotScoreComponents.NamedComponent::name)
			.contains("grid-alignment", "notice-horizon", "allowed-window", "clinic-open", "veterinarian-working",
					"specialty-requirement", "overlap", "preferred-tier");
	}

	private SlotSelectionSnapshot snapshot(MatchingMode mode, List<TimeWindow> allowed, List<TimeWindow> preferred,
			int preferredVet, String strength) {
		Instant now = Instant.parse("2026-03-16T14:00:00Z");
		CandidateSlot unused = new CandidateSlot("x", 1, now, now.plusSeconds(1800), "STANDARD", 0);
		return new SlotSelectionSnapshot("1.0", "slot-selection-1", 1L, 1L, 0, mode, "UTC", now,
				now.plusSeconds(15 * 60), now.plusSeconds(7 * 24 * 3600), 30, 15, 1L, 7, null, preferredVet, strength,
				allowed, preferred, List.of(), Set.of(), List.of(new VetFact(1, Set.of()), new VetFact(2, Set.of())),
				List.of(new HoursFact(null, DayOfWeek.MONDAY, LocalTime.of(0, 0), LocalTime.of(23, 59))), List.of(),
				List.of(), List.of(unused));
	}

	private SlotSelectionSnapshot withOccupancy(SlotSelectionSnapshot source, OccupancyFact occupancy) {
		return new SlotSelectionSnapshot(source.snapshotSchemaVersion(), source.solverConfigurationVersion(),
				source.requestId(), source.requestRevisionId(), source.requestRevisionVersion(), source.mode(),
				source.clinicZone(), source.now(), source.noticeBoundary(), source.horizonEnd(),
				source.durationMinutes(), source.gridMinutes(), source.configurationVersion(), source.petId(),
				source.requiredSpecialtyId(), source.preferredVeterinarianId(), source.veterinarianPreferenceStrength(),
				source.allowedWindows(), source.preferredWindows(), source.excludedWindows(), source.exclusionKeys(),
				source.veterinarians(), source.clinicHours(), source.veterinarianHours(), List.of(occupancy),
				source.candidates());
	}

}
