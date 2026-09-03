/*
 * Copyright 2012-2026 the original author or authors.
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

package org.springframework.samples.petclinic.scheduling;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Pinned clock test configuration (RULE-37, AC-136). Pins clock to Monday 2026-09-07
 * 09:00 Europe/Amsterdam.
 */
@TestConfiguration
public class TestClockConfig {

	public static final ZoneId ZONE = ZoneId.of("Europe/Amsterdam");

	public static final ZonedDateTime PINNED_DATE_TIME = ZonedDateTime.of(2026, 9, 7, 9, 0, 0, 0, ZONE);

	public static final Instant PINNED_INSTANT = PINNED_DATE_TIME.toInstant();

	@Bean
	@Primary
	public Clock testClock() {
		return Clock.fixed(PINNED_INSTANT, ZONE);
	}

}
