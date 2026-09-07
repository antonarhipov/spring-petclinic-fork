package org.springframework.samples.petclinic.scheduling.matching;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.scheduling.matching.FeasibilityChecker.ExistingAppointment;
import org.springframework.samples.petclinic.scheduling.matching.FeasibilityChecker.OpeningHours;
import org.springframework.samples.petclinic.scheduling.matching.FeasibilityChecker.Window;

import static org.assertj.core.api.Assertions.assertThat;

class FeasibilityCheckerTests {

	private static final LocalDate DATE = LocalDate.of(2026, 9, 14);

	@Test
	@Tag("AC-56")
	void ac56_only_15_minute_grid_starts() {
		assertThat(FeasibilityChecker.isGridAligned(LocalTime.of(9, 0))).isTrue();
		assertThat(FeasibilityChecker.isGridAligned(LocalTime.of(9, 15))).isTrue();
		assertThat(FeasibilityChecker.isGridAligned(LocalTime.of(9, 30))).isTrue();
		assertThat(FeasibilityChecker.isGridAligned(LocalTime.of(9, 45))).isTrue();

		assertThat(FeasibilityChecker.isGridAligned(LocalTime.of(8, 45))).isTrue();

		assertThat(FeasibilityChecker.isGridAligned(LocalTime.of(9, 1))).isFalse();
		assertThat(FeasibilityChecker.isGridAligned(LocalTime.of(8, 59))).isFalse();
		assertThat(FeasibilityChecker.isGridAligned(LocalTime.of(9, 14))).isFalse();
		assertThat(FeasibilityChecker.isGridAligned(LocalTime.of(9, 16))).isFalse();
		assertThat(FeasibilityChecker.isGridAligned(LocalTime.of(9, 29))).isFalse();
		assertThat(FeasibilityChecker.isGridAligned(LocalTime.of(9, 31))).isFalse();
		assertThat(FeasibilityChecker.isGridAligned(LocalTime.of(9, 44))).isFalse();
		assertThat(FeasibilityChecker.isGridAligned(LocalTime.of(9, 46))).isFalse();
		assertThat(FeasibilityChecker.isGridAligned(LocalTime.of(9, 0, 1))).isFalse();
	}

	@Test
	@Tag("AC-57")
	void ac57_inside_included_and_outside_excluded_windows() {
		List<Window> windows = List.of(Window.preferred(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0)),
				Window.allowed(DayOfWeek.MONDAY, LocalTime.of(13, 0), LocalTime.of(17, 0)),
				Window.excluded(DayOfWeek.MONDAY, LocalTime.of(10, 30), LocalTime.of(11, 0)));

		assertThat(FeasibilityChecker.checkWindowFeasibility(DATE, LocalTime.of(9, 0), LocalTime.of(9, 30), windows))
			.contains(WindowType.PREFERRED);
		assertThat(FeasibilityChecker.checkWindowFeasibility(DATE, LocalTime.of(9, 30), LocalTime.of(10, 30), windows))
			.contains(WindowType.PREFERRED);

		assertThat(FeasibilityChecker.checkWindowFeasibility(DATE, LocalTime.of(14, 0), LocalTime.of(15, 0), windows))
			.contains(WindowType.ALLOWED);

		assertThat(FeasibilityChecker.checkWindowFeasibility(DATE, LocalTime.of(8, 0), LocalTime.of(8, 30), windows))
			.isEmpty();
		assertThat(FeasibilityChecker.checkWindowFeasibility(DATE, LocalTime.of(12, 0), LocalTime.of(12, 30), windows))
			.isEmpty();
		assertThat(FeasibilityChecker.checkWindowFeasibility(DATE, LocalTime.of(17, 0), LocalTime.of(17, 30), windows))
			.isEmpty();

		assertThat(FeasibilityChecker.checkWindowFeasibility(DATE, LocalTime.of(8, 45), LocalTime.of(9, 15), windows))
			.isEmpty();
		assertThat(FeasibilityChecker.checkWindowFeasibility(DATE, LocalTime.of(11, 45), LocalTime.of(12, 15), windows))
			.isEmpty();

