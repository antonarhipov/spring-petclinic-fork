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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists veterinarian availability and computes the effective blocks for a day.
 */
@Service
@Transactional
public class VetAvailabilityService {

	private final VetRepository vetRepository;

	private final VetWeeklyBlockRepository weeklyBlockRepository;

	private final VetExceptionRepository exceptionRepository;

	private final VetLeaveRepository leaveRepository;

	private final ClinicClosureRepository closureRepository;

	private final ClinicOpeningHourRepository openingHourRepository;

	public VetAvailabilityService(VetRepository vetRepository, VetWeeklyBlockRepository weeklyBlockRepository,
			VetExceptionRepository exceptionRepository, VetLeaveRepository leaveRepository,
			ClinicClosureRepository closureRepository, ClinicOpeningHourRepository openingHourRepository) {
		this.vetRepository = vetRepository;
		this.weeklyBlockRepository = weeklyBlockRepository;
		this.exceptionRepository = exceptionRepository;
		this.leaveRepository = leaveRepository;
		this.closureRepository = closureRepository;
		this.openingHourRepository = openingHourRepository;
	}

	@Transactional(readOnly = true)
	public VetAvailabilityData getAvailabilityData(Integer vetId) {
		Vet vet = requireVet(vetId);
		List<VetWeeklyBlock> weeklyBlocks = this.weeklyBlockRepository.findByVetId(vetId)
			.stream()
			.sorted(Comparator.comparing(VetWeeklyBlock::getDayOfWeek).thenComparing(VetWeeklyBlock::getStartTime))
			.toList();
		List<VetException> exceptions = this.exceptionRepository.findByVetId(vetId)
			.stream()
			.sorted(Comparator.comparing(VetException::getExceptionDate)
				.thenComparing(e -> e.getStartTime() == null ? LocalTime.MIN : e.getStartTime()))
			.toList();
		List<VetLeave> leaves = this.leaveRepository.findByVetId(vetId)
			.stream()
			.sorted(Comparator.comparing(VetLeave::getStartDate))
			.toList();
		List<ClinicClosure> closures = this.closureRepository.findAll()
			.stream()
			.sorted(Comparator.comparing(ClinicClosure::getClosureDate))
			.toList();
		return new VetAvailabilityData(vet, weeklyBlocks, exceptions, leaves, closures);
	}

	public AvailabilityUpdateResult saveSchedule(Integer vetId, Map<DayOfWeek, List<TimeInterval>> schedule,
			List<VetExceptionDto> exceptions, List<VetLeaveDto> leaves, List<ClinicClosureDto> closures) {
		Vet vet = requireVet(vetId);
		boolean warning = hasOutOfHoursBlock(schedule);
		replaceSchedule(vet, schedule);
		replaceExceptions(vet, exceptions);
		replaceLeaves(vet, leaves);
		replaceClosures(closures);
		return new AvailabilityUpdateResult(warning);
	}

	@Transactional(readOnly = true)
	public List<TimeInterval> effectiveBlocks(Integer vetId, LocalDate date) {
		ClinicOpeningHour opening = this.openingHourRepository.findAll()
			.stream()
			.filter(row -> row.getDayOfWeek() == date.getDayOfWeek())
			.findFirst()
			.orElse(null);
		if (opening == null || opening.isClosed()
				|| this.closureRepository.findAll()
					.stream()
					.anyMatch(closure -> closure.getClosureDate().equals(date))) {
			return List.of();
		}
		if (this.leaveRepository.findByVetId(vetId)
			.stream()
			.anyMatch(leave -> !date.isBefore(leave.getStartDate()) && !date.isAfter(leave.getEndDate()))) {
			return List.of();
		}

		List<VetException> exceptions = this.exceptionRepository.findByVetId(vetId)
			.stream()
			.filter(exception -> exception.getExceptionDate().equals(date))
			.toList();
		List<TimeInterval> source;
		if (!exceptions.isEmpty()) {
			if (exceptions.stream().anyMatch(VetException::isUnavailable)) {
				return List.of();
			}
			source = exceptions.stream()
				.filter(exception -> exception.getStartTime() != null && exception.getEndTime() != null)
				.map(exception -> new TimeInterval(exception.getStartTime(), exception.getEndTime()))
				.toList();
		}
		else {
			source = this.weeklyBlockRepository.findByVetId(vetId)
				.stream()
				.filter(block -> block.getDayOfWeek() == date.getDayOfWeek())
				.map(block -> new TimeInterval(block.getStartTime(), block.getEndTime()))
				.toList();
		}
		return intersectWithOpeningHours(source, opening);
	}

