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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

/**
 * Stable boundary for effective availability calculations.
 */
@Service
public class EffectiveAvailabilityService {

	private final VetAvailabilityService availabilityService;

	private final AvailabilityConflictService conflictService;

	private final ClinicOpeningHourRepository openingHourRepository;

	public EffectiveAvailabilityService(VetAvailabilityService availabilityService,
			AvailabilityConflictService conflictService, ClinicOpeningHourRepository openingHourRepository) {
		this.availabilityService = availabilityService;
		this.conflictService = conflictService;
		this.openingHourRepository = openingHourRepository;
	}

	public VetAvailabilityService.VetAvailabilityData getAvailabilityData(Integer vetId) {
		return this.availabilityService.getAvailabilityData(vetId);
	}

	public List<VetAvailabilityService.TimeInterval> effectiveBlocks(Integer vetId, LocalDate date) {
		return this.availabilityService.effectiveBlocks(vetId, date);
	}

	public AvailabilityUpdateResult saveSchedule(Integer vetId,
			Map<java.time.DayOfWeek, List<VetAvailabilityService.TimeInterval>> schedule,
			List<VetAvailabilityService.VetExceptionDto> exceptions, List<VetAvailabilityService.VetLeaveDto> leaves,
			List<VetAvailabilityService.ClinicClosureDto> closures, String actor) {
		VetAvailabilityService.VetAvailabilityData current = this.availabilityService.getAvailabilityData(vetId);
		List<AvailabilityConflictService.AvailabilityEdit> edits = new ArrayList<>();
		addExceptionEdits(edits, vetId, current, exceptions, leaves, closures);
		addLeaveEdits(edits, vetId, current.leaves(), leaves);
		addClosureEdits(edits, current.closures(), closures);

		AvailabilityConflictService.EditResult<VetAvailabilityService.AvailabilityUpdateResult> result = this.conflictService
			.apply(edits, actor,
					() -> this.availabilityService.saveSchedule(vetId, schedule, exceptions, leaves, closures));
		boolean warning = result.value() != null && result.value().outOfHoursWarning();
		return new AvailabilityUpdateResult(result.applied(), warning, result.invalidatedHoldIds(), result.conflicts());
	}

	private void addExceptionEdits(List<AvailabilityConflictService.AvailabilityEdit> edits, Integer vetId,
			VetAvailabilityService.VetAvailabilityData current,
			List<VetAvailabilityService.VetExceptionDto> replacement,
			List<VetAvailabilityService.VetLeaveDto> replacementLeaves,
			List<VetAvailabilityService.ClinicClosureDto> replacementClosures) {
		if (replacement == null) {
			return;
		}
		Map<LocalDate, List<VetAvailabilityService.TimeInterval>> oldByDate = exceptionBlocks(current.exceptions());
		Map<LocalDate, List<VetAvailabilityService.TimeInterval>> newByDate = exceptionBlocksFromDtos(replacement);
		Set<LocalDate> dates = new HashSet<>(oldByDate.keySet());
		dates.addAll(newByDate.keySet());
		for (LocalDate date : dates) {
			List<VetAvailabilityService.TimeInterval> oldBlocks = oldByDate.get(date);
			List<VetAvailabilityService.TimeInterval> newBlocks = newByDate.get(date);
			if (!Objects.equals(newBlocks, oldBlocks)) {
				List<VetAvailabilityService.TimeInterval> projected = projectedExceptionBlocks(date, newByDate, current,
						replacementLeaves, replacementClosures);
				edits.add(new AvailabilityConflictService.ExceptionEdit(vetId, date, projected));
			}
		}
	}

	private List<VetAvailabilityService.TimeInterval> projectedExceptionBlocks(LocalDate date,
			Map<LocalDate, List<VetAvailabilityService.TimeInterval>> newByDate,
			VetAvailabilityService.VetAvailabilityData current,
			List<VetAvailabilityService.VetLeaveDto> replacementLeaves,
			List<VetAvailabilityService.ClinicClosureDto> replacementClosures) {
		List<VetAvailabilityService.ClinicClosureDto> closures = replacementClosures != null ? replacementClosures
				: current.closures()
					.stream()
					.map(closure -> new VetAvailabilityService.ClinicClosureDto(closure.getClosureDate(),
							closure.getReason()))
					.toList();
		if (closures.stream().anyMatch(closure -> closure.date().equals(date))) {
			return List.of();
		}
		List<VetAvailabilityService.VetLeaveDto> leaves = replacementLeaves != null ? replacementLeaves
				: current.leaves()
					.stream()
					.map(leave -> new VetAvailabilityService.VetLeaveDto(leave.getStartDate(), leave.getEndDate(),
							leave.getReason()))
					.toList();
		if (leaves.stream().anyMatch(leave -> !date.isBefore(leave.startDate()) && !date.isAfter(leave.endDate()))) {
			return List.of();
		}
		List<VetAvailabilityService.TimeInterval> source = newByDate.containsKey(date) ? newByDate.get(date)
				: current.weeklyBlocks()
					.stream()
					.filter(block -> block.getDayOfWeek() == date.getDayOfWeek())
					.map(block -> new VetAvailabilityService.TimeInterval(block.getStartTime(), block.getEndTime()))
					.toList();
		ClinicOpeningHour opening = this.openingHourRepository.findAll()
			.stream()
			.filter(row -> row.getDayOfWeek() == date.getDayOfWeek())
			.findFirst()
			.orElse(null);
		if (opening == null || opening.isClosed()) {
			return List.of();
		}
		return source.stream().map(block -> intersect(block, opening)).filter(Objects::nonNull).toList();
	}

