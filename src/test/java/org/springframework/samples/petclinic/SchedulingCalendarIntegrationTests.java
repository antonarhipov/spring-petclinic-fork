package org.springframework.samples.petclinic;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.scheduling.availability.RecurringVetShift;

class SchedulingCalendarIntegrationTests {

	@Test
	void recurringShiftRetainsConfiguredGridValues() {
		RecurringVetShift shift = new RecurringVetShift(1, 1, LocalTime.of(9, 0), LocalTime.of(17, 0));
		assertThat(shift.getStartTime()).isEqualTo(LocalTime.of(9, 0));
		assertThat(shift.getEndTime()).isEqualTo(LocalTime.of(17, 0));
	}

}
