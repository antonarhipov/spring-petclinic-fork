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
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
public class EffectiveAvailabilityResolver {

	private final VetWeeklyShiftRepository shiftRepository;

	private final VetAvailabilityExceptionRepository exceptionRepository;

	private final VetLeaveRepository leaveRepository;

	private final ClinicClosureRepository closureRepository;

	private final ClinicSettingsRepository clinicSettingsRepository;

	public EffectiveAvailabilityResolver(VetWeeklyShiftRepository shiftRepository,
			VetAvailabilityExceptionRepository exceptionRepository, VetLeaveRepository leaveRepository,
			ClinicClosureRepository closureRepository, ClinicSettingsRepository clinicSettingsRepository) {
		this.shiftRepository = shiftRepository;
		this.exceptionRepository = exceptionRepository;
		this.leaveRepository = leaveRepository;
		this.closureRepository = closureRepository;
		this.clinicSettingsRepository = clinicSettingsRepository;
	}

	public List<InstantInterval> resolve(Integer vetId, LocalDate date, ClinicSettings clinicSettings) {
		Objects.requireNonNull(vetId, "vetId must not be null");
		Objects.requireNonNull(date, "date must not be null");
		ClinicSettings settings = clinicSettings != null ? clinicSettings
				: this.clinicSettingsRepository.getClinicSettings();
		ZoneId zoneId = settings.getZone();

		List<ClinicClosure> closures = this.closureRepository.findAll();
		List<VetLeave> leaves = this.leaveRepository.findByVetId(vetId);
		List<VetWeeklyShift> shifts = this.shiftRepository.findByVetIdAndDayOfWeek(vetId,
				date.getDayOfWeek().getValue());
		List<VetAvailabilityException> exceptions = this.exceptionRepository.findByVetIdAndDate(vetId, date);

		return resolve(date, zoneId, closures, leaves, shifts, exceptions);
	}

	public List<InstantInterval> resolve(Integer vetId, LocalDate date) {
		return resolve(vetId, date, this.clinicSettingsRepository.getClinicSettings());
	}