	private VetAvailabilityService.TimeInterval intersect(VetAvailabilityService.TimeInterval block,
			ClinicOpeningHour opening) {
		LocalTime start = block.start().isBefore(opening.getOpenTime()) ? opening.getOpenTime() : block.start();
		LocalTime end = block.end().isAfter(opening.getCloseTime()) ? opening.getCloseTime() : block.end();
		return start.isBefore(end) ? new VetAvailabilityService.TimeInterval(start, end) : null;
	}

	private Map<LocalDate, List<VetAvailabilityService.TimeInterval>> exceptionBlocks(List<VetException> exceptions) {
		Map<LocalDate, List<VetAvailabilityService.TimeInterval>> blocks = new HashMap<>();
		Set<LocalDate> unavailableDates = new HashSet<>();
		for (VetException exception : exceptions) {
			List<VetAvailabilityService.TimeInterval> dayBlocks = blocks.computeIfAbsent(exception.getExceptionDate(),
					ignored -> new ArrayList<>());
			if (exception.isUnavailable()) {
				unavailableDates.add(exception.getExceptionDate());
			}
			else if (exception.getStartTime() != null && exception.getEndTime() != null) {
				dayBlocks
					.add(new VetAvailabilityService.TimeInterval(exception.getStartTime(), exception.getEndTime()));
			}
		}
		unavailableDates.forEach(date -> blocks.put(date, List.of()));
		return sortedBlocks(blocks);
	}

	private Map<LocalDate, List<VetAvailabilityService.TimeInterval>> exceptionBlocksFromDtos(
			List<VetAvailabilityService.VetExceptionDto> exceptions) {
		Map<LocalDate, List<VetAvailabilityService.TimeInterval>> blocks = new HashMap<>();
		Set<LocalDate> unavailableDates = new HashSet<>();
		for (VetAvailabilityService.VetExceptionDto exception : exceptions) {
			List<VetAvailabilityService.TimeInterval> dayBlocks = blocks.computeIfAbsent(exception.date(),
					ignored -> new ArrayList<>());
			if (exception.unavailable()) {
				unavailableDates.add(exception.date());
			}
			else if (exception.start() != null && exception.end() != null) {
				dayBlocks.add(new VetAvailabilityService.TimeInterval(exception.start(), exception.end()));
			}
		}
		unavailableDates.forEach(date -> blocks.put(date, List.of()));
		return sortedBlocks(blocks);
	}

	private Map<LocalDate, List<VetAvailabilityService.TimeInterval>> sortedBlocks(
			Map<LocalDate, List<VetAvailabilityService.TimeInterval>> blocks) {
		blocks.replaceAll((date, values) -> values.stream()
			.sorted(Comparator.comparing(VetAvailabilityService.TimeInterval::start))
			.toList());
		return blocks;
	}

	private void addLeaveEdits(List<AvailabilityConflictService.AvailabilityEdit> edits, Integer vetId,
			List<VetLeave> current, List<VetAvailabilityService.VetLeaveDto> replacement) {
		if (replacement == null) {
			return;
		}
		Set<LeaveValue> existing = current.stream()
			.map(leave -> new LeaveValue(leave.getStartDate(), leave.getEndDate(), leave.getReason()))
			.collect(java.util.stream.Collectors.toSet());
		for (VetAvailabilityService.VetLeaveDto leave : replacement) {
			if (!existing.contains(new LeaveValue(leave.startDate(), leave.endDate(), leave.reason()))) {
				edits.add(new AvailabilityConflictService.LeaveEdit(vetId, leave.startDate(), leave.endDate()));
			}
		}
	}

	private void addClosureEdits(List<AvailabilityConflictService.AvailabilityEdit> edits, List<ClinicClosure> current,
			List<VetAvailabilityService.ClinicClosureDto> replacement) {
		if (replacement == null) {
			return;
		}
		Set<LocalDate> existing = current.stream()
			.map(ClinicClosure::getClosureDate)
			.collect(java.util.stream.Collectors.toSet());
		for (VetAvailabilityService.ClinicClosureDto closure : replacement) {
			if (!existing.contains(closure.date())) {
				edits.add(new AvailabilityConflictService.ClosureEdit(closure.date()));
			}
		}
	}

	private record LeaveValue(LocalDate startDate, LocalDate endDate, String reason) {
	}

	public record AvailabilityUpdateResult(boolean success, boolean outOfHoursWarning, List<Integer> invalidatedHoldIds,
			List<AvailabilityConflictService.AppointmentConflict> conflicts) {

		public boolean holdsInvalidated() {
			return !this.invalidatedHoldIds.isEmpty();
		}

	}

}
