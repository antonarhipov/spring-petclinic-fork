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

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.samples.petclinic.scheduling.web.ClinicSettingsForm;
import org.springframework.stereotype.Service;

/**
 * Service managing clinic configuration and settings operations (AC-99).
 */
@Service
public class ClinicSettingsService {

	private final ClinicConfigService configService;

	private final AvailabilityConflictService conflictService;

	public ClinicSettingsService(ClinicConfigService configService, AvailabilityConflictService conflictService) {
		this.configService = configService;
		this.conflictService = conflictService;
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

	public AvailabilityConflictService.EditResult<Void> updateSettings(ClinicSettingsForm form, String actor) {
		List<AvailabilityConflictService.OpeningHoursEdit> edits = openingHoursEdits(form.getOpeningHours());
		return this.conflictService.apply(edits, actor, () -> {
			this.configService.updateSettings(form, actor);
			return null;
		});
	}

	public List<ClinicConfigService.ConfigAuditRecord> getAuditLog() {
		return this.configService.getAuditLog();
	}

	private List<AvailabilityConflictService.OpeningHoursEdit> openingHoursEdits(Map<String, String> submitted) {
		if (submitted == null) {
			return List.of();
		}
		List<AvailabilityConflictService.OpeningHoursEdit> edits = new ArrayList<>();
		for (ClinicOpeningHour current : allOpeningHours()) {
			DayOfWeek day = current.getDayOfWeek();
			String prefix = day.name();
			if (!submitted.containsKey(prefix + "_closed") && !submitted.containsKey(prefix + "_open")
					&& !submitted.containsKey(prefix + "_close")) {
				continue;
			}
			boolean closed = "true".equalsIgnoreCase(submitted.get(prefix + "_closed"));
			LocalTime open = closed ? null : LocalTime.parse(submitted.get(prefix + "_open"));
			LocalTime close = closed ? null : LocalTime.parse(submitted.get(prefix + "_close"));
			if (closed != current.isClosed() || !Objects.equals(open, current.getOpenTime())
					|| !Objects.equals(close, current.getCloseTime())) {
				edits.add(new AvailabilityConflictService.OpeningHoursEdit(day, closed, open, close));
			}
		}
		return edits;
	}

}
