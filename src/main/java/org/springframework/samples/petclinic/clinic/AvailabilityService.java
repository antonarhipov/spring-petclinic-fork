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

package org.springframework.samples.petclinic.clinic;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.samples.petclinic.scheduling.ai.SymbolicWindow;
import org.springframework.samples.petclinic.scheduling.solver.VetAvailability;
import org.springframework.samples.petclinic.vet.VetDateException;
import org.springframework.samples.petclinic.vet.VetDateExceptionRepository;
import org.springframework.samples.petclinic.vet.VetDateExceptionType;
import org.springframework.samples.petclinic.vet.VetWeeklyShift;
import org.springframework.samples.petclinic.vet.VetWeeklyShiftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvailabilityService {

	private final ClinicSettingsRepository clinicSettingsRepository;

	private final PartOfDayRepository partOfDayRepository;

	private final VetWeeklyShiftRepository vetWeeklyShiftRepository;

	private final VetDateExceptionRepository vetDateExceptionRepository;

	private final ClinicClosureRepository clinicClosureRepository;

	private Clock clock = Clock.systemDefaultZone();

	public AvailabilityService(ClinicSettingsRepository clinicSettingsRepository,
			PartOfDayRepository partOfDayRepository, VetWeeklyShiftRepository vetWeeklyShiftRepository,
			VetDateExceptionRepository vetDateExceptionRepository, ClinicClosureRepository clinicClosureRepository) {
		this.clinicSettingsRepository = clinicSettingsRepository;
		this.partOfDayRepository = partOfDayRepository;
		this.vetWeeklyShiftRepository = vetWeeklyShiftRepository;
		this.vetDateExceptionRepository = vetDateExceptionRepository;
		this.clinicClosureRepository = clinicClosureRepository;
	}

	@Autowired(required = false)
	public void setClock(Clock clock) {
		if (clock != null) {
			this.clock = clock;
		}
	}

	@Transactional(readOnly = true)
	public ClinicSettings getClinicSettings() {
		return this.clinicSettingsRepository.getSettingsOrDefault();
	}

	@Transactional(readOnly = true)
	public ZoneId getClinicZoneId() {
		return getClinicSettings().getZoneId();
	}

	@Transactional(readOnly = true)
	public ZonedDateTime getNow() {
		return Instant.now(this.clock).atZone(getClinicZoneId());
	}

	@Transactional(readOnly = true)
	public LocalDate getToday() {
		return getNow().toLocalDate();
	}

	@Transactional(readOnly = true)
	public LocalDate getHorizonEndDate() {
		ClinicSettings settings = getClinicSettings();
		return getToday().plusDays(settings.getBookingHorizonDays());
	}

	@Transactional(readOnly = true)
	public List<VetAvailability> getVetAvailability(Integer vetId, LocalDate date) {
		if (vetId == null || date == null) {
			return Collections.emptyList();
		}

		// 1. Check clinic closures on this date
		List<ClinicClosure> closures = this.clinicClosureRepository.findByDateRange(date, date);
		List<TimeRange> closureSubtracts = new ArrayList<>();
		for (ClinicClosure closure : closures) {
			if (closure.isFullDay()) {
				return Collections.emptyList();
			}
			closureSubtracts.add(new TimeRange(closure.getStartTime(), closure.getEndTime()));
		}

		// 2. Check vet date exceptions on this date
		List<VetDateException> exceptions = this.vetDateExceptionRepository.findByVetIdAndDateRange(vetId, date, date);
		List<TimeRange> baseRanges = new ArrayList<>();
		List<TimeRange> leaveSubtracts = new ArrayList<>();
		List<TimeRange> extraRanges = new ArrayList<>();
		boolean hasModifiedHours = false;

		for (VetDateException ex : exceptions) {
			if (ex.getType() == VetDateExceptionType.LEAVE) {
				if (ex.isFullDay()) {
					return Collections.emptyList();
				}
				leaveSubtracts.add(new TimeRange(ex.getStartTime(), ex.getEndTime()));
			}
			else if (ex.getType() == VetDateExceptionType.MODIFIED_HOURS) {
				hasModifiedHours = true;
				if (ex.getStartTime() != null && ex.getEndTime() != null) {
					baseRanges.add(new TimeRange(ex.getStartTime(), ex.getEndTime()));
				}
			}
			else if (ex.getType() == VetDateExceptionType.EXTRA) {
				if (ex.getStartTime() != null && ex.getEndTime() != null) {
					extraRanges.add(new TimeRange(ex.getStartTime(), ex.getEndTime()));
				}
			}
		}

		// If no modified hours exception, load regular weekly shifts
		if (!hasModifiedHours) {
			List<VetWeeklyShift> shifts = this.vetWeeklyShiftRepository.findByVetIdAndDayOfWeek(vetId,
					date.getDayOfWeek());
			for (VetWeeklyShift shift : shifts) {
				baseRanges.add(new TimeRange(shift.getStartTime(), shift.getEndTime()));
			}
		}

		// Add extra shifts
		baseRanges.addAll(extraRanges);

		if (baseRanges.isEmpty()) {
			return Collections.emptyList();
		}

		// Merge base ranges
		List<TimeRange> currentRanges = mergeRanges(baseRanges);

		// Subtract leaves
		for (TimeRange leave : leaveSubtracts) {
			currentRanges = subtractRange(currentRanges, leave);
		}

		// Subtract partial clinic closures
		for (TimeRange closure : closureSubtracts) {
			currentRanges = subtractRange(currentRanges, closure);
		}

		// Convert to VetAvailability facts
		List<VetAvailability> availabilities = new ArrayList<>();
		for (TimeRange range : currentRanges) {
			LocalDateTime start = date.atTime(range.start());
			LocalDateTime end = date.atTime(range.end());
			availabilities.add(new VetAvailability(vetId, start, end));
		}

		return availabilities;
	}

	@Transactional(readOnly = true)
	public List<VetAvailability> getVetAvailabilityForRange(Integer vetId, LocalDate startDate, LocalDate endDate) {
		List<VetAvailability> list = new ArrayList<>();
		LocalDate current = startDate;
		while (!current.isAfter(endDate)) {
			list.addAll(getVetAvailability(vetId, current));
			current = current.plusDays(1);
		}
		return list;
	}

	@Transactional(readOnly = true)
	public List<VetAvailability> getAllVetsAvailabilityForRange(List<Integer> vetIds, LocalDate startDate,
			LocalDate endDate) {
		List<VetAvailability> list = new ArrayList<>();
		if (vetIds == null || vetIds.isEmpty()) {
			return list;
		}
		for (Integer vetId : vetIds) {
			list.addAll(getVetAvailabilityForRange(vetId, startDate, endDate));
		}
		return list;
	}

	@Transactional(readOnly = true)
	public List<VetAvailability> getHorizonAvailability(List<Integer> vetIds) {
		LocalDate today = getToday();
		LocalDate horizonEnd = getHorizonEndDate();
		return getAllVetsAvailabilityForRange(vetIds, today, horizonEnd);
	}

	@Transactional(readOnly = true)
	public List<DateTimeRange> resolveSymbolicWindow(SymbolicWindow window, LocalDate horizonStart,
			LocalDate horizonEnd) {
		if (window == null) {
			return Collections.emptyList();
		}

		List<LocalDate> targetDates = new ArrayList<>();
		if (window.date() != null) {
			if (!window.date().isBefore(horizonStart) && !window.date().isAfter(horizonEnd)) {
				targetDates.add(window.date());
			}
		}
		else if (window.dayOfWeek() != null) {
			LocalDate cur = horizonStart;
			while (!cur.isAfter(horizonEnd)) {
				if (cur.getDayOfWeek() == window.dayOfWeek()) {
					targetDates.add(cur);
				}
				cur = cur.plusDays(1);
			}
		}
		else {
			LocalDate cur = horizonStart;
			while (!cur.isAfter(horizonEnd)) {
				targetDates.add(cur);
				cur = cur.plusDays(1);
			}
		}

		LocalTime startTime = LocalTime.MIN;
		LocalTime endTime = LocalTime.MAX;

		if (window.partOfDay() != null) {
			Optional<PartOfDay> podOpt = this.partOfDayRepository.findByNameIgnoreCase(window.partOfDay());
			if (podOpt.isPresent()) {
				startTime = podOpt.get().getStartTime();
				endTime = podOpt.get().getEndTime();
			}
		}

		List<DateTimeRange> ranges = new ArrayList<>();
		for (LocalDate date : targetDates) {
			ranges.add(new DateTimeRange(date.atTime(startTime), date.atTime(endTime)));
		}
		return ranges;
	}

	public record DateTimeRange(LocalDateTime start, LocalDateTime end) {
		public boolean overlaps(LocalDateTime otherStart, LocalDateTime otherEnd) {
			if (otherStart == null || otherEnd == null) {
				return false;
			}
			return otherStart.isBefore(this.end) && otherEnd.isAfter(this.start);
		}
	}

	private List<TimeRange> mergeRanges(List<TimeRange> ranges) {
		if (ranges.isEmpty()) {
			return Collections.emptyList();
		}
		List<TimeRange> sorted = new ArrayList<>(ranges);
		sorted.sort(Comparator.comparing(TimeRange::start));

		List<TimeRange> merged = new ArrayList<>();
		TimeRange prev = sorted.get(0);

		for (int i = 1; i < sorted.size(); i++) {
			TimeRange curr = sorted.get(i);
			if (!curr.start().isAfter(prev.end())) {
				LocalTime maxEnd = curr.end().isAfter(prev.end()) ? curr.end() : prev.end();
				prev = new TimeRange(prev.start(), maxEnd);
			}
			else {
				merged.add(prev);
				prev = curr;
			}
		}
		merged.add(prev);
		return merged;
	}

	private List<TimeRange> subtractRange(List<TimeRange> sourceRanges, TimeRange subtract) {
		List<TimeRange> result = new ArrayList<>();
		for (TimeRange range : sourceRanges) {
			if (!subtract.end().isAfter(range.start()) || !subtract.start().isBefore(range.end())) {
				// No overlap
				result.add(range);
			}
			else if (!subtract.start().isAfter(range.start()) && !subtract.end().isBefore(range.end())) {
				// Completely covered by subtract range, drop
			}
			else if (subtract.start().isAfter(range.start()) && subtract.end().isBefore(range.end())) {
				// Split into two
				result.add(new TimeRange(range.start(), subtract.start()));
				result.add(new TimeRange(subtract.end(), range.end()));
			}
			else if (!subtract.start().isAfter(range.start()) && subtract.end().isBefore(range.end())) {
				// Trim start
				result.add(new TimeRange(subtract.end(), range.end()));
			}
			else if (subtract.start().isAfter(range.start()) && !subtract.end().isBefore(range.end())) {
				// Trim end
				result.add(new TimeRange(range.start(), subtract.start()));
			}
		}
		return result;
	}

	public record TimeRange(LocalTime start, LocalTime end) {
		public TimeRange {
			if (start != null && end != null && !start.isBefore(end)) {
				throw new IllegalArgumentException("Start time must be before end time");
			}
		}
	}

}
