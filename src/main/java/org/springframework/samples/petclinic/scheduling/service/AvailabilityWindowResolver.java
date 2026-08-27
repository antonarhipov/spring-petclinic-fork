package org.springframework.samples.petclinic.scheduling.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.samples.petclinic.scheduling.model.AvailabilityWindowKind;
import org.springframework.samples.petclinic.scheduling.model.OrdinalWeek;
import org.springframework.samples.petclinic.scheduling.model.RequestAvailabilityInterval;
import org.springframework.samples.petclinic.scheduling.model.RequestAvailabilityWindow;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.repository.RequestAvailabilityIntervalRepository;
import org.springframework.samples.petclinic.scheduling.repository.RequestAvailabilityWindowRepository;
import org.springframework.stereotype.Service;

@Service
public class AvailabilityWindowResolver {

	public enum MaterializationResult {

		SUCCESS, OUTSIDE_HORIZON

	}

	public record ResolvedInterval(AvailabilityWindowKind kind, Instant start, Instant end) {
	}

	private final SchedulingTimeService timeService;

	private final RequestAvailabilityWindowRepository windowRepository;

	private final RequestAvailabilityIntervalRepository intervalRepository;

	public AvailabilityWindowResolver(SchedulingTimeService timeService,
			RequestAvailabilityWindowRepository windowRepository,
			RequestAvailabilityIntervalRepository intervalRepository) {
		this.timeService = timeService;
		this.windowRepository = windowRepository;
		this.intervalRepository = intervalRepository;
	}

	public MaterializationResult materialize(SchedulingRequest request, Instant confirmationInstant,
			Instant horizonEnd) {
		Instant horizonStart = timeService.nextGridBoundary(confirmationInstant);
		List<RequestAvailabilityWindow> windows = windowRepository
			.findByRequestIdOrderByWindowOrderAscIdAsc(request.getId());
		boolean hasHardAllowedWindow = windows.stream().anyMatch(w -> w.getKind() == AvailabilityWindowKind.ALLOWED);
		List<ResolvedWithSource> resolved = new ArrayList<>();
		for (RequestAvailabilityWindow window : windows) {
			for (ResolvedInterval interval : resolve(window, confirmationInstant, horizonEnd)) {
				resolved.add(new ResolvedWithSource(window, interval));
			}
		}
		if (hasHardAllowedWindow
				&& resolved.stream().noneMatch(item -> item.interval().kind() == AvailabilityWindowKind.ALLOWED)) {
			return MaterializationResult.OUTSIDE_HORIZON;
		}

		intervalRepository.deleteByRequestId(request.getId());
		resolved.sort(Comparator.comparing(item -> item.interval().start()));
		for (ResolvedWithSource item : resolved) {
			RequestAvailabilityInterval entity = new RequestAvailabilityInterval();
			entity.setRequest(request);
			entity.setSourceWindow(item.source());
			entity.setKind(item.interval().kind());
			entity.setStartInstant(item.interval().start());
			entity.setEndInstant(item.interval().end());
			intervalRepository.save(entity);
		}
		return MaterializationResult.SUCCESS;
	}

	public List<ResolvedInterval> resolve(RequestAvailabilityWindow window, Instant confirmationInstant,
			Instant horizonEnd) {
		Instant horizonStart = timeService.nextGridBoundary(confirmationInstant);
		LocalDate anchor = confirmationInstant.atZone(timeService.getClinicZoneId()).toLocalDate();
		DateRange baseRange = baseRange(window, anchor, horizonStart, horizonEnd);
		if (window.getOrdinalWeek() != null) {
			baseRange = ordinalRange(baseRange, window.getOrdinalWeek());
		}
		if (baseRange == null || baseRange.start().isAfter(baseRange.end())) {
			return List.of();
		}

		List<ResolvedInterval> result = new ArrayList<>();
		boolean perDay = window.getDayOfWeek() != null || window.getStartTime() != null;
		if (perDay) {
			for (LocalDate date = baseRange.start(); !date.isAfter(baseRange.end()); date = date.plusDays(1)) {
				if (window.getDayOfWeek() == null || date.getDayOfWeek().getValue() == window.getDayOfWeek()) {
					addClipped(result, window.getKind(), date, window.getStartTime(), window.getEndTime(), horizonStart,
							horizonEnd);
				}
			}
		}
		else {
			addClipped(result, window.getKind(), baseRange.start(), LocalTime.MIN, null, horizonStart, horizonEnd,
					baseRange.end().plusDays(1));
		}
		return result;
	}

