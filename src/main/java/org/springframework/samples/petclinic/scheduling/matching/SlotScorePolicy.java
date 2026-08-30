package org.springframework.samples.petclinic.scheduling.matching;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.samples.petclinic.scheduling.appointment.ReservationResourceType;

import ai.timefold.solver.core.api.score.BendableScore;

public final class SlotScorePolicy {

	private SlotScorePolicy() {
	}

	public static SlotScoreComponents score(SlotSelectionSnapshot snapshot, CandidateSlot slot) {
		List<SlotScoreComponents.NamedComponent> components = new ArrayList<>();
		if (slot == null) {
			components.add(hard("missing-assignment", -1));
			return new SlotScoreComponents(BendableScore.of(new long[] { -1 }, new long[] { 0, 0, 0, 0 }), "NONE",
					List.copyOf(components));
		}
		int hard = 0;
		hard += addHard(components, "missing-assignment", 0);
		hard += addHard(components, "grid-alignment", gridAligned(snapshot, slot) ? 0 : -1);
		hard += addHard(components, "notice-horizon", insideNoticeAndHorizon(snapshot, slot) ? 0 : -1);
		hard += addHard(components, "allowed-window", insideAllowedAndNotExcluded(snapshot, slot) ? 0 : -1);
		hard += addHard(components, "clinic-open", clinicOpen(snapshot, slot) ? 0 : -1);
		hard += addHard(components, "veterinarian-working", veterinarianWorking(snapshot, slot) ? 0 : -1);
		hard += addHard(components, "specialty-requirement", specialtyAndRequiredVet(snapshot, slot) ? 0 : -1);
		hard += addHard(components, "overlap", !overlaps(snapshot, slot) ? 0 : -1);
		boolean preferredTier = "STANDARD".equals(slot.preferenceClass());
		int preferredHard = snapshot.mode() == MatchingMode.PREFERRED_ONLY && !preferredTier ? -1 : 0;
		hard += addHard(components, "preferred-tier", preferredHard);

		boolean preferredWindow = matchesPreferredWindow(snapshot, slot);
		boolean preferredVet = matchesPreferredVeterinarian(snapshot, slot);
		int soft0 = (preferredWindow ? 1 : 0) + (preferredVet ? 1 : 0);
		components.add(new SlotScoreComponents.NamedComponent("preferred-window", "soft-0", preferredWindow ? 1 : 0));
		components
			.add(new SlotScoreComponents.NamedComponent("preferred-veterinarian", "soft-0", preferredVet ? 1 : 0));
		int earliest = (int) -Duration.between(snapshot.noticeBoundary(), slot.startAt()).toMinutes();
		components.add(new SlotScoreComponents.NamedComponent("earliest-start", "soft-1", earliest));
		int efficiency = clinicEfficiency(snapshot, slot);
		components.add(new SlotScoreComponents.NamedComponent("clinic-efficiency", "soft-2", efficiency));
		int stable = -slot.stableOrdinal();
		components.add(new SlotScoreComponents.NamedComponent("stable-ordinal", "soft-3", stable));
		BendableScore score = BendableScore.of(new long[] { hard }, new long[] { soft0, earliest, efficiency, stable });
		return new SlotScoreComponents(score, explanation(preferredWindow, preferredVet, slot),
				List.copyOf(components));
	}

	public static boolean baseEligible(SlotSelectionSnapshot snapshot, CandidateSlot slot) {
		if (slot == null) {
			return false;
		}
		return gridAligned(snapshot, slot) && insideNoticeAndHorizon(snapshot, slot)
				&& insideAllowedAndNotExcluded(snapshot, slot) && clinicOpen(snapshot, slot)
				&& veterinarianWorking(snapshot, slot) && specialtyAndRequiredVet(snapshot, slot)
				&& !overlaps(snapshot, slot);
	}

	private static int addHard(List<SlotScoreComponents.NamedComponent> components, String name, int value) {
		components.add(hard(name, value));
		return value;
	}

	private static SlotScoreComponents.NamedComponent hard(String name, int value) {
		return new SlotScoreComponents.NamedComponent(name, "hard-0", value);
	}

	private static boolean gridAligned(SlotSelectionSnapshot snapshot, CandidateSlot slot) {
		int grid = snapshot.gridMinutes();
		return aligned(slot.startAt(), grid) && aligned(slot.endAt(), grid)
				&& Duration.between(slot.startAt(), slot.endAt()).toMinutes() == snapshot.durationMinutes();
	}

	private static boolean aligned(Instant instant, int grid) {
		return instant.getEpochSecond() % (grid * 60L) == 0;
	}

	private static boolean insideNoticeAndHorizon(SlotSelectionSnapshot snapshot, CandidateSlot slot) {
		return !slot.startAt().isBefore(snapshot.noticeBoundary()) && !slot.endAt().isAfter(snapshot.horizonEnd());
	}

	private static boolean insideAllowedAndNotExcluded(SlotSelectionSnapshot snapshot, CandidateSlot slot) {
		boolean allowed = snapshot.allowedWindows()
			.stream()
			.anyMatch(window -> !slot.startAt().isBefore(window.startAt()) && !slot.endAt().isAfter(window.endAt()));
		boolean excluded = snapshot.excludedWindows()
			.stream()
			.anyMatch(window -> slot.startAt().isBefore(window.endAt()) && slot.endAt().isAfter(window.startAt()));
		boolean keyExcluded = snapshot.exclusionKeys().contains(slot.veterinarianId() + "@" + slot.startAt());
		return allowed && !excluded && !keyExcluded;
	}

