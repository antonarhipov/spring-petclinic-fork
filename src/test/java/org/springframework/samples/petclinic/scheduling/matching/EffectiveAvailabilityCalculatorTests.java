package org.springframework.samples.petclinic.scheduling.matching;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.samples.petclinic.scheduling.config.ClinicConfiguration.OpeningPeriod;
import org.springframework.samples.petclinic.scheduling.config.ClinicConfiguration.WorkingPeriod;

import static org.assertj.core.api.Assertions.assertThat;

class EffectiveAvailabilityCalculatorTests {

	private final EffectiveAvailabilityCalculator calculator = new EffectiveAvailabilityCalculator();

	@Test
	void uc7G1AndRule20IntersectsSplitShiftsWithClinicHoursAndRemovesUnavailableVets() {
		LocalDate date = LocalDate.of(2026, 9, 10);
		OpeningPeriod opening = new OpeningPeriod(DayOfWeek.THURSDAY, LocalTime.of(9, 0), LocalTime.of(17, 0));
		List<WorkingPeriod> blocks = List.of(
				new WorkingPeriod(1, DayOfWeek.THURSDAY, LocalTime.of(8, 0), LocalTime.of(12, 0)),
				new WorkingPeriod(1, DayOfWeek.THURSDAY, LocalTime.of(13, 0), LocalTime.of(18, 0)),
				new WorkingPeriod(2, DayOfWeek.THURSDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)));

		assertThat(this.calculator.calculate(date, opening, blocks, Set.of(2), false, null)).containsExactly(
				new EffectiveAvailability(1, date, LocalTime.of(9, 0), LocalTime.NOON),
				new EffectiveAvailability(1, date, LocalTime.of(13, 0), LocalTime.of(17, 0)));
		assertThat(this.calculator.calculate(date, opening, blocks, Set.of(), true, null)).isEmpty();
	}

}
