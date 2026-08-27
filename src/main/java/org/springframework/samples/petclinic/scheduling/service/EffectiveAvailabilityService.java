package org.springframework.samples.petclinic.scheduling.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.springframework.samples.petclinic.scheduling.dto.TimeInterval;
import org.springframework.samples.petclinic.scheduling.model.ClinicClosure;
import org.springframework.samples.petclinic.scheduling.model.ScheduleExceptionType;
import org.springframework.samples.petclinic.scheduling.model.VetSchedule;
import org.springframework.samples.petclinic.scheduling.model.VetScheduleException;
import org.springframework.samples.petclinic.scheduling.repository.ClinicClosureRepository;
import org.springframework.samples.petclinic.scheduling.repository.VetScheduleExceptionRepository;
import org.springframework.samples.petclinic.scheduling.repository.VetScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EffectiveAvailabilityService {

	private final VetScheduleRepository vetScheduleRepository;

	private final VetScheduleExceptionRepository vetScheduleExceptionRepository;

	private final ClinicClosureRepository clinicClosureRepository;

	private final SchedulingTimeService timeService;

	public EffectiveAvailabilityService(VetScheduleRepository vetScheduleRepository,
			VetScheduleExceptionRepository vetScheduleExceptionRepository,
			ClinicClosureRepository clinicClosureRepository, SchedulingTimeService timeService) {
		this.vetScheduleRepository = vetScheduleRepository;
		this.vetScheduleExceptionRepository = vetScheduleExceptionRepository;
		this.clinicClosureRepository = clinicClosureRepository;
		this.timeService = timeService;
	}

	@Transactional(readOnly = true)
	public List<TimeInterval> getEffectiveSlots(Integer vetId, LocalDate startDate, LocalDate endDate) {
		if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
			return Collections.emptyList();
		}

		List<TimeInterval> allSlots = new ArrayList<>();
		LocalDate curr = startDate;
		while (!curr.isAfter(endDate)) {
			allSlots.addAll(getEffectiveSlotsForDate(vetId, curr));
			curr = curr.plusDays(1);
		}
		return allSlots;
	}

	@Transactional(readOnly = true)
	public List<TimeInterval> getEffectiveSlotsForDate(Integer vetId, LocalDate date) {
		// 1. Clinic closure precedence
		List<ClinicClosure> closures = clinicClosureRepository
			.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(date, date);
		if (!closures.isEmpty()) {
			return Collections.emptyList();
		}

		// 2. Vet leave exception precedence
		List<VetScheduleException> exceptions = vetScheduleExceptionRepository
			.findByVetIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(vetId, date, date);
		boolean onLeave = exceptions.stream().anyMatch(e -> e.getExceptionType() == ScheduleExceptionType.LEAVE);
		if (onLeave) {
			return Collections.emptyList();
		}

		// 3. Base working hours (Modified hours takes precedence over weekly recurring)
		List<LocalTimeRange> ranges = new ArrayList<>();
		List<VetScheduleException> modifiedHours = exceptions.stream()
			.filter(e -> e.getExceptionType() == ScheduleExceptionType.MODIFIED_HOURS)
			.toList();

		if (!modifiedHours.isEmpty()) {
			for (VetScheduleException mod : modifiedHours) {
				if (mod.getStartTime() != null && mod.getEndTime() != null
						&& mod.getStartTime().isBefore(mod.getEndTime())) {
					ranges.add(new LocalTimeRange(mod.getStartTime(), mod.getEndTime()));
				}
			}
		}
		else {
			List<VetSchedule> weeklySchedules = vetScheduleRepository.findByVetIdAndDayOfWeek(vetId,
					date.getDayOfWeek().getValue());
			for (VetSchedule sched : weeklySchedules) {
				if (sched.getStartTime() != null && sched.getEndTime() != null
						&& sched.getStartTime().isBefore(sched.getEndTime())) {
					ranges.add(new LocalTimeRange(sched.getStartTime(), sched.getEndTime()));
				}
			}
		}

		// 4. Extra hours union
		List<VetScheduleException> extraHours = exceptions.stream()
			.filter(e -> e.getExceptionType() == ScheduleExceptionType.EXTRA_HOURS)
			.toList();
		for (VetScheduleException extra : extraHours) {
			if (extra.getStartTime() != null && extra.getEndTime() != null
					&& extra.getStartTime().isBefore(extra.getEndTime())) {
				ranges.add(new LocalTimeRange(extra.getStartTime(), extra.getEndTime()));
			}
		}

		if (ranges.isEmpty()) {
			return Collections.emptyList();
		}

		// 5. Merge overlapping or adjacent local ranges
		List<LocalTimeRange> mergedRanges = mergeLocalTimeRanges(ranges);

		// 6. Quantize into 15-minute slots with DST validation
		List<TimeInterval> slots = new ArrayList<>();
		for (LocalTimeRange range : mergedRanges) {
			LocalTime time = range.start();
			while (!time.plusMinutes(15).isAfter(range.end()) && !time.plusMinutes(15).isBefore(time)) {
				LocalTime next = time.plusMinutes(15);
				LocalDateTime slotStart = LocalDateTime.of(date, time);
				LocalDateTime slotEnd = LocalDateTime.of(date, next);

				if (timeService.isValidOffset(slotStart) && timeService.isValidOffset(slotEnd)) {
					Instant startInstant = slotStart.atZone(timeService.getClinicZoneId()).toInstant();
					Instant endInstant = slotEnd.atZone(timeService.getClinicZoneId()).toInstant();
					slots.add(new TimeInterval(startInstant, endInstant, vetId));
				}
				time = next;
			}
		}

		return slots;
	}

	@Transactional(readOnly = true)
	public List<TimeInterval> getMergedEffectiveIntervals(Integer vetId, LocalDate startDate, LocalDate endDate) {
		List<TimeInterval> slots = getEffectiveSlots(vetId, startDate, endDate);
		if (slots.isEmpty()) {
			return Collections.emptyList();
		}

		Collections.sort(slots);
		List<TimeInterval> merged = new ArrayList<>();
		TimeInterval current = slots.get(0);

		for (int i = 1; i < slots.size(); i++) {
			TimeInterval next = slots.get(i);
			if (current.getEnd().equals(next.getStart())) {
				current = new TimeInterval(current.getStart(), next.getEnd(), vetId);
			}
			else {
				merged.add(current);
				current = next;
			}
		}
		merged.add(current);
		return merged;
	}

	@Transactional(readOnly = true)
	public List<TimeInterval> calculateEffectiveAvailability(Integer vetId, Instant startTime, Instant endTime) {
		if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
			return Collections.emptyList();
		}
		LocalDate startDate = startTime.atZone(timeService.getClinicZoneId()).toLocalDate();
		LocalDate endDate = endTime.atZone(timeService.getClinicZoneId()).toLocalDate();
		return getMergedEffectiveIntervals(vetId, startDate, endDate);
	}

	@Transactional(readOnly = true)
	public boolean isVetAvailable(Integer vetId, Instant startTime, Instant endTime) {
		if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
			return false;
		}
		LocalDate startDate = startTime.atZone(timeService.getClinicZoneId()).toLocalDate();
		LocalDate endDate = endTime.atZone(timeService.getClinicZoneId()).toLocalDate();

		List<TimeInterval> availableIntervals = getMergedEffectiveIntervals(vetId, startDate, endDate);
		for (TimeInterval interval : availableIntervals) {
			if (interval.contains(startTime, endTime)) {
				return true;
			}
		}
		return false;
	}

	private List<LocalTimeRange> mergeLocalTimeRanges(List<LocalTimeRange> ranges) {
		if (ranges.isEmpty()) {
			return ranges;
		}
		List<LocalTimeRange> sorted = new ArrayList<>(ranges);
		sorted.sort(Comparator.comparing(LocalTimeRange::start).thenComparing(LocalTimeRange::end));

		List<LocalTimeRange> result = new ArrayList<>();
		LocalTimeRange current = sorted.get(0);

		for (int i = 1; i < sorted.size(); i++) {
			LocalTimeRange next = sorted.get(i);
			if (!current.end().isBefore(next.start())) {
				// Overlap or adjacent -> merge
				LocalTime mergedEnd = current.end().isAfter(next.end()) ? current.end() : next.end();
				current = new LocalTimeRange(current.start(), mergedEnd);
			}
			else {
				result.add(current);
				current = next;
			}
		}
		result.add(current);
		return result;
	}

	public record LocalTimeRange(LocalTime start, LocalTime end) {
	}

}
