/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.clinic;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ClinicSettingsTests {

	@Autowired
	private ClinicSettingsRepository clinicSettingsRepository;

	@Autowired
	private PartOfDayRepository partOfDayRepository;

	@Test
	void shouldLoadDefaultClinicSettings() {
		Optional<ClinicSettings> settingsOpt = clinicSettingsRepository.findFirstSettings();
		assertThat(settingsOpt).isPresent();

		ClinicSettings settings = settingsOpt.get();
		assertThat(settings.getTimeZone()).isEqualTo("America/New_York");
		assertThat(settings.getMinVisitMinutes()).isEqualTo(15);
		assertThat(settings.getMaxVisitMinutes()).isEqualTo(120);
		assertThat(settings.getDefaultVisitMinutes()).isEqualTo(30);
		assertThat(settings.getBookingHorizonDays()).isEqualTo(14);
		assertThat(settings.getHoldDurationMinutes()).isEqualTo(5);
		assertThat(settings.getGridMinutes()).isEqualTo(15);
		assertThat(settings.getZoneId().getId()).isEqualTo("America/New_York");
	}

	@Test
	void shouldClampDurationCorrectly() {
		ClinicSettings settings = clinicSettingsRepository.getSettingsOrDefault();
		assertThat(settings.clampDuration(null)).isEqualTo(30);
		assertThat(settings.clampDuration(0)).isEqualTo(30);
		assertThat(settings.clampDuration(5)).isEqualTo(15);
		assertThat(settings.clampDuration(45)).isEqualTo(45);
		assertThat(settings.clampDuration(180)).isEqualTo(120);
	}

	@Test
	void shouldLoadDefaultPartsOfDay() {
		assertThat(partOfDayRepository.count()).isGreaterThanOrEqualTo(3);

		Optional<PartOfDay> morning = partOfDayRepository.findByNameIgnoreCase("MORNING");
		assertThat(morning).isPresent();
		assertThat(morning.get().getStartTime()).isEqualTo(LocalTime.of(8, 0));
		assertThat(morning.get().getEndTime()).isEqualTo(LocalTime.of(12, 0));
		assertThat(morning.get().matches(LocalTime.of(9, 30))).isTrue();
		assertThat(morning.get().matches(LocalTime.of(12, 0))).isFalse();

		Optional<PartOfDay> afternoon = partOfDayRepository.findByNameIgnoreCase("AFTERNOON");
		assertThat(afternoon).isPresent();
		assertThat(afternoon.get().getStartTime()).isEqualTo(LocalTime.of(12, 0));
		assertThat(afternoon.get().getEndTime()).isEqualTo(LocalTime.of(17, 0));

		Optional<PartOfDay> evening = partOfDayRepository.findByNameIgnoreCase("EVENING");
		assertThat(evening).isPresent();
		assertThat(evening.get().getStartTime()).isEqualTo(LocalTime.of(17, 0));
		assertThat(evening.get().getEndTime()).isEqualTo(LocalTime.of(20, 0));
	}

}
