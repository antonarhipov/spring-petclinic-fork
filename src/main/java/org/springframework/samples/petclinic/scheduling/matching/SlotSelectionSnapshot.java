package org.springframework.samples.petclinic.scheduling.matching;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

public record SlotSelectionSnapshot(String snapshotSchemaVersion, String solverConfigurationVersion, Long requestId,
		Long requestRevisionId, Integer requestRevisionVersion, MatchingMode mode, String clinicZone, Instant now,
		Instant noticeBoundary, Instant horizonEnd, int durationMinutes, int gridMinutes, long configurationVersion,
		Integer petId, Integer requiredSpecialtyId, Integer preferredVeterinarianId,
		String veterinarianPreferenceStrength, List<TimeWindow> allowedWindows, List<TimeWindow> preferredWindows,
		List<TimeWindow> excludedWindows, Set<String> exclusionKeys, List<VetFact> veterinarians,
		List<HoursFact> clinicHours, List<HoursFact> veterinarianHours, List<ClosureFact> closures,
		List<VetLeaveFact> vetLeaves, List<VetDateExceptionFact> vetDateExceptions, List<OccupancyFact> occupancies,
		List<CandidateSlot> candidates) {

	public SlotSelectionSnapshot(String snapshotSchemaVersion, String solverConfigurationVersion, Long requestId,
			Long requestRevisionId, Integer requestRevisionVersion, MatchingMode mode, String clinicZone, Instant now,
			Instant noticeBoundary, Instant horizonEnd, int durationMinutes, int gridMinutes, long configurationVersion,
			Integer petId, Integer requiredSpecialtyId, Integer preferredVeterinarianId,
			String veterinarianPreferenceStrength, List<TimeWindow> allowedWindows, List<TimeWindow> preferredWindows,
			List<TimeWindow> excludedWindows, Set<String> exclusionKeys, List<VetFact> veterinarians,
			List<HoursFact> clinicHours, List<HoursFact> veterinarianHours, List<OccupancyFact> occupancies,
			List<CandidateSlot> candidates) {
		this(snapshotSchemaVersion, solverConfigurationVersion, requestId, requestRevisionId, requestRevisionVersion,
				mode, clinicZone, now, noticeBoundary, horizonEnd, durationMinutes, gridMinutes, configurationVersion,
				petId, requiredSpecialtyId, preferredVeterinarianId, veterinarianPreferenceStrength, allowedWindows,
				preferredWindows, excludedWindows, exclusionKeys, veterinarians, clinicHours, veterinarianHours,
				List.of(), List.of(), List.of(), occupancies, candidates);
	}

	public ZoneId zone() {
		return ZoneId.of(this.clinicZone);
	}

}
