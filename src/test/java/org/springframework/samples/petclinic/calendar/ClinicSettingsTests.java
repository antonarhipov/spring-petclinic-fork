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
package org.springframework.samples.petclinic.calendar;

import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClinicSettingsTests {

	@Test
	void defaultSettingsMatchSpecification() {
		ClinicSettings settings = new ClinicSettings();

		assertThat(settings.getGridGranularityMin()).isEqualTo(15);
		assertThat(settings.getHoldDurationMin()).isEqualTo(10);
		assertThat(settings.getBookingHorizonDays()).isEqualTo(60);
		assertThat(settings.getMinVisitMin()).isEqualTo(15);
		assertThat(settings.getMaxVisitMin()).isEqualTo(120);
		assertThat(settings.getDefaultVisitMin()).isEqualTo(30);
		assertThat(settings.getZoneId()).isEqualTo("Europe/Amsterdam");
		assertThat(settings.getZone()).isEqualTo(ZoneId.of("Europe/Amsterdam"));

		assertThat(settings.getMorningStart()).isEqualTo(LocalTime.of(8, 0));
		assertThat(settings.getMorningEnd()).isEqualTo(LocalTime.of(12, 0));
		assertThat(settings.getAfternoonStart()).isEqualTo(LocalTime.of(12, 0));
		assertThat(settings.getAfternoonEnd()).isEqualTo(LocalTime.of(17, 0));
		assertThat(settings.getEveningStart()).isEqualTo(LocalTime.of(17, 0));
		assertThat(settings.getEveningEnd()).isEqualTo(LocalTime.of(20, 0));
	}

	@Test
	void visitDurationClampingEnforcesBounds() {
		ClinicSettings settings = new ClinicSettings();
		settings.setMinVisitMin(15);
		settings.setMaxVisitMin(120);
		settings.setDefaultVisitMin(30);

		assertThat(settings.clampDuration(5)).isEqualTo(15);
		assertThat(settings.clampDuration(150)).isEqualTo(120);
		assertThat(settings.clampDuration(45)).isEqualTo(45);
		assertThat(settings.clampDuration(0)).isEqualTo(30);
		assertThat(settings.clampDuration(null)).isEqualTo(30);
	}

}