		assertThat(FeasibilityChecker.checkWindowFeasibility(DATE, LocalTime.of(10, 30), LocalTime.of(11, 0), windows))
			.isEmpty();
		assertThat(FeasibilityChecker.checkWindowFeasibility(DATE, LocalTime.of(10, 15), LocalTime.of(10, 45), windows))
			.isEmpty();
		assertThat(FeasibilityChecker.checkWindowFeasibility(DATE, LocalTime.of(10, 45), LocalTime.of(11, 15), windows))
			.isEmpty();
		assertThat(FeasibilityChecker.checkWindowFeasibility(DATE, LocalTime.of(10, 0), LocalTime.of(11, 30), windows))
			.isEmpty();
	}

	@Test
	@Tag("AC-58")
	void ac58_entire_slot_inside_opening_hours() {
		OpeningHours opening = new OpeningHours(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0));

		assertThat(FeasibilityChecker.isInsideOpeningHours(LocalTime.of(9, 0), LocalTime.of(9, 30), opening)).isTrue();
		assertThat(FeasibilityChecker.isInsideOpeningHours(LocalTime.of(16, 30), LocalTime.of(17, 0), opening))
			.isTrue();

		assertThat(FeasibilityChecker.isInsideOpeningHours(LocalTime.of(8, 59), LocalTime.of(9, 29), opening))
			.isFalse();

		assertThat(FeasibilityChecker.isInsideOpeningHours(LocalTime.of(16, 31), LocalTime.of(17, 1), opening))
			.isFalse();

		assertThat(FeasibilityChecker.isInsideOpeningHours(LocalTime.of(7, 0), LocalTime.of(8, 0), opening)).isFalse();
		assertThat(FeasibilityChecker.isInsideOpeningHours(LocalTime.of(17, 0), LocalTime.of(18, 0), opening))
			.isFalse();

		assertThat(FeasibilityChecker.isInsideOpeningHours(LocalTime.of(9, 0), LocalTime.of(9, 30), null)).isFalse();
	}

	@Test
	@Tag("AC-59")
	void ac59_entire_slot_inside_one_effective_block() {
		int vetId = 1;
		List<EffectiveAvailability> blocks = List.of(
				new EffectiveAvailability(vetId, DATE, LocalTime.of(9, 0), LocalTime.of(12, 0)),
				new EffectiveAvailability(vetId, DATE, LocalTime.of(13, 0), LocalTime.of(17, 0)));

		assertThat(
				FeasibilityChecker.isInsideEffectiveBlock(vetId, DATE, LocalTime.of(9, 0), LocalTime.of(9, 30), blocks))
			.isTrue();
		assertThat(FeasibilityChecker.isInsideEffectiveBlock(vetId, DATE, LocalTime.of(11, 30), LocalTime.of(12, 0),
				blocks))
			.isTrue();
		assertThat(FeasibilityChecker.isInsideEffectiveBlock(vetId, DATE, LocalTime.of(13, 0), LocalTime.of(13, 30),
				blocks))
			.isTrue();

		assertThat(FeasibilityChecker.isInsideEffectiveBlock(vetId, DATE, LocalTime.of(11, 45), LocalTime.of(12, 15),
				blocks))
			.isFalse();
		assertThat(FeasibilityChecker.isInsideEffectiveBlock(vetId, DATE, LocalTime.of(12, 0), LocalTime.of(13, 0),
				blocks))
			.isFalse();

		List<EffectiveAvailability> noBlocks = List.of();
		assertThat(FeasibilityChecker.isInsideEffectiveBlock(vetId, DATE, LocalTime.of(9, 0), LocalTime.of(9, 30),
				noBlocks))
			.isFalse();

		assertThat(FeasibilityChecker.isInsideEffectiveBlock(2, DATE, LocalTime.of(9, 0), LocalTime.of(9, 30), blocks))
			.isFalse();
		assertThat(FeasibilityChecker.isInsideEffectiveBlock(vetId, DATE.plusDays(1), LocalTime.of(9, 0),
				LocalTime.of(9, 30), blocks))
			.isFalse();
	}

	@Test
	@Tag("AC-60")
	void ac60_confirmed_and_held_overlap_excluded() {
		int vetId = 1;
		List<ExistingAppointment> appointments = List.of(
				new ExistingAppointment(vetId, DATE, LocalTime.of(10, 0), LocalTime.of(10, 30), "CONFIRMED"),
				new ExistingAppointment(vetId, DATE, LocalTime.of(14, 0), LocalTime.of(14, 30), "HELD"));

		assertThat(FeasibilityChecker.hasAppointmentOverlap(vetId, DATE, LocalTime.of(9, 30), LocalTime.of(10, 0),
				appointments))
			.isFalse();
		assertThat(FeasibilityChecker.hasAppointmentOverlap(vetId, DATE, LocalTime.of(10, 30), LocalTime.of(11, 0),
				appointments))
			.isFalse();
		assertThat(FeasibilityChecker.hasAppointmentOverlap(vetId, DATE, LocalTime.of(9, 45), LocalTime.of(10, 15),
				appointments))
			.isTrue();
		assertThat(FeasibilityChecker.hasAppointmentOverlap(vetId, DATE, LocalTime.of(10, 0), LocalTime.of(10, 30),
				appointments))
			.isTrue();
		assertThat(FeasibilityChecker.hasAppointmentOverlap(vetId, DATE, LocalTime.of(10, 15), LocalTime.of(10, 45),
				appointments))
			.isTrue();

		assertThat(FeasibilityChecker.hasAppointmentOverlap(vetId, DATE, LocalTime.of(13, 30), LocalTime.of(14, 0),
				appointments))
			.isFalse();
		assertThat(FeasibilityChecker.hasAppointmentOverlap(vetId, DATE, LocalTime.of(14, 30), LocalTime.of(15, 0),
				appointments))
			.isFalse();
		assertThat(FeasibilityChecker.hasAppointmentOverlap(vetId, DATE, LocalTime.of(13, 45), LocalTime.of(14, 15),
				appointments))
			.isTrue();
		assertThat(FeasibilityChecker.hasAppointmentOverlap(vetId, DATE, LocalTime.of(14, 0), LocalTime.of(14, 30),
				appointments))
			.isTrue();
		assertThat(FeasibilityChecker.hasAppointmentOverlap(vetId, DATE, LocalTime.of(14, 15), LocalTime.of(14, 45),
				appointments))
			.isTrue();

		List<ExistingAppointment> cancelled = List
			.of(new ExistingAppointment(vetId, DATE, LocalTime.of(10, 0), LocalTime.of(10, 30), "CANCELLED"));
		assertThat(FeasibilityChecker.hasAppointmentOverlap(vetId, DATE, LocalTime.of(10, 0), LocalTime.of(10, 30),
				cancelled))
			.isFalse();

		assertThat(FeasibilityChecker.hasAppointmentOverlap(2, DATE, LocalTime.of(10, 0), LocalTime.of(10, 30),
				appointments))
			.isFalse();
	}

}