	/**
	 * Pure, deterministic resolution of effective working intervals for a veterinarian on
	 * a given date. Enforces precedence: Clinic Closure > Vet Leave > Exceptions >
	 * Recurring Shifts. Performs local -> Instant conversion at the single ZoneId
	 * boundary, handling DST transitions.
	 */
	public static List<InstantInterval> resolve(LocalDate date, ZoneId zoneId, List<ClinicClosure> closures,
			List<VetLeave> leaves, List<VetWeeklyShift> shifts, List<VetAvailabilityException> exceptions) {
		Objects.requireNonNull(date, "date must not be null");
		Objects.requireNonNull(zoneId, "zoneId must not be null");

		// 1. Clinic closure check (highest precedence)
		if (closures != null && closures.stream().anyMatch(c -> c.covers(date))) {
			return List.of();
		}

		// 2. Vet leave check
		if (leaves != null && leaves.stream().anyMatch(l -> l.covers(date))) {
			return List.of();
		}

		// 3. Base local intervals and exceptions
		List<VetAvailabilityException> dateExceptions = (exceptions != null)
				? exceptions.stream().filter(e -> date.equals(e.getDate())).collect(Collectors.toList()) : List.of();

		List<VetAvailabilityException> replaceExceptions = dateExceptions.stream()
			.filter(e -> e.getType() == ExceptionType.REPLACE)
			.collect(Collectors.toList());

		List<VetAvailabilityException> removeExceptions = dateExceptions.stream()
			.filter(e -> e.getType() == ExceptionType.REMOVE)
			.collect(Collectors.toList());

		List<VetAvailabilityException> addExceptions = dateExceptions.stream()
			.filter(e -> e.getType() == ExceptionType.ADD)
			.collect(Collectors.toList());

		List<LocalTimeInterval> localIntervals = new ArrayList<>();

		if (!replaceExceptions.isEmpty()) {
			for (VetAvailabilityException r : replaceExceptions) {
				if (r.getStartLocal() != null && r.getEndLocal() != null
						&& r.getStartLocal().isBefore(r.getEndLocal())) {
					localIntervals.add(new LocalTimeInterval(r.getStartLocal(), r.getEndLocal()));
				}
			}
		}
		else if (removeExceptions.stream().anyMatch(r -> r.getStartLocal() == null || r.getEndLocal() == null)) {
			// Full-day remove exception clears all recurring shifts
			localIntervals = new ArrayList<>();
		}
		else {
			// Recurring weekly shifts for date's day of week
			if (shifts != null) {
				int targetDayOfWeek = date.getDayOfWeek().getValue();
				for (VetWeeklyShift s : shifts) {
					if (s.getDayOfWeek() == targetDayOfWeek && s.getStartLocal() != null && s.getEndLocal() != null
							&& s.getStartLocal().isBefore(s.getEndLocal())) {
						localIntervals.add(new LocalTimeInterval(s.getStartLocal(), s.getEndLocal()));
					}
				}
			}

			// Partial remove exceptions subtract intervals
			for (VetAvailabilityException rem : removeExceptions) {
				if (rem.getStartLocal() != null && rem.getEndLocal() != null
						&& rem.getStartLocal().isBefore(rem.getEndLocal())) {
					localIntervals = subtractInterval(localIntervals, rem.getStartLocal(), rem.getEndLocal());
				}
			}
		}

		// Apply ADD exceptions
		for (VetAvailabilityException add : addExceptions) {
			if (add.getStartLocal() != null && add.getEndLocal() != null
					&& add.getStartLocal().isBefore(add.getEndLocal())) {
				localIntervals.add(new LocalTimeInterval(add.getStartLocal(), add.getEndLocal()));
			}
		}

		if (localIntervals.isEmpty()) {
			return List.of();
		}

		// 4. Local -> Instant conversion at clinic ZoneId boundary (DST handling)
		List<InstantInterval> rawInstants = new ArrayList<>();
		for (LocalTimeInterval interval : localIntervals) {
			rawInstants.add(toInstantInterval(date, interval.start(), interval.end(), zoneId));
		}

		if (rawInstants.isEmpty()) {
			return List.of();
		}

		// 5. Merge + sort instant intervals
		rawInstants.sort(Comparator.comparing(InstantInterval::start).thenComparing(InstantInterval::end));

		List<InstantInterval> merged = new ArrayList<>();
		Instant currentStart = rawInstants.get(0).start();
		Instant currentEnd = rawInstants.get(0).end();

		for (int i = 1; i < rawInstants.size(); i++) {
			InstantInterval next = rawInstants.get(i);
			if (!next.start().isAfter(currentEnd)) {
				// Overlapping or contiguous
				if (next.end().isAfter(currentEnd)) {
					currentEnd = next.end();
				}
			}
			else {
				merged.add(new InstantInterval(currentStart, currentEnd));
				currentStart = next.start();
				currentEnd = next.end();
			}
		}
		merged.add(new InstantInterval(currentStart, currentEnd));

		return Collections.unmodifiableList(merged);
	}

	/**
	 * Converts one clinic-local interval at the shared DST-aware conversion boundary.
	 */
	public static InstantInterval toInstantInterval(LocalDate date, LocalTime start, LocalTime end, ZoneId zoneId) {
		Objects.requireNonNull(date, "date must not be null");
		Objects.requireNonNull(start, "start must not be null");
		Objects.requireNonNull(end, "end must not be null");
		Objects.requireNonNull(zoneId, "zoneId must not be null");
		Instant startInstant = ZonedDateTime.of(date, start, zoneId).toInstant();
		Instant endInstant = ZonedDateTime.of(date, end, zoneId).toInstant();
		return new InstantInterval(startInstant, endInstant);
	}

	private static List<LocalTimeInterval> subtractInterval(List<LocalTimeInterval> intervals, LocalTime removeStart,
			LocalTime removeEnd) {
		if (removeStart == null || removeEnd == null || !removeStart.isBefore(removeEnd)) {
			return intervals;
		}
		List<LocalTimeInterval> result = new ArrayList<>();
		for (LocalTimeInterval interval : intervals) {
			if (!removeStart.isBefore(interval.end()) || !removeEnd.isAfter(interval.start())) {
				// No overlap
				result.add(interval);
			}
			else if (!removeStart.isAfter(interval.start()) && !removeEnd.isBefore(interval.end())) {
				// Completely covered by remove, drop it
			}
			else if (removeStart.isAfter(interval.start()) && removeEnd.isBefore(interval.end())) {
				// Split into two
				result.add(new LocalTimeInterval(interval.start(), removeStart));
				result.add(new LocalTimeInterval(removeEnd, interval.end()));
			}
			else if (removeStart.isAfter(interval.start())) {
				// Truncate right
				result.add(new LocalTimeInterval(interval.start(), removeStart));
			}
			else {
				// Truncate left
				result.add(new LocalTimeInterval(removeEnd, interval.end()));
			}
		}
		return result;
	}

}
