package org.springframework.samples.petclinic.scheduling.matching;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class CandidateSlotFactory {

	public List<CandidateSlot> enumerate(SlotSelectionSnapshot templateWithoutCandidates) {
		List<CandidateSlot> slots = new ArrayList<>();
		int ordinal = 0;
		List<Instant> starts = new ArrayList<>();
		for (TimeWindow window : templateWithoutCandidates.allowedWindows()) {
			Instant cursor = alignUp(window.startAt(), templateWithoutCandidates.gridMinutes());
			while (!cursor.plus(Duration.ofMinutes(templateWithoutCandidates.durationMinutes())).isAfter(window.endAt())
					&& !cursor.isAfter(templateWithoutCandidates.horizonEnd())) {
				if (!cursor.isBefore(templateWithoutCandidates.noticeBoundary())) {
					starts.add(cursor);
				}
				cursor = cursor.plus(Duration.ofMinutes(templateWithoutCandidates.gridMinutes()));
			}
		}
		starts = starts.stream().distinct().sorted().toList();
		List<VetFact> vets = templateWithoutCandidates.veterinarians()
			.stream()
			.sorted(Comparator.comparingInt(VetFact::veterinarianId))
			.toList();
		for (Instant start : starts) {
			Instant end = start.plus(Duration.ofMinutes(templateWithoutCandidates.durationMinutes()));
			for (VetFact vet : vets) {
				String key = vet.veterinarianId() + "@" + start;
				if (templateWithoutCandidates.exclusionKeys().contains(key)) {
					continue;
				}
				String preference = classify(templateWithoutCandidates, vet.veterinarianId(), start, end);
				slots.add(new CandidateSlot(key, vet.veterinarianId(), start, end, preference, ordinal++));
			}
		}
		return List.copyOf(slots);
	}

	private Instant alignUp(Instant instant, int gridMinutes) {
		long grid = gridMinutes * 60L;
		long seconds = instant.getEpochSecond();
		long remainder = seconds % grid;
		if (remainder == 0) {
			return instant;
		}
		return Instant.ofEpochSecond(seconds + (grid - remainder));
	}

	private String classify(SlotSelectionSnapshot snapshot, int veterinarianId, Instant start, Instant end) {
		boolean windowOk = snapshot.preferredWindows().isEmpty() || snapshot.preferredWindows()
			.stream()
			.anyMatch(window -> !start.isBefore(window.startAt()) && !end.isAfter(window.endAt()));
		boolean vetOk = snapshot.preferredVeterinarianId() == null
				|| "NONE".equals(snapshot.veterinarianPreferenceStrength())
				|| snapshot.preferredVeterinarianId() == veterinarianId;
		return windowOk && vetOk ? "STANDARD" : "FALLBACK";
	}

}
