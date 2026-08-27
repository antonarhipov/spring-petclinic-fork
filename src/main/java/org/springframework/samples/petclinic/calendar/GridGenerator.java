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

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

@Component
public class GridGenerator {

	private final EffectiveAvailabilityResolver availabilityResolver;

	private final ClinicSettingsRepository clinicSettingsRepository;

	public GridGenerator(EffectiveAvailabilityResolver availabilityResolver,
			ClinicSettingsRepository clinicSettingsRepository) {
		this.availabilityResolver = availabilityResolver;
		this.clinicSettingsRepository = clinicSettingsRepository;
	}

	/**
	 * Pure function generating candidate start instants from a list of effective
	 * availability intervals. A start instant is valid if and only if: 1. It falls on the
	 * configured granularity grid (epochSecond % (granularityMin * 60) == 0). 2. The
	 * entire visit duration [start, start + durationMin] fits completely within ONE
	 * continuous interval block.
	 */
	public static List<Instant> generateCandidateStarts(List<InstantInterval> intervals, int durationMin,
			int granularityMin) {
		if (intervals == null || intervals.isEmpty() || durationMin <= 0 || granularityMin <= 0) {
			return List.of();
		}

		List<Instant> candidateStarts = new ArrayList<>();
		long granularitySeconds = (long) granularityMin * 60;
		long durationSeconds = (long) durationMin * 60;

		for (InstantInterval interval : intervals) {
			long startEpochSec = interval.start().getEpochSecond();
			long endEpochSec = interval.end().getEpochSecond();

			long remainder = Math.floorMod(startEpochSec, granularitySeconds);
			long firstGridSec = (remainder == 0) ? startEpochSec : (startEpochSec + (granularitySeconds - remainder));

			for (long currentSec = firstGridSec; currentSec
					+ durationSeconds <= endEpochSec; currentSec += granularitySeconds) {
				candidateStarts.add(Instant.ofEpochSecond(currentSec));
			}
		}

		return Collections.unmodifiableList(candidateStarts);
	}

	/**
	 * Generates candidate starts for a specific veterinarian on a single date.
	 */
	public List<Instant> generateCandidateStarts(Integer vetId, LocalDate date, int durationMin,
			ClinicSettings settings) {
		Objects.requireNonNull(vetId, "vetId must not be null");
		Objects.requireNonNull(date, "date must not be null");
		ClinicSettings clinicSettings = settings != null ? settings : this.clinicSettingsRepository.getClinicSettings();
		int duration = clinicSettings.clampDuration(durationMin);
		int granularity = clinicSettings.getGridGranularityMin();

		List<InstantInterval> intervals = this.availabilityResolver.resolve(vetId, date, clinicSettings);
		return generateCandidateStarts(intervals, duration, granularity);
	}

	/**
	 * Generates candidate slots for a veterinarian across the booking horizon starting
	 * from fromDate.
	 */
	public List<SlotCandidate> generateCandidateSlots(Integer vetId, LocalDate fromDate, Integer visitDurationMin,
			ClinicSettings settings) {
		Objects.requireNonNull(vetId, "vetId must not be null");
		Objects.requireNonNull(fromDate, "fromDate must not be null");
		ClinicSettings clinicSettings = settings != null ? settings : this.clinicSettingsRepository.getClinicSettings();
		int duration = clinicSettings.clampDuration(visitDurationMin);
		int granularity = clinicSettings.getGridGranularityMin();
		int horizonDays = clinicSettings.getBookingHorizonDays();

		List<SlotCandidate> candidates = new ArrayList<>();
		for (int day = 0; day < horizonDays; day++) {
			LocalDate date = fromDate.plusDays(day);
			List<InstantInterval> intervals = this.availabilityResolver.resolve(vetId, date, clinicSettings);
			List<Instant> starts = generateCandidateStarts(intervals, duration, granularity);
			for (Instant start : starts) {
				candidates.add(new SlotCandidate(vetId, start));
			}
		}
		return Collections.unmodifiableList(candidates);
	}

	public List<SlotCandidate> generateCandidateSlots(Integer vetId, LocalDate fromDate, Integer visitDurationMin) {
		return generateCandidateSlots(vetId, fromDate, visitDurationMin,
				this.clinicSettingsRepository.getClinicSettings());
	}

}
