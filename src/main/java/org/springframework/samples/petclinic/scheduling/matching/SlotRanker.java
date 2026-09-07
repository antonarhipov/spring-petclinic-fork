package org.springframework.samples.petclinic.scheduling.matching;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntBiFunction;

public class SlotRanker {

	public record RankedSlot(Slot slot, RankReason rankReason) {
	}

	public static List<RankedSlot> rank(List<Slot> candidates, LocalDate today, int leadDays, int horizonDays,
			Integer preferredVetId, boolean preferredVetEligible,
			ToIntBiFunction<Integer, LocalDate> appointmentCountFn) {
		if (candidates == null || candidates.isEmpty()) {
			return List.of();
		}

		LocalDate minDate = today.plusDays(leadDays);
		LocalDate maxDate = today.plusDays(horizonDays);

		List<Slot> inHorizon = candidates.stream()
			.filter(s -> !s.date().isBefore(minDate) && !s.date().isAfter(maxDate))
			.toList();

		if (inHorizon.isEmpty()) {
			return List.of();
		}

		boolean honorPreferredVet = preferredVetId != null && preferredVetEligible;

		Comparator<Slot> comparator = Comparator
			.<Slot, Integer>comparing(s -> s.windowType() == WindowType.PREFERRED ? 0 : 1)
			.thenComparing(s -> (honorPreferredVet && s.vetId() == preferredVetId) ? 0 : 1)
			.thenComparing(Slot::date)
			.thenComparing(Slot::startTime)
			.thenComparingInt(s -> appointmentCountFn != null ? appointmentCountFn.applyAsInt(s.vetId(), s.date()) : 0)
			.thenComparingInt(Slot::vetId);

		List<Slot> sorted = new ArrayList<>(inHorizon);
		sorted.sort(comparator);

		List<RankedSlot> result = new ArrayList<>();
		for (Slot slot : sorted) {
			result.add(new RankedSlot(slot, RankReason.forSlot(slot, preferredVetId, preferredVetEligible)));
		}
		return result;
	}

}
