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
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.samples.petclinic.scheduling.web.ClinicSettingsForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing clinic configuration, opening hours, time buckets and audits (AC-99,
 * RULE-37).
 */
@Service
public class ClinicConfigService {

	private final ClinicConfigRepository repository;

	private final ClinicOpeningHourRepository openingHourRepository;

	private final ClinicPartOfDayRepository partOfDayRepository;

	private final Clock clock;

	private final List<ConfigAuditRecord> auditLog = new CopyOnWriteArrayList<>();

	public ClinicConfigService(ClinicConfigRepository repository, ClinicOpeningHourRepository openingHourRepository,
			ClinicPartOfDayRepository partOfDayRepository, Clock clock) {
		this.repository = repository;
		this.openingHourRepository = openingHourRepository;
		this.partOfDayRepository = partOfDayRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public ClinicConfig current() {
		return this.repository.findById(1).orElseThrow();
	}

	@Transactional(readOnly = true)
	public List<ClinicOpeningHour> allOpeningHours() {
		return this.openingHourRepository.findAll()
			.stream()
			.sorted(Comparator.comparing(ClinicOpeningHour::getDayOfWeek))
			.toList();
	}

	@Transactional(readOnly = true)
	public List<ClinicPartOfDay> allPartsOfDay() {
		return this.partOfDayRepository.findAll()
			.stream()
			.sorted(Comparator.comparing(ClinicPartOfDay::getStartTime))
			.toList();
	}

	@Transactional
	public void updateSettings(ClinicSettingsForm form, String actor) {
		ClinicConfig config = current();
		if (form.getHorizonDays() != null) {
			config.setBookingHorizonDays(form.getHorizonDays());
		}
		if (form.getMinDurationMinutes() != null) {
			config.setMinDurationMinutes(form.getMinDurationMinutes());
		}
		if (form.getMaxDurationMinutes() != null) {
			config.setMaxDurationMinutes(form.getMaxDurationMinutes());
		}
		if (form.getDefaultDurationMinutes() != null) {
			config.setDefaultDurationMinutes(form.getDefaultDurationMinutes());
		}
		if (form.getEmergencyPhone() != null) {
			config.setEmergencyPhone(form.getEmergencyPhone());
		}
		if (form.getTimeZone() != null) {
			config.setTimeZone(form.getTimeZone());
		}
		this.repository.save(config);

		// Update opening hours
		Map<String, String> hours = form.getOpeningHours();
		if (hours != null) {
			List<ClinicOpeningHour> allHours = this.openingHourRepository.findAll();
			for (ClinicOpeningHour h : allHours) {
				String dayKey = h.getDayOfWeek().name();
				String openKey = dayKey + "_open";
				String closeKey = dayKey + "_close";
				String closedKey = dayKey + "_closed";

				if ("true".equalsIgnoreCase(hours.get(closedKey))) {
					h.setClosed(true);
					h.setOpenTime(null);
					h.setCloseTime(null);
				}
				else if (hours.containsKey(openKey) && hours.containsKey(closeKey)) {
					h.setClosed(false);
					h.setOpenTime(LocalTime.parse(hours.get(openKey)));
					h.setCloseTime(LocalTime.parse(hours.get(closeKey)));
				}
				this.openingHourRepository.save(h);
			}
		}

		// Update parts of day
		List<ClinicPartOfDay> parts = this.partOfDayRepository.findAll();
		for (ClinicPartOfDay part : parts) {
			if ("morning".equalsIgnoreCase(part.getName())) {
				if (form.getMorningStart() != null)
					part.setStartTime(LocalTime.parse(form.getMorningStart()));
				if (form.getMorningEnd() != null)
					part.setEndTime(LocalTime.parse(form.getMorningEnd()));
			}
			else if ("afternoon".equalsIgnoreCase(part.getName())) {
				if (form.getAfternoonStart() != null)
					part.setStartTime(LocalTime.parse(form.getAfternoonStart()));
				if (form.getAfternoonEnd() != null)
					part.setEndTime(LocalTime.parse(form.getAfternoonEnd()));
			}
			else if ("evening".equalsIgnoreCase(part.getName())) {
				if (form.getEveningStart() != null)
					part.setStartTime(LocalTime.parse(form.getEveningStart()));
				if (form.getEveningEnd() != null)
					part.setEndTime(LocalTime.parse(form.getEveningEnd()));
			}
			this.partOfDayRepository.save(part);
		}

		// Record audit
		String effectiveActor = (actor != null && !actor.isBlank()) ? actor : "staff";
		this.auditLog.add(new ConfigAuditRecord(effectiveActor, "UPDATE_CLINIC_CONFIG", "Updated clinic settings",
				this.clock.instant()));
	}

	public List<ConfigAuditRecord> getAuditLog() {
		return Collections.unmodifiableList(this.auditLog);
	}

	public record ConfigAuditRecord(String actor, String action, String details, Instant timestamp) {
	}

}
