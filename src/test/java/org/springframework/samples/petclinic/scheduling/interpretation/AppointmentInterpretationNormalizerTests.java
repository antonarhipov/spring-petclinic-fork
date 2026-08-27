/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.calendar.ClinicSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppointmentInterpretationNormalizerTests {

	private final AppointmentInterpretationNormalizer normalizer = new AppointmentInterpretationNormalizer();

	@Test
	void clampsDurationAndResolvesConfiguredDayPartInClinicZone() {
		ClinicSettings settings = new ClinicSettings();
		settings.setMorningStart(LocalTime.of(7, 30));
		settings.setMorningEnd(LocalTime.of(11, 30));
		AppointmentInterpretation input = interpretation(500,
				List.of(new InterpretationWindow(DayOfWeek.MONDAY, " MORNING ", null, null, null)));

		AppointmentInterpretation result = this.normalizer.normalize(input, settings);

		assertThat(result.estimatedDurationMin()).isEqualTo(120);
		assertThat(result.preferred()).containsExactly(new InterpretationWindow(DayOfWeek.MONDAY, "morning",
				LocalTime.of(7, 30), LocalTime.of(11, 30), "Europe/Amsterdam"));
	}

	@Test
	void preservesValidExplicitLocalRangeAndAddsClinicZone() {
		InterpretationWindow window = new InterpretationWindow(DayOfWeek.FRIDAY, null, LocalTime.of(13, 15),
				LocalTime.of(15, 0), "ignored");

		AppointmentInterpretation result = this.normalizer.normalize(interpretation(5, List.of(window)),
				new ClinicSettings());

		assertThat(result.estimatedDurationMin()).isEqualTo(15);
		assertThat(result.preferred().getFirst().zoneId()).isEqualTo("Europe/Amsterdam");
	}

	@Test
	void rejectsAnUnknownDayPartAsUnusable() {
		AppointmentInterpretation input = interpretation(30,
				List.of(new InterpretationWindow(DayOfWeek.MONDAY, "lunchtime", null, null, null)));

		assertThatThrownBy(() -> this.normalizer.normalize(input, new ClinicSettings()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Unknown clinic day part");
	}

	private static AppointmentInterpretation interpretation(int duration, List<InterpretationWindow> preferred) {
		return new AppointmentInterpretation("check-up", null, duration, preferred, List.of(), List.of(), null,
				Urgency.ROUTINE);
	}

}
