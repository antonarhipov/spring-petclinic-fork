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
		String configuredZone = clinicConfigRepository.findById(1)
			.map(ClinicConfig::getTimeZone)
			.orElse(DEFAULT_CLINIC_TIME_ZONE);
		return Clock.system(ZoneId.of(configuredZone));
	}

}
