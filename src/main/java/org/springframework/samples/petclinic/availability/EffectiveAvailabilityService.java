package org.springframework.samples.petclinic.availability;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.samples.petclinic.shared.TimeInterval;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EffectiveAvailabilityService {

	private final ClinicPolicyRepository clinicPolicyRepository;

	private final ClinicClosureRepository clinicClosureRepository;

	private final VeterinarianLeaveRepository veterinarianLeaveRepository;

	private final AvailabilityExceptionDayRepository availabilityExceptionDayRepository;

	private final RecurringShiftRepository recurringShiftRepository;

	public EffectiveAvailabilityService(ClinicPolicyRepository clinicPolicyRepository,
			ClinicClosureRepository clinicClosureRepository, VeterinarianLeaveRepository veterinarianLeaveRepository,
			AvailabilityExceptionDayRepository availabilityExceptionDayRepository,
			RecurringShiftRepository recurringShiftRepository) {
		this.clinicPolicyRepository = clinicPolicyRepository;
		this.clinicClosureRepository = clinicClosureRepository;
		this.veterinarianLeaveRepository = veterinarianLeaveRepository;
		this.availabilityExceptionDayRepository = availabilityExceptionDayRepository;
		this.recurringShiftRepository = recurringShiftRepository;
	}

	public ClinicPolicy getClinicPolicy() {
		return this.clinicPolicyRepository.findSingleton()
			.orElseThrow(() -> new IllegalStateException("Clinic policy not initialized"));
	}

	public ZoneId getClinicZoneId() {
		return ZoneId.of(getClinicPolicy().getZoneId());
	}

	/**
	 * Computes effective local time intervals for a veterinarian on a given date by
	 * applying strict precedence: 1. ClinicClosure -> Empty 2. VeterinarianLeave -> Empty
	 * 3. AvailabilityExceptionDay -> Replacement intervals (0 intervals means
	 * unavailable) 4. RecurringShift -> Shifts for that weekday Then intersects with
	 * ClinicPolicy operating intervals for that weekday.
	 */
	public List<LocalTimeInterval> getEffectiveLocalAvailability(Integer vetId, LocalDate date) {
		// 1. Clinic closure precedence
		List<ClinicClosure> activeClosures = this.clinicClosureRepository.findActiveClosuresOnDate(date);
		if (!activeClosures.isEmpty()) {
			return Collections.emptyList();
		}

		// 2. Veterinarian leave precedence
		List<VeterinarianLeave> activeLeaves = this.veterinarianLeaveRepository.findActiveLeaveForVetOnDate(vetId,
				date);
		if (!activeLeaves.isEmpty()) {
			return Collections.emptyList();
		}

		ClinicPolicy policy = getClinicPolicy();
		DayOfWeek weekday = date.getDayOfWeek();
		List<LocalTimeInterval> operatingIntervals = policy.getOperatingIntervals()
			.stream()
			.filter(oi -> oi.getWeekday() == weekday)
			.map(oi -> new LocalTimeInterval(oi.getLocalStart(), oi.getLocalEnd()))
			.toList();

		if (operatingIntervals.isEmpty()) {
			return Collections.emptyList();
		}

		// 3. Availability exception day (replacement schedule)
		Optional<AvailabilityExceptionDay> exceptionDayOpt = this.availabilityExceptionDayRepository
			.findByVetIdAndLocalDate(vetId, date);

		List<LocalTimeInterval> rawIntervals = new ArrayList<>();
		if (exceptionDayOpt.isPresent()) {
			AvailabilityExceptionDay exceptionDay = exceptionDayOpt.get();
			for (AvailabilityExceptionInterval interval : exceptionDay.getIntervals()) {
				rawIntervals.add(new LocalTimeInterval(interval.getLocalStart(), interval.getLocalEnd()));
			}
		}
		else {
			// 4. Recurring shifts for weekday
			List<RecurringShift> shifts = this.recurringShiftRepository.findByVetIdAndWeekday(vetId, weekday);
			for (RecurringShift shift : shifts) {
				rawIntervals.add(new LocalTimeInterval(shift.getLocalStart(), shift.getLocalEnd()));
			}
		}

		if (rawIntervals.isEmpty()) {
			return Collections.emptyList();
		}

		// Intersect raw intervals with clinic operating hours
		List<LocalTimeInterval> intersected = new ArrayList<>();
		for (LocalTimeInterval raw : rawIntervals) {
			for (LocalTimeInterval op : operatingIntervals) {
				raw.intersection(op).ifPresent(intersected::add);
			}
		}

		return mergeAdjacentOrOverlapping(intersected);
	}

	/**
	 * Computes effective availability as UTC {@link TimeInterval}s for a veterinarian on
	 * a given date.
	 */
	public List<TimeInterval> getEffectiveAvailability(Integer vetId, LocalDate date) {
		ZoneId zoneId = getClinicZoneId();
		List<LocalTimeInterval> localIntervals = getEffectiveLocalAvailability(vetId, date);
		List<TimeInterval> intervals = new ArrayList<>();
		for (LocalTimeInterval local : localIntervals) {
			Instant start = ZonedDateTime.of(date, local.start(), zoneId).toInstant();
			Instant end = ZonedDateTime.of(date, local.end(), zoneId).toInstant();
			intervals.add(TimeInterval.of(start, end));
		}
		return intervals;
	}

	public List<TimeInterval> findEffectiveAvailability(Integer vetId, LocalDate date) {
		return getEffectiveAvailability(vetId, date);
	}

	/**
	 * Computes effective availability across a date range [startDate, endDate] inclusive.
	 */
	public List<TimeInterval> getEffectiveAvailability(Integer vetId, LocalDate startDate, LocalDate endDate) {
		List<TimeInterval> intervals = new ArrayList<>();
		LocalDate curr = startDate;
		while (!curr.isAfter(endDate)) {
			intervals.addAll(getEffectiveAvailability(vetId, curr));
			curr = curr.plusDays(1);
		}
		return intervals;
	}

	/**
	 * Computes effective availability for multiple veterinarians across a date range.
	 */
	public Map<Integer, List<TimeInterval>> getEffectiveAvailabilityForAllVets(LocalDate startDate, LocalDate endDate,
			List<Integer> vetIds) {
		Map<Integer, List<TimeInterval>> result = new LinkedHashMap<>();
		for (Integer vetId : vetIds) {
			result.put(vetId, getEffectiveAvailability(vetId, startDate, endDate));
		}
		return result;
	}

	/**
	 * Returns true if the proposed time interval is completely enclosed within one of the
	 * veterinarian's effective availability intervals on that date.
	 */
	public boolean isAvailable(Integer vetId, Instant startAt, Instant endAt) {
		if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
			return false;
		}
		ZoneId zoneId = getClinicZoneId();
		LocalDate startDate = ZonedDateTime.ofInstant(startAt, zoneId).toLocalDate();
		LocalDate endDate = ZonedDateTime.ofInstant(endAt, zoneId).toLocalDate();

		if (!startDate.equals(endDate)) {
			// Appointments crossing midnight are not allowed by policy
			return false;
		}

		TimeInterval candidate = TimeInterval.of(startAt, endAt);
		List<TimeInterval> effective = getEffectiveAvailability(vetId, startDate);
		for (TimeInterval interval : effective) {
			if (interval.encloses(candidate)) {
				return true;
			}
		}
		return false;
	}

	private List<LocalTimeInterval> mergeAdjacentOrOverlapping(List<LocalTimeInterval> intervals) {
		if (intervals.isEmpty()) {
			return Collections.emptyList();
		}
		List<LocalTimeInterval> sorted = new ArrayList<>(intervals);
		sorted.sort(Comparator.naturalOrder());

		List<LocalTimeInterval> merged = new ArrayList<>();
		LocalTime currentStart = sorted.get(0).start();
		LocalTime currentEnd = sorted.get(0).end();

		for (int i = 1; i < sorted.size(); i++) {
			LocalTimeInterval next = sorted.get(i);
			if (!next.start().isAfter(currentEnd)) { // overlaps or is adjacent
				if (next.end().isAfter(currentEnd)) {
					currentEnd = next.end();
				}
			}
			else {
				merged.add(new LocalTimeInterval(currentStart, currentEnd));
				currentStart = next.start();
				currentEnd = next.end();
			}
		}
		merged.add(new LocalTimeInterval(currentStart, currentEnd));
		return merged;
	}

}
