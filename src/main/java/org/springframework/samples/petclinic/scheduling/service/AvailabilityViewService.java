package org.springframework.samples.petclinic.scheduling.service;

import java.time.DayOfWeek;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.samples.petclinic.scheduling.dto.AvailabilityIntervalViewDto;
import org.springframework.samples.petclinic.scheduling.dto.AvailabilityWindowViewDto;
import org.springframework.samples.petclinic.scheduling.model.RequestAvailabilityWindow;
import org.springframework.samples.petclinic.scheduling.repository.RequestAvailabilityIntervalRepository;
import org.springframework.samples.petclinic.scheduling.repository.RequestAvailabilityWindowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvailabilityViewService {

	private static final DateTimeFormatter LOCAL_DATE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm VV");

	private final RequestAvailabilityWindowRepository windowRepository;

	private final RequestAvailabilityIntervalRepository intervalRepository;

	private final SchedulingTimeService timeService;

	public AvailabilityViewService(RequestAvailabilityWindowRepository windowRepository,
			RequestAvailabilityIntervalRepository intervalRepository, SchedulingTimeService timeService) {
		this.windowRepository = windowRepository;
		this.intervalRepository = intervalRepository;
		this.timeService = timeService;
	}

	@Transactional(readOnly = true)
	public List<AvailabilityWindowViewDto> getSymbolicWindows(Integer requestId) {
		return windowRepository.findByRequestIdOrderByWindowOrderAscIdAsc(requestId)
			.stream()
			.map(this::toView)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<AvailabilityIntervalViewDto> getResolvedIntervals(Integer requestId) {
		return intervalRepository.findByRequestIdOrderByStartInstantAscIdAsc(requestId)
			.stream()
			.map(interval -> new AvailabilityIntervalViewDto(interval.getKind().name(),
					LOCAL_DATE_TIME.format(interval.getStartInstant().atZone(timeService.getClinicZoneId())),
					LOCAL_DATE_TIME.format(interval.getEndInstant().atZone(timeService.getClinicZoneId()))))
			.toList();
	}

	private AvailabilityWindowViewDto toView(RequestAvailabilityWindow window) {
		List<String> calendar = new ArrayList<>();
		if (window.getExplicitDate() != null) {
			calendar.add(window.getExplicitDate().toString());
		}
		if (window.getWeekOffset() != null) {
			calendar.add("weekOffset=" + window.getWeekOffset());
		}
		if (window.getMonthOffset() != null) {
			calendar.add("monthOffset=" + window.getMonthOffset());
		}
		if (window.getMonthOfYear() != null) {
			calendar.add(Month.of(window.getMonthOfYear()).name());
		}
		if (window.getOrdinalWeek() != null) {
			calendar.add(window.getOrdinalWeek().name());
		}
		if (window.getDayOfWeek() != null) {
			calendar.add(DayOfWeek.of(window.getDayOfWeek()).name());
		}

		List<String> time = new ArrayList<>();
		if (window.getNamedPeriod() != null) {
			time.add(window.getNamedPeriod());
		}
		if (window.getStartTime() != null && window.getEndTime() != null) {
			time.add(window.getStartTime() + "-" + window.getEndTime());
		}
		return new AvailabilityWindowViewDto(window.getWindowOrder(), window.getKind().name(),
				window.getExpressionType().name(), String.join(", ", calendar), String.join(", ", time));
	}

}
