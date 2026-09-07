package org.springframework.samples.petclinic.scheduling.matching;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.scheduling.matching.SlotRanker.RankedSlot;

import static org.assertj.core.api.Assertions.assertThat;

class SlotRankerTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 9, 14);

	@Test
	@Tag("AC-66")
	void ac66_outside_horizon_excluded() {
		int leadDays = 1;
		int horizonDays = 30;

		Slot sameDay = new Slot(1, TODAY, LocalTime.of(9, 0), LocalTime.of(9, 30));
		Slot beforeLead = new Slot(1, TODAY.minusDays(1), LocalTime.of(9, 0), LocalTime.of(9, 30));
		Slot tomorrow = new Slot(1, TODAY.plusDays(1), LocalTime.of(9, 0), LocalTime.of(9, 30));
		Slot dayThirty = new Slot(1, TODAY.plusDays(30), LocalTime.of(9, 0), LocalTime.of(9, 30));
		Slot beyondHorizon = new Slot(1, TODAY.plusDays(31), LocalTime.of(9, 0), LocalTime.of(9, 30));

		List<Slot> candidates = List.of(sameDay, beforeLead, tomorrow, dayThirty, beyondHorizon);

		List<RankedSlot> ranked = SlotRanker.rank(candidates, TODAY, leadDays, horizonDays, null, false, (v, d) -> 0);

		List<Slot> rankedSlots = ranked.stream().map(RankedSlot::slot).toList();
		assertThat(rankedSlots).contains(tomorrow, dayThirty);
		assertThat(rankedSlots).doesNotContain(sameDay, beforeLead, beyondHorizon);
	}

	@Test
	@Tag("AC-67")
	void ac67_lexicographic_order_is_deterministic() {
		LocalDate date1 = TODAY.plusDays(2);
		LocalDate date2 = TODAY.plusDays(3);

		// Tier 1: Preferred window before allowed window
		Slot preferredWin = new Slot(2, date1, LocalTime.of(10, 0), LocalTime.of(10, 30), WindowType.PREFERRED);
		Slot allowedWin = new Slot(2, date1, LocalTime.of(10, 0), LocalTime.of(10, 30), WindowType.ALLOWED);
		List<RankedSlot> tier1 = SlotRanker.rank(List.of(allowedWin, preferredWin), TODAY, 1, 30, null, false,
				(v, d) -> 0);
		assertThat(tier1.get(0).slot()).isEqualTo(preferredWin);

		// Tier 2: Preferred vet before other vet
		int preferredVetId = 1;
		Slot preferredVet = new Slot(1, date1, LocalTime.of(10, 0), LocalTime.of(10, 30), WindowType.PREFERRED);
		Slot otherVet = new Slot(2, date1, LocalTime.of(10, 0), LocalTime.of(10, 30), WindowType.PREFERRED);
		List<RankedSlot> tier2 = SlotRanker.rank(List.of(otherVet, preferredVet), TODAY, 1, 30, preferredVetId, true,
				(v, d) -> 0);
		assertThat(tier2.get(0).slot()).isEqualTo(preferredVet);

		// Tier 3: Earliest start (date, then time)
		Slot earlierTime = new Slot(2, date1, LocalTime.of(9, 0), LocalTime.of(9, 30), WindowType.ALLOWED);
		Slot laterTime = new Slot(2, date1, LocalTime.of(11, 0), LocalTime.of(11, 30), WindowType.ALLOWED);
		Slot laterDate = new Slot(2, date2, LocalTime.of(9, 0), LocalTime.of(9, 30), WindowType.ALLOWED);
		List<RankedSlot> tier3 = SlotRanker.rank(List.of(laterDate, laterTime, earlierTime), TODAY, 1, 30, null, false,
				(v, d) -> 0);
		assertThat(tier3.get(0).slot()).isEqualTo(earlierTime);
		assertThat(tier3.get(1).slot()).isEqualTo(laterTime);
		assertThat(tier3.get(2).slot()).isEqualTo(laterDate);

		// Tier 4: Fewest same-day CONFIRMED appointments
		Slot vetWithFewestAppts = new Slot(1, date1, LocalTime.of(10, 0), LocalTime.of(10, 30), WindowType.ALLOWED);
		Slot vetWithMoreAppts = new Slot(2, date1, LocalTime.of(10, 0), LocalTime.of(10, 30), WindowType.ALLOWED);
		Map<Integer, Integer> appts = Map.of(1, 0, 2, 3);
		List<RankedSlot> tier4 = SlotRanker.rank(List.of(vetWithMoreAppts, vetWithFewestAppts), TODAY, 1, 30, null,
				false, (v, d) -> appts.getOrDefault(v, 0));
		assertThat(tier4.get(0).slot()).isEqualTo(vetWithFewestAppts);

		// Tier 5: Lowest vet id
		Slot lowerVetId = new Slot(1, date1, LocalTime.of(10, 0), LocalTime.of(10, 30), WindowType.ALLOWED);
		Slot higherVetId = new Slot(2, date1, LocalTime.of(10, 0), LocalTime.of(10, 30), WindowType.ALLOWED);
		List<RankedSlot> tier5 = SlotRanker.rank(List.of(higherVetId, lowerVetId), TODAY, 1, 30, null, false,
				(v, d) -> 0);
		assertThat(tier5.get(0).slot()).isEqualTo(lowerVetId);

		// Deterministic stability under repeated shuffling
		List<Slot> allSlots = new ArrayList<>(List.of(preferredWin, allowedWin, preferredVet, otherVet, earlierTime,
				laterTime, laterDate, lowerVetId, higherVetId));
		List<Slot> expectedOrder = SlotRanker.rank(allSlots, TODAY, 1, 30, 1, true, (v, d) -> 0)
			.stream()
			.map(RankedSlot::slot)
			.toList();

		for (int i = 0; i < 10; i++) {
			List<Slot> shuffled = new ArrayList<>(allSlots);
			Collections.shuffle(shuffled);
			List<Slot> actualOrder = SlotRanker.rank(shuffled, TODAY, 1, 30, 1, true, (v, d) -> 0)
				.stream()
				.map(RankedSlot::slot)
				.toList();
			assertThat(actualOrder).isEqualTo(expectedOrder);
		}
	}

	@Test
	@Tag("AC-68")
	void ac68_ineligible_preferred_vet_not_honored() {
		LocalDate date = TODAY.plusDays(2);
		int preferredVetId = 1;
		boolean preferredVetEligible = false;

		Slot slotPreferredVet = new Slot(1, date, LocalTime.of(10, 0), LocalTime.of(10, 30), WindowType.PREFERRED);
		Slot slotOtherVet = new Slot(2, date, LocalTime.of(9, 0), LocalTime.of(9, 30), WindowType.PREFERRED);

		// When preferred vet is ineligible, preference is NOT honored (earlier start of
		// vet 2 wins)
		List<RankedSlot> ranked = SlotRanker.rank(List.of(slotPreferredVet, slotOtherVet), TODAY, 1, 30, preferredVetId,
				preferredVetEligible, (v, d) -> 0);

		assertThat(ranked.get(0).slot()).isEqualTo(slotOtherVet);
		assertThat(ranked.get(0).rankReason()).isEqualTo(RankReason.PREFERRED_WINDOW_PREFERRED_VET_UNAVAILABLE);
		assertThat(ranked.get(0).rankReason().getMessageKey()).isEqualTo("scheduling.slot.rank.preferred.unavailable");
	}

	@Test
	@Tag("AC-69")
	void ac69_rank_reason_is_correct_message_key() {
		Slot preferredSlot = new Slot(1, TODAY.plusDays(1), LocalTime.of(9, 0), LocalTime.of(9, 30),
				WindowType.PREFERRED);
		Slot allowedSlot = new Slot(1, TODAY.plusDays(1), LocalTime.of(9, 0), LocalTime.of(9, 30), WindowType.ALLOWED);

		// Preferred window + preferred vet honored
		RankReason r1 = RankReason.forSlot(preferredSlot, 1, true);
		assertThat(r1).isEqualTo(RankReason.PREFERRED_WINDOW_PREFERRED_VET);
		assertThat(r1.getMessageKey()).isEqualTo("scheduling.slot.rank.preferred.honored");

		// Preferred window + preferred vet unavailable
		RankReason r2 = RankReason.forSlot(preferredSlot, 2, false);
		assertThat(r2).isEqualTo(RankReason.PREFERRED_WINDOW_PREFERRED_VET_UNAVAILABLE);
		assertThat(r2.getMessageKey()).isEqualTo("scheduling.slot.rank.preferred.unavailable");

		// Preferred window + no vet requested
		RankReason r3 = RankReason.forSlot(preferredSlot, null, false);
		assertThat(r3).isEqualTo(RankReason.PREFERRED_WINDOW_NO_VET_REQUESTED);
		assertThat(r3.getMessageKey()).isEqualTo("scheduling.slot.rank.preferred.noPreference");

		// Allowed window + preferred vet honored
		RankReason r4 = RankReason.forSlot(allowedSlot, 1, true);
		assertThat(r4).isEqualTo(RankReason.ALLOWED_WINDOW_PREFERRED_VET);
		assertThat(r4.getMessageKey()).isEqualTo("scheduling.slot.rank.allowed.honored");

		// Allowed window + preferred vet unavailable
		RankReason r5 = RankReason.forSlot(allowedSlot, 2, false);
		assertThat(r5).isEqualTo(RankReason.ALLOWED_WINDOW_PREFERRED_VET_UNAVAILABLE);
		assertThat(r5.getMessageKey()).isEqualTo("scheduling.slot.rank.allowed.unavailable");

		// Allowed window + no vet requested
		RankReason r6 = RankReason.forSlot(allowedSlot, null, false);
		assertThat(r6).isEqualTo(RankReason.ALLOWED_WINDOW_NO_VET_REQUESTED);
		assertThat(r6.getMessageKey()).isEqualTo("scheduling.slot.rank.allowed.noPreference");

		// Staff selected
		RankReason r7 = RankReason.STAFF_SELECTED;
		assertThat(r7.getMessageKey()).isEqualTo("scheduling.slot.rank.staff");

		// Assert all keys are valid enumerated message keys starting with scheduling.
		for (RankReason reason : RankReason.values()) {
			assertThat(reason.getMessageKey()).startsWith("scheduling.slot.");
		}
	}

	@Test
	@Tag("AC-70")
	void ac70_in_bound_duration_is_verbatim() {
		int min = 15;
		int def = 30;
		int max = 60;

		DurationPolicy.DurationResult result30 = DurationPolicy.resolve(30, min, def, max);
		assertThat(result30.effectiveDuration()).isEqualTo(30);
		assertThat(result30.clamped()).isFalse();
		assertThat(result30.clampNoteKey()).isNull();

		DurationPolicy.DurationResult result45 = DurationPolicy.resolve(45, min, def, max);
		assertThat(result45.effectiveDuration()).isEqualTo(45);
		assertThat(result45.clamped()).isFalse();
		assertThat(result45.clampNoteKey()).isNull();

		Slot slot = new Slot(1, TODAY.plusDays(1), LocalTime.of(9, 0), LocalTime.of(9, 45));
		assertThat(slot.durationMinutes()).isEqualTo(45);
	}

}
