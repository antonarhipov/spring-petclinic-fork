/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 */

package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class InterpretationNormalizerTests {

	@Autowired
	private InterpretationNormalizer normalizer;

	@Autowired
	private VetRepository vetRepository;

	@Test
	@Tag("AC-39")
	void derivesEveryStructuredField_AC39() {
		List<AvailabilityWindow> windows = List.of(
				AvailabilityWindow.preferredDayOfWeek(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0),
						"MORNING"),
				AvailabilityWindow.allowed(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 7), LocalTime.of(12, 0),
						LocalTime.of(15, 0), null),
				AvailabilityWindow.excluded(LocalDate.of(2026, 9, 6), LocalTime.of(13, 0), LocalTime.of(14, 0),
						"not then"));
		InterpretationResult input = result(45, 2, windows);

		InterpretationResult normalized = this.normalizer.normalize(input);

		assertThat(normalized.reasonSummary()).isEqualTo("Dental follow-up");
		assertThat(normalized.estimatedMinutes()).isEqualTo(45);
		assertThat(normalized.careType()).isEqualTo(CareType.SPECIALTY);
		assertThat(normalized.specialty()).isEqualTo("dentistry");
		assertThat(normalized.preferredVetId()).isEqualTo(2);
		assertThat(normalized.windows()).containsExactlyElementsOf(windows);
		assertThat(normalized.windows()).extracting(AvailabilityWindow::kind)
			.containsExactly(WindowKind.PREFERRED, WindowKind.ALLOWED, WindowKind.EXCLUDED);
	}

	@Test
	@Tag("AC-40")
	void knownPreferredVetResolvesById_AC40() {
		InterpretationResult normalized = this.normalizer.normalize(result(30, 2, List.of()));

		Vet resolved = this.vetRepository.findById(normalized.preferredVetId()).orElseThrow();
		assertThat(normalized.preferredVetId()).isEqualTo(2);
		assertThat(resolved.getFirstName() + " " + resolved.getLastName()).isEqualTo("Helen Leary");
	}

	@Test
	@Tag("AC-41")
	void unknownOrAmbiguousVetBecomesNull_AC41() {
		assertThat(this.normalizer.normalize(result(30, 999, List.of())).preferredVetId()).isNull();
		assertThat(this.normalizer.normalize(result(30, null, List.of())).preferredVetId()).isNull();
	}

	@Test
	@Tag("AC-42")
	void withinBoundsUnchanged_AC42() {
		assertThat(this.normalizer.normalize(result(16, null, List.of())).estimatedMinutes()).isEqualTo(16);
		assertThat(this.normalizer.normalize(result(59, null, List.of())).estimatedMinutes()).isEqualTo(59);
	}

	@Test
	@Tag("AC-43")
	void exactBoundsUnchanged_AC43() {
		assertThat(this.normalizer.normalize(result(15, null, List.of())).estimatedMinutes()).isEqualTo(15);
		assertThat(this.normalizer.normalize(result(60, null, List.of())).estimatedMinutes()).isEqualTo(60);
	}

	private static InterpretationResult result(Integer minutes, Integer preferredVetId,
			List<AvailabilityWindow> windows) {
		return new InterpretationResult("Dental follow-up", minutes, CareType.SPECIALTY, "dentistry", preferredVetId,
				false, windows, "raw", "configured-model", "v1");
	}

}
