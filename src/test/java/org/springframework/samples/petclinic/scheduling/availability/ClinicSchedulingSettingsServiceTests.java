package org.springframework.samples.petclinic.scheduling.availability;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

class ClinicSchedulingSettingsServiceTests {

	@Test
	void rejectsAZeroLengthNamedPeriod() {
		assertThatThrownBy(() -> new NamedDayPeriod("Morning", LocalTime.of(9, 0), LocalTime.of(9, 0)))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
