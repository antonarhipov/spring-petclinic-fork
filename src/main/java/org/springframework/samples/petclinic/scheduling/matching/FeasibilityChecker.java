package org.springframework.samples.petclinic.scheduling.matching;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public class FeasibilityChecker {

	public record Window(WindowType type, DayOfWeek weekday, LocalDate date, LocalTime start, LocalTime end) {

		public static Window preferred(DayOfWeek weekday, LocalTime start, LocalTime end) {
			return new Window(WindowType.PREFERRED, weekday, null, start, end);
		}

		public static Window preferred(LocalDate date, LocalTime start, LocalTime end) {
			return new Window(WindowType.PREFERRED, null, date, start, end);
		}

		public static Window allowed(DayOfWeek weekday, LocalTime start, LocalTime end) {
			return new Window(WindowType.ALLOWED, weekday, null, start, end);
		}

		public static Window allowed(LocalDate date, LocalTime start, LocalTime end) {
			return new Window(WindowType.ALLOWED, null, date, start, end);
		}

		public static Window excluded(DayOfWeek weekday, LocalTime start, LocalTime end) {
			return new Window(WindowType.EXCLUDED, weekday, null, start, end);
		}

		public static Window excluded(LocalDate date, LocalTime start, LocalTime end) {
			return new Window(WindowType.EXCLUDED, null, date, start, end);
		}

		public boolean appliesTo(LocalDate targetDate) {
			if (this.weekday != null && this.weekday != targetDate.getDayOfWeek()) {
				return false;
			}
			if (this.date != null && !this.date.equals(targetDate)) {
				return false;
			}
			return true;
		}

		public boolean contains(LocalDate targetDate, LocalTime startTime, LocalTime endTime) {
			return appliesTo(targetDate) && !startTime.isBefore(this.start) && !endTime.isAfter(this.end);
		}

		public boolean overlaps(LocalDate targetDate, LocalTime startTime, LocalTime endTime) {
			return appliesTo(targetDate) && startTime.isBefore(this.end) && endTime.isAfter(this.start);
		}

	}

	public record ExistingAppointment(int vetId, LocalDate date, LocalTime startTime, LocalTime endTime,
			String status) {

		public boolean isBlocking() {
			return "CONFIRMED".equalsIgnoreCase(this.status) || "HELD".equalsIgnoreCase(this.status);
		}

		public boolean overlaps(int targetVetId, LocalDate targetDate, LocalTime start, LocalTime end) {
			return this.vetId == targetVetId && this.date.equals(targetDate) && isBlocking()
					&& start.isBefore(this.endTime) && end.isAfter(this.startTime);
		}

	}

	public record OpeningHours(DayOfWeek weekday, LocalTime openTime, LocalTime closeTime) {

		public boolean contains(LocalTime start, LocalTime end) {
			return this.openTime != null && this.closeTime != null && !start.isBefore(this.openTime)
					&& !end.isAfter(this.closeTime);
		}

	}

	public record VetInfo(int id, List<String> specialties) {

		public boolean hasSpecialty(String specialty) {
			if (specialty == null || this.specialties == null) {
				return false;
			}
			return this.specialties.stream().anyMatch(s -> s.equalsIgnoreCase(specialty));
		}

	}

	public record RejectedSlot(int vetId, LocalDate date, LocalTime startTime) {

		public static RejectedSlot of(int vetId, LocalDate date, LocalTime startTime) {
			return new RejectedSlot(vetId, date, startTime);
		}

	}

	public static boolean isGridAligned(LocalTime time) {
		return time != null && time.getMinute() % 15 == 0 && time.getSecond() == 0 && time.getNano() == 0;
	}

	public static LocalTime roundUpToGrid(LocalTime time) {
		int minute = time.getMinute();
		int remainder = minute % 15;
		if (remainder == 0 && time.getSecond() == 0 && time.getNano() == 0) {
			return time;
		}
		int minutesToAdd = 15 - remainder;
		return time.plusMinutes(minutesToAdd).withSecond(0).withNano(0);
	}

	public static boolean isRejected(int vetId, LocalDate date, LocalTime startTime, List<RejectedSlot> rejections) {
		if (rejections == null) {
			return false;
		}
		for (RejectedSlot r : rejections) {
			if (r.vetId() == vetId && r.date().equals(date) && r.startTime().equals(startTime)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isVetEligible(VetInfo vet, String careType, String requiredSpecialty) {
		if ("SPECIALTY".equalsIgnoreCase(careType)) {
			return vet.hasSpecialty(requiredSpecialty);
		}
		return true;
	}

	public static boolean isInsideOpeningHours(LocalTime start, LocalTime end, OpeningHours openingHours) {
		return openingHours != null && openingHours.contains(start, end);
	}

	public static boolean isInsideEffectiveBlock(int vetId, LocalDate date, LocalTime start, LocalTime end,
			List<EffectiveAvailability> effectiveBlocks) {
		if (effectiveBlocks == null) {
			return false;
		}
		for (EffectiveAvailability block : effectiveBlocks) {
			if (block.vetId() == vetId && block.date().equals(date) && block.contains(start, end)) {
				return true;
			}
		}
		return false;
	}

	public static Optional<WindowType> checkWindowFeasibility(LocalDate date, LocalTime start, LocalTime end,
			List<Window> windows) {
		if (windows == null || windows.isEmpty()) {
			return Optional.empty();
		}
		for (Window window : windows) {
			if (window.type() == WindowType.EXCLUDED && window.overlaps(date, start, end)) {
				return Optional.empty();
			}
		}
		boolean inPreferred = false;
		boolean inAllowed = false;
		for (Window window : windows) {
			if (window.type() == WindowType.PREFERRED && window.contains(date, start, end)) {
				inPreferred = true;
				break;
			}
			if (window.type() == WindowType.ALLOWED && window.contains(date, start, end)) {
				inAllowed = true;
			}
		}
		if (inPreferred) {
			return Optional.of(WindowType.PREFERRED);
		}
		if (inAllowed) {
			return Optional.of(WindowType.ALLOWED);
		}
		return Optional.empty();
	}

	public static boolean hasAppointmentOverlap(int vetId, LocalDate date, LocalTime start, LocalTime end,
			List<ExistingAppointment> appointments) {
		if (appointments == null) {
			return false;
		}
		for (ExistingAppointment app : appointments) {
			if (app.overlaps(vetId, date, start, end)) {
				return true;
			}
		}
		return false;
	}

	public static Optional<Slot> evaluateSlot(int vetId, LocalDate date, LocalTime start, LocalTime end,
			OpeningHours openingHours, List<EffectiveAvailability> effectiveBlocks, List<Window> windows,
			List<ExistingAppointment> appointments) {
		if (!isGridAligned(start)) {
			return Optional.empty();
		}
		if (!isInsideOpeningHours(start, end, openingHours)) {
			return Optional.empty();
		}
		if (!isInsideEffectiveBlock(vetId, date, start, end, effectiveBlocks)) {
			return Optional.empty();
		}
		Optional<WindowType> windowType = checkWindowFeasibility(date, start, end, windows);
		if (windowType.isEmpty()) {
			return Optional.empty();
		}
		if (hasAppointmentOverlap(vetId, date, start, end, appointments)) {
			return Optional.empty();
		}
		return Optional.of(new Slot(vetId, date, start, end, windowType.get()));
	}

	public static List<Slot> findCandidates(LocalDate today, int leadDays, int horizonDays, int durationMinutes,
			String careType, String requiredSpecialty, List<VetInfo> vets, List<Window> windows,
			List<RejectedSlot> rejections,
			java.util.function.Function<LocalDate, Optional<OpeningHours>> openingHoursFn,
			java.util.function.BiFunction<Integer, LocalDate, List<EffectiveAvailability>> availabilityFn,
			List<ExistingAppointment> appointments) {
		if (windows == null || windows.isEmpty() || vets == null || vets.isEmpty()) {
			return List.of();
		}

		LocalDate startDate = today.plusDays(leadDays);
		LocalDate endDate = today.plusDays(horizonDays);
		List<Slot> candidates = new java.util.ArrayList<>();

		LocalDate currentDate = startDate;
		while (!currentDate.isAfter(endDate)) {
			Optional<OpeningHours> openingOpt = openingHoursFn.apply(currentDate);
			if (openingOpt.isPresent() && openingOpt.get().openTime() != null && openingOpt.get().closeTime() != null) {
				OpeningHours opening = openingOpt.get();
				LocalTime openTime = opening.openTime();
				LocalTime closeTime = opening.closeTime();

				for (VetInfo vet : vets) {
					if (!isVetEligible(vet, careType, requiredSpecialty)) {
						continue;
					}

					List<EffectiveAvailability> blocks = availabilityFn.apply(vet.id(), currentDate);
					if (blocks == null || blocks.isEmpty()) {
						continue;
					}

					LocalTime slotStart = roundUpToGrid(openTime);
					while (!slotStart.plusMinutes(durationMinutes).isAfter(closeTime)
							&& !slotStart.plusMinutes(durationMinutes).isBefore(slotStart)) {
						LocalTime slotEnd = slotStart.plusMinutes(durationMinutes);

						if (!isRejected(vet.id(), currentDate, slotStart, rejections)) {
							Optional<Slot> slot = evaluateSlot(vet.id(), currentDate, slotStart, slotEnd, opening,
									blocks, windows, appointments);
							slot.ifPresent(candidates::add);
						}

						slotStart = slotStart.plusMinutes(15);
					}
				}
			}
			currentDate = currentDate.plusDays(1);
		}

		return candidates;
	}

}