	private void replaceSchedule(Vet vet, Map<DayOfWeek, List<TimeInterval>> schedule) {
		if (schedule == null) {
			return;
		}
		this.weeklyBlockRepository.deleteAll(this.weeklyBlockRepository.findByVetId(vet.getId()));
		for (Map.Entry<DayOfWeek, List<TimeInterval>> entry : schedule.entrySet()) {
			for (TimeInterval interval : entry.getValue()) {
				VetWeeklyBlock block = new VetWeeklyBlock();
				block.setVet(vet);
				block.setDayOfWeek(entry.getKey());
				block.setStartTime(interval.start());
				block.setEndTime(interval.end());
				this.weeklyBlockRepository.save(block);
			}
		}
	}

	private void replaceExceptions(Vet vet, List<VetExceptionDto> exceptions) {
		if (exceptions == null) {
			return;
		}
		this.exceptionRepository.deleteAll(this.exceptionRepository.findByVetId(vet.getId()));
		for (VetExceptionDto dto : exceptions) {
			VetException exception = new VetException();
			exception.setVet(vet);
			exception.setExceptionDate(dto.date());
			exception.setUnavailable(dto.unavailable());
			exception.setStartTime(dto.start());
			exception.setEndTime(dto.end());
			this.exceptionRepository.save(exception);
		}
	}

	private void replaceLeaves(Vet vet, List<VetLeaveDto> leaves) {
		if (leaves == null) {
			return;
		}
		this.leaveRepository.findByVetId(vet.getId()).forEach(this.leaveRepository::delete);
		for (VetLeaveDto dto : leaves) {
			VetLeave leave = new VetLeave();
			leave.setVet(vet);
			leave.setStartDate(dto.startDate());
			leave.setEndDate(dto.endDate());
			leave.setReason(dto.reason());
			this.leaveRepository.save(leave);
		}
	}

	private void replaceClosures(List<ClinicClosureDto> closures) {
		if (closures == null) {
			return;
		}
		this.closureRepository.findAll().forEach(this.closureRepository::delete);
		for (ClinicClosureDto dto : closures) {
			ClinicClosure closure = new ClinicClosure();
			closure.setClosureDate(dto.date());
			closure.setReason(dto.reason());
			this.closureRepository.save(closure);
		}
	}

	private boolean hasOutOfHoursBlock(Map<DayOfWeek, List<TimeInterval>> schedule) {
		if (schedule == null) {
			return false;
		}
		Map<DayOfWeek, ClinicOpeningHour> openings = new LinkedHashMap<>();
		this.openingHourRepository.findAll().forEach(row -> openings.put(row.getDayOfWeek(), row));
		for (Map.Entry<DayOfWeek, List<TimeInterval>> entry : schedule.entrySet()) {
			ClinicOpeningHour opening = openings.get(entry.getKey());
			for (TimeInterval interval : entry.getValue()) {
				if (opening == null || opening.isClosed() || interval.start().isBefore(opening.getOpenTime())
						|| interval.end().isAfter(opening.getCloseTime())) {
					return true;
				}
			}
		}
		return false;
	}

	private List<TimeInterval> intersectWithOpeningHours(List<TimeInterval> source, ClinicOpeningHour opening) {
		List<TimeInterval> effective = new ArrayList<>();
		for (TimeInterval interval : source) {
			LocalTime start = interval.start().isBefore(opening.getOpenTime()) ? opening.getOpenTime()
					: interval.start();
			LocalTime end = interval.end().isAfter(opening.getCloseTime()) ? opening.getCloseTime() : interval.end();
			if (start.isBefore(end)) {
				effective.add(new TimeInterval(start, end));
			}
		}
		effective.sort(Comparator.comparing(TimeInterval::start));
		return List.copyOf(effective);
	}

	private Vet requireVet(Integer vetId) {
		return this.vetRepository.findById(vetId)
			.orElseThrow(() -> new IllegalArgumentException("Vet not found: " + vetId));
	}

	public record TimeInterval(LocalTime start, LocalTime end) {
	}

	public record VetExceptionDto(LocalDate date, boolean unavailable, LocalTime start, LocalTime end) {
	}

	public record VetLeaveDto(LocalDate startDate, LocalDate endDate, String reason) {
	}

	public record ClinicClosureDto(LocalDate date, String reason) {
	}

	public record VetAvailabilityData(Vet vet, List<VetWeeklyBlock> weeklyBlocks, List<VetException> exceptions,
			List<VetLeave> leaves, List<ClinicClosure> closures) {
	}

	public record AvailabilityUpdateResult(boolean outOfHoursWarning) {
	}

}