	private DateRange baseRange(RequestAvailabilityWindow window, LocalDate anchor, Instant horizonStart,
			Instant horizonEnd) {
		return switch (window.getExpressionType()) {
			case ANY_DATE -> new DateRange(horizonStart.atZone(timeService.getClinicZoneId()).toLocalDate(),
					horizonEnd.minusNanos(1).atZone(timeService.getClinicZoneId()).toLocalDate());
			case EXPLICIT_DATE -> new DateRange(window.getExplicitDate(), window.getExplicitDate());
			case RELATIVE_WEEK -> weekRange(anchor, window.getWeekOffset());
			case END_OF_WEEK -> {
				DateRange week = weekRange(anchor, window.getWeekOffset());
				yield new DateRange(week.start().with(TemporalAdjusters.nextOrSame(DayOfWeek.THURSDAY)), week.end());
			}
			case RELATIVE_MONTH -> monthRange(YearMonth.from(anchor).plusMonths(window.getMonthOffset()));
			case NAMED_MONTH -> monthRange(namedMonth(anchor, window.getMonthOfYear()));
		};
	}

	private DateRange weekRange(LocalDate anchor, int offset) {
		LocalDate monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset);
		return new DateRange(monday, monday.plusDays(6));
	}

	private YearMonth namedMonth(LocalDate anchor, int monthValue) {
		YearMonth candidate = YearMonth.of(anchor.getYear(), Month.of(monthValue));
		return candidate.atEndOfMonth().isBefore(anchor) ? candidate.plusYears(1) : candidate;
	}

	private DateRange monthRange(YearMonth month) {
		return new DateRange(month.atDay(1), month.atEndOfMonth());
	}

	private DateRange ordinalRange(DateRange monthRange, OrdinalWeek ordinal) {
		LocalDate monthStart = monthRange.start();
		LocalDate monthEnd = monthRange.end();
		if (ordinal == OrdinalWeek.LAST) {
			return new DateRange(monthEnd.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), monthEnd);
		}
		int index = ordinal.ordinal();
		LocalDate firstRowEnd = monthStart.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
		LocalDate start = index == 0 ? monthStart : firstRowEnd.plusDays(1).plusWeeks(index - 1L);
		LocalDate end = start.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
		return new DateRange(start, end.isAfter(monthEnd) ? monthEnd : end);
	}

	private void addClipped(List<ResolvedInterval> result, AvailabilityWindowKind kind, LocalDate date,
			LocalTime startTime, LocalTime endTime, Instant horizonStart, Instant horizonEnd) {
		addClipped(result, kind, date, startTime != null ? startTime : LocalTime.MIN, endTime, horizonStart, horizonEnd,
				date.plusDays(1));
	}

	private void addClipped(List<ResolvedInterval> result, AvailabilityWindowKind kind, LocalDate date,
			LocalTime startTime, LocalTime endTime, Instant horizonStart, Instant horizonEnd, LocalDate endDate) {
		LocalDateTime localStart = LocalDateTime.of(date, startTime);
		LocalDateTime localEnd = endTime != null ? LocalDateTime.of(date, endTime) : endDate.atStartOfDay();
		Instant start = uniqueInstant(localStart);
		Instant end = uniqueInstant(localEnd);
		if (start == null || end == null) {
			return;
		}
		if (start.isBefore(horizonStart)) {
			start = horizonStart;
		}
		if (end.isAfter(horizonEnd)) {
			end = horizonEnd;
		}
		if (start.isBefore(end)) {
			result.add(new ResolvedInterval(kind, start, end));
		}
	}

	private Instant uniqueInstant(LocalDateTime localDateTime) {
		List<java.time.ZoneOffset> offsets = timeService.getClinicZoneId().getRules().getValidOffsets(localDateTime);
		return offsets.size() == 1 ? localDateTime.toInstant(offsets.getFirst()) : null;
	}

	private record DateRange(LocalDate start, LocalDate end) {
	}

	private record ResolvedWithSource(RequestAvailabilityWindow source, ResolvedInterval interval) {
	}

}
