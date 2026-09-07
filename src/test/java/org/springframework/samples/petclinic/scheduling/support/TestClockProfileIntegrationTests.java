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
package org.springframework.samples.petclinic.scheduling.support;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TestClockProfileIntegrationTests {

	@Autowired
	private Clock clock;

	@Test
	@Tag("AC-135")
	void ac135_test_profile_automatically_overrides_clock() {
		ZoneId amsterdam = ZoneId.of("Europe/Amsterdam");
		assertThat(this.clock.getZone()).isEqualTo(amsterdam);
		assertThat(this.clock.instant()).isEqualTo(Instant.parse("2026-09-07T07:00:00Z"));
		assertThat(LocalDate.now(this.clock)).isEqualTo(LocalDate.of(2026, 9, 7));
		assertThat(LocalTime.now(this.clock)).isEqualTo(LocalTime.of(9, 0));
	}

}
