package org.springframework.samples.petclinic.scheduling.matching;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.scheduling.matching.FeasibilityChecker.OpeningHours;
import org.springframework.samples.petclinic.scheduling.matching.FeasibilityChecker.RejectedSlot;
import org.springframework.samples.petclinic.scheduling.matching.FeasibilityChecker.VetInfo;
import org.springframework.samples.petclinic.scheduling.matching.FeasibilityChecker.Window;

import static org.assertj.core.api.Assertions.assertThat;

class FeasibilityBoundaryTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 9, 14); // Monday

	private final OpeningHours standardOpening = new OpeningHours(null, LocalTime.of(9, 0), LocalTime.of(17, 0));

	private Optional<OpeningHours> defaultOpeningHours(LocalDate date) {
		if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
			return Optional.empty();
		}
		return Optional.of(this.standardOpening);
	}

	private List<EffectiveAvailability> defaultAvailability(int vetId, LocalDate date) {
		if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
			return List.of();
		}
		return List.of(new EffectiveAvailability(vetId, date, LocalTime.of(9, 0), LocalTime.of(17, 0)));
	}

	@Test
	@Tag("AC-61")
	void ac61_rejected_vet_start_excluded() {
		LocalDate tomorrow = TODAY.plusDays(1);
		List<VetInfo> vets = List.of(new VetInfo(1, List.of("general")), new VetInfo(2, List.of("general")));
		List<Window> windows = List.of(Window.preferred(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(10, 0)));
		List<RejectedSlot> rejections = List.of(RejectedSlot.of(1, tomorrow, LocalTime.of(9, 0)));

		List<Slot> candidates = FeasibilityChecker.findCandidates(TODAY, 1, 30, 30, "GENERAL", null, vets, windows,
				rejections, this::defaultOpeningHours, this::defaultAvailability, List.of());

		assertThat(candidates)
			.noneMatch(s -> s.vetId() == 1 && s.date().equals(tomorrow) && s.startTime().equals(LocalTime.of(9, 0)));
		assertThat(candidates)
			.anyMatch(s -> s.vetId() == 1 && s.date().equals(tomorrow) && s.startTime().equals(LocalTime.of(9, 15)));
		assertThat(candidates)
			.anyMatch(s -> s.vetId() == 2 && s.date().equals(tomorrow) && s.startTime().equals(LocalTime.of(9, 0)));
	}

	@Test
	@Tag("AC-62")
	void ac62_specialty_required_only_for_specialty_care() {
		List<VetInfo> vets = List.of(new VetInfo(1, List.of("surgery")), new VetInfo(2, List.of("radiology")),
				new VetInfo(3, List.of()));
		List<Window> windows = List.of(Window.preferred(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(12, 0)));

		List<Slot> specialtySlots = FeasibilityChecker.findCandidates(TODAY, 1, 30, 30, "SPECIALTY", "surgery", vets,
				windows, List.of(), this::defaultOpeningHours, this::defaultAvailability, List.of());
		assertThat(specialtySlots).isNotEmpty();
		assertThat(specialtySlots).allMatch(s -> s.vetId() == 1);

		List<Slot> generalSlots = FeasibilityChecker.findCandidates(TODAY, 1, 30, 30, "GENERAL", null, vets, windows,
				List.of(), this::defaultOpeningHours, this::defaultAvailability, List.of());
		assertThat(generalSlots).isNotEmpty();
		assertThat(generalSlots).anyMatch(s -> s.vetId() == 1);
		assertThat(generalSlots).anyMatch(s -> s.vetId() == 2);
		assertThat(generalSlots).anyMatch(s -> s.vetId() == 3);
	}

	@Test
	@Tag("AC-63")
	void ac63_semantic_bad_windows_return_empty_not_failure() {
		List<VetInfo> vets = List.of(new VetInfo(1, List.of("general")));

		List<Window> closedWeekday = List
			.of(Window.preferred(DayOfWeek.SUNDAY, LocalTime.of(9, 0), LocalTime.of(12, 0)));
		List<Slot> fromClosed = FeasibilityChecker.findCandidates(TODAY, 1, 30, 30, "GENERAL", null, vets,
				closedWeekday, List.of(), this::defaultOpeningHours, this::defaultAvailability, List.of());
		assertThat(fromClosed).isEmpty();

		List<Window> pastDate = List.of(Window.preferred(TODAY.minusDays(5), LocalTime.of(9, 0), LocalTime.of(12, 0)));
		List<Slot> fromPast = FeasibilityChecker.findCandidates(TODAY, 1, 30, 30, "GENERAL", null, vets, pastDate,
				List.of(), this::defaultOpeningHours, this::defaultAvailability, List.of());
		assertThat(fromPast).isEmpty();

		List<Window> beyondHorizon = List
			.of(Window.preferred(TODAY.plusDays(40), LocalTime.of(9, 0), LocalTime.of(12, 0)));
		List<Slot> fromBeyond = FeasibilityChecker.findCandidates(TODAY, 1, 30, 30, "GENERAL", null, vets,
				beyondHorizon, List.of(), this::defaultOpeningHours, this::defaultAvailability, List.of());
		assertThat(fromBeyond).isEmpty();

		List<Window> invertedTimes = List
			.of(Window.preferred(DayOfWeek.TUESDAY, LocalTime.of(12, 0), LocalTime.of(10, 0)));
		List<Slot> fromInverted = FeasibilityChecker.findCandidates(TODAY, 1, 30, 30, "GENERAL", null, vets,
				invertedTimes, List.of(), this::defaultOpeningHours, this::defaultAvailability, List.of());
		assertThat(fromInverted).isEmpty();

		List<Window> equalTimes = List
			.of(Window.preferred(DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(10, 0)));
		List<Slot> fromEqual = FeasibilityChecker.findCandidates(TODAY, 1, 30, 30, "GENERAL", null, vets, equalTimes,
				List.of(), this::defaultOpeningHours, this::defaultAvailability, List.of());
		assertThat(fromEqual).isEmpty();
	}

	@Test
	@Tag("AC-64")
	void ac64_candidates_within_lead_horizon() {
		List<VetInfo> vets = List.of(new VetInfo(1, List.of("general")));
		List<Window> openWindow = List.of(Window.preferred(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)),
				Window.preferred(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)),
				Window.preferred(DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)),
				Window.preferred(DayOfWeek.THURSDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)),
				Window.preferred(DayOfWeek.FRIDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)));

		int leadDays = 1;
		int horizonDays = 30;
		LocalDate minDate = TODAY.plusDays(leadDays);
		LocalDate maxDate = TODAY.plusDays(horizonDays);

		List<Slot> candidates = FeasibilityChecker.findCandidates(TODAY, leadDays, horizonDays, 30, "GENERAL", null,
				vets, openWindow, List.of(), this::defaultOpeningHours, this::defaultAvailability, List.of());

		assertThat(candidates).isNotEmpty();
		assertThat(candidates).allMatch(s -> !s.date().isBefore(minDate) && !s.date().isAfter(maxDate));
	}

	@Test
	@Tag("AC-65")
	void ac65_first_and_last_boundary_days_included() {
		List<VetInfo> vets = List.of(new VetInfo(1, List.of("general")));
		List<Window> openWindow = List.of(Window.preferred(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)),
				Window.preferred(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)),
				Window.preferred(DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)),
				Window.preferred(DayOfWeek.THURSDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)),
				Window.preferred(DayOfWeek.FRIDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)));

		int leadDays = 1;
		int horizonDays = 30;
		LocalDate tomorrow = TODAY.plusDays(leadDays);
		LocalDate dayThirty = TODAY.plusDays(horizonDays);

		List<Slot> candidates = FeasibilityChecker.findCandidates(TODAY, leadDays, horizonDays, 30, "GENERAL", null,
				vets, openWindow, List.of(), this::defaultOpeningHours, this::defaultAvailability, List.of());

		assertThat(candidates).anyMatch(s -> s.date().equals(tomorrow));
		assertThat(candidates).anyMatch(s -> s.date().equals(dayThirty));

		assertThat(candidates).noneMatch(s -> s.date().equals(TODAY));
		assertThat(candidates).noneMatch(s -> s.date().equals(TODAY.plusDays(horizonDays + 1)));
	}

}
