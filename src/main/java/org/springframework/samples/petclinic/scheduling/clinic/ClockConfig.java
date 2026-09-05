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

package org.springframework.samples.petclinic.scheduling.clinic;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Clock configuration providing an injectable Clock bean for deterministic time handling
 * (RULE-37, AC-108, AC-136).
 */
@Configuration
public class ClockConfig {

	public static final String DEFAULT_CLINIC_TIME_ZONE = "Europe/Amsterdam";

	@Bean
	@ConditionalOnMissingBean
	public Clock clock(ClinicConfigRepository clinicConfigRepository) {
		return new ConfiguredClinicClock(clinicConfigRepository);
	}

	private static final class ConfiguredClinicClock extends Clock {

		private final ClinicConfigRepository clinicConfigRepository;

		private ConfiguredClinicClock(ClinicConfigRepository clinicConfigRepository) {
			this.clinicConfigRepository = clinicConfigRepository;
		}

		@Override
		public ZoneId getZone() {
			return this.clinicConfigRepository.findById(1)
				.map(ClinicConfig::getTimeZone)
				.map(ZoneId::of)
				.orElseGet(() -> ZoneId.of(DEFAULT_CLINIC_TIME_ZONE));
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return Clock.system(zone);
		}

		@Override
		public Instant instant() {
			return Clock.systemUTC().instant();
		}

	}

}