	private static boolean clinicOpen(SlotSelectionSnapshot snapshot, CandidateSlot slot) {
		return coveredByHours(snapshot.clinicHours().stream().filter(h -> h.veterinarianId() == null).toList(),
				snapshot.zone(), slot);
	}

	private static boolean veterinarianWorking(SlotSelectionSnapshot snapshot, CandidateSlot slot) {
		List<HoursFact> vetHours = snapshot.veterinarianHours()
			.stream()
			.filter(h -> h.veterinarianId() != null && h.veterinarianId() == slot.veterinarianId())
			.toList();
		if (vetHours.isEmpty()) {
			return clinicOpen(snapshot, slot);
		}
		return coveredByHours(vetHours, snapshot.zone(), slot);
	}

	private static boolean coveredByHours(List<HoursFact> hours, ZoneId zone, CandidateSlot slot) {
		Instant cursor = slot.startAt();
		while (cursor.isBefore(slot.endAt())) {
			LocalDateTime local = LocalDateTime.ofInstant(cursor, zone);
			boolean open = hours.stream()
				.anyMatch(h -> h.dayOfWeek() == local.getDayOfWeek() && !local.toLocalTime().isBefore(h.startLocal())
						&& local.toLocalTime().isBefore(h.endLocal()));
			if (!open) {
				return false;
			}
			cursor = cursor.plus(Duration.ofMinutes(15));
		}
		return true;
	}

	private static boolean specialtyAndRequiredVet(SlotSelectionSnapshot snapshot, CandidateSlot slot) {
		VetFact vet = snapshot.veterinarians()
			.stream()
			.filter(item -> item.veterinarianId() == slot.veterinarianId())
			.findFirst()
			.orElse(null);
		if (vet == null) {
			return false;
		}
		if (snapshot.requiredSpecialtyId() != null && !vet.specialtyIds().contains(snapshot.requiredSpecialtyId())) {
			return false;
		}
		return !"REQUIRED".equals(snapshot.veterinarianPreferenceStrength())
				|| snapshot.preferredVeterinarianId() == null
				|| snapshot.preferredVeterinarianId() == slot.veterinarianId();
	}

	private static boolean overlaps(SlotSelectionSnapshot snapshot, CandidateSlot slot) {
		Instant cursor = slot.startAt();
		while (cursor.isBefore(slot.endAt())) {
			Instant block = cursor;
			boolean vetBusy = snapshot.occupancies()
				.stream()
				.anyMatch(o -> o.resourceType() == ReservationResourceType.VETERINARIAN
						&& o.resourceId() == slot.veterinarianId() && o.blockStart().equals(block));
			boolean petBusy = snapshot.occupancies()
				.stream()
				.anyMatch(o -> o.resourceType() == ReservationResourceType.PET && o.resourceId() == snapshot.petId()
						&& o.blockStart().equals(block));
			if (vetBusy || petBusy) {
				return true;
			}
			cursor = cursor.plus(Duration.ofMinutes(15));
		}
		return false;
	}

	private static boolean matchesPreferredWindow(SlotSelectionSnapshot snapshot, CandidateSlot slot) {
		if (snapshot.preferredWindows().isEmpty()) {
			return false;
		}
		return snapshot.preferredWindows()
			.stream()
			.anyMatch(window -> !slot.startAt().isBefore(window.startAt()) && !slot.endAt().isAfter(window.endAt()));
	}

	private static boolean matchesPreferredVeterinarian(SlotSelectionSnapshot snapshot, CandidateSlot slot) {
		return snapshot.preferredVeterinarianId() != null && snapshot.preferredVeterinarianId() == slot.veterinarianId()
				&& !"NONE".equals(snapshot.veterinarianPreferenceStrength());
	}

	private static int clinicEfficiency(SlotSelectionSnapshot snapshot, CandidateSlot slot) {
		long bestGap = Long.MAX_VALUE;
		for (OccupancyFact occupancy : snapshot.occupancies()) {
			if (occupancy.resourceType() != ReservationResourceType.VETERINARIAN
					|| occupancy.resourceId() != slot.veterinarianId()) {
				continue;
			}
			long before = Duration.between(occupancy.blockStart().plus(Duration.ofMinutes(15)), slot.startAt())
				.toMinutes();
			long after = Duration.between(slot.endAt(), occupancy.blockStart()).toMinutes();
			if (before >= 0) {
				bestGap = Math.min(bestGap, before);
			}
			if (after >= 0) {
				bestGap = Math.min(bestGap, after);
			}
		}
		if (bestGap == Long.MAX_VALUE) {
			return -10_000;
		}
		return (int) -Math.min(bestGap, 10_000);
	}

	private static String explanation(boolean preferredWindow, boolean preferredVet, CandidateSlot slot) {
		if (preferredWindow && preferredVet) {
			return "PREFERRED_TIME_AND_VETERINARIAN";
		}
		if (preferredWindow) {
			return "PREFERRED_TIME";
		}
		if (preferredVet) {
			return "PREFERRED_VETERINARIAN";
		}
		if ("FALLBACK".equals(slot.preferenceClass())) {
			return "FALLBACK_SLOT";
		}
		return "EARLIEST_AVAILABLE";
	}

}
