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

import java.util.List;

import org.springframework.samples.petclinic.scheduling.web.ClinicSettingsForm;
import org.springframework.stereotype.Service;

/**
 * Service managing clinic configuration and settings operations (AC-99).
 */
@Service
public class ClinicSettingsService {

	private final ClinicConfigService configService;

	public ClinicSettingsService(ClinicConfigService configService) {
		this.configService = configService;
	}

	public ClinicConfig current() {
		return this.configService.current();
	}

	public List<ClinicOpeningHour> allOpeningHours() {
		return this.configService.allOpeningHours();
	}

	public List<ClinicPartOfDay> allPartsOfDay() {
		return this.configService.allPartsOfDay();
	}

	public void updateSettings(ClinicSettingsForm form, String actor) {
		this.configService.updateSettings(form, actor);
	}

	public List<ClinicConfigService.ConfigAuditRecord> getAuditLog() {
		return this.configService.getAuditLog();
	}

}
