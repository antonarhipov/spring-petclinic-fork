package org.springframework.samples.petclinic.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.samples.petclinic.scheduling.dto.AvailabilityIntervalViewDto;
import org.springframework.samples.petclinic.scheduling.dto.AvailabilityWindowViewDto;
import org.springframework.samples.petclinic.scheduling.model.AvailabilityWindowKind;
import org.springframework.samples.petclinic.scheduling.model.CalendarExpressionType;
import org.springframework.samples.petclinic.scheduling.model.RequestAvailabilityInterval;
import org.springframework.samples.petclinic.scheduling.model.RequestAvailabilityWindow;
import org.springframework.samples.petclinic.scheduling.repository.RequestAvailabilityIntervalRepository;
import org.springframework.samples.petclinic.scheduling.repository.RequestAvailabilityWindowRepository;

@ExtendWith(MockitoExtension.class)
class AvailabilityViewServiceTests {

	@Mock
	private RequestAvailabilityWindowRepository windowRepository;

	@Mock
	private RequestAvailabilityIntervalRepository intervalRepository;

	private AvailabilityViewService service;

	@BeforeEach
	void setUp() {
		ZoneId clinicZone = ZoneId.of("Europe/Tallinn");
		SchedulingTimeService timeService = new SchedulingTimeService(
				Clock.fixed(Instant.parse("2026-08-27T10:00:00Z"), clinicZone), clinicZone);
		service = new AvailabilityViewService(windowRepository, intervalRepository, timeService);
	}

	@Test
	void projectsSymbolicParametersAndResolvedClinicLocalTimes() {
		RequestAvailabilityWindow window = new RequestAvailabilityWindow();
		window.setWindowOrder(0);
		window.setKind(AvailabilityWindowKind.ALLOWED);
		window.setExpressionType(CalendarExpressionType.RELATIVE_WEEK);
		window.setWeekOffset(1);
		window.setDayOfWeek(4);
		window.setNamedPeriod("AFTERNOON");
		window.setStartTime(LocalTime.NOON);
		window.setEndTime(LocalTime.of(17, 0));
		RequestAvailabilityInterval interval = new RequestAvailabilityInterval();
		interval.setKind(AvailabilityWindowKind.ALLOWED);
		interval.setStartInstant(Instant.parse("2026-09-03T09:00:00Z"));
		interval.setEndInstant(Instant.parse("2026-09-03T14:00:00Z"));
		when(windowRepository.findByRequestIdOrderByWindowOrderAscIdAsc(100)).thenReturn(List.of(window));
		when(intervalRepository.findByRequestIdOrderByStartInstantAscIdAsc(100)).thenReturn(List.of(interval));

		assertThat(service.getSymbolicWindows(100)).containsExactly(new AvailabilityWindowViewDto(0, "ALLOWED",
				"RELATIVE_WEEK", "weekOffset=1, THURSDAY", "AFTERNOON, 12:00-17:00"));
		assertThat(service.getResolvedIntervals(100)).containsExactly(new AvailabilityIntervalViewDto("ALLOWED",
				"2026-09-03 12:00 Europe/Tallinn", "2026-09-03 17:00 Europe/Tallinn"));
	}

}
