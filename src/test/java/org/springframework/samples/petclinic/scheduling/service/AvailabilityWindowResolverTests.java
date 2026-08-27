package org.springframework.samples.petclinic.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.scheduling.model.AvailabilityWindowKind;
import org.springframework.samples.petclinic.scheduling.model.CalendarExpressionType;
import org.springframework.samples.petclinic.scheduling.model.OrdinalWeek;
import org.springframework.samples.petclinic.scheduling.model.RequestAvailabilityWindow;

class AvailabilityWindowResolverTests {

	private static final Instant CONFIRMATION = Instant.parse("2026-08-27T10:33:05Z");

	private static final Instant HORIZON_END = Instant.parse("2026-10-22T00:00:00Z");

	private final AvailabilityWindowResolver resolver = resolver(ZoneId.of("UTC"));

	@Test
	void resolvesNextWeekThursdayAfterLunch() {
		RequestAvailabilityWindow window = window(CalendarExpressionType.RELATIVE_WEEK);
		window.setWeekOffset(1);
		window.setDayOfWeek(4);
		window.setStartTime(LocalTime.NOON);
		window.setEndTime(LocalTime.of(17, 0));

		assertThat(resolver.resolve(window, CONFIRMATION, HORIZON_END))
			.containsExactly(interval("2026-09-03T12:00:00Z", "2026-09-03T17:00:00Z"));
	}

	@Test
	void resolvesWholeNextMonth() {
		RequestAvailabilityWindow window = window(CalendarExpressionType.RELATIVE_MONTH);
		window.setMonthOffset(1);

		assertThat(resolver.resolve(window, CONFIRMATION, HORIZON_END))
			.containsExactly(interval("2026-09-01T00:00:00Z", "2026-10-01T00:00:00Z"));
	}

	@Test
	void resolvesNumberedFutureWeekAndMonthAsWholeCalendarRanges() {
		RequestAvailabilityWindow week = window(CalendarExpressionType.RELATIVE_WEEK);
		week.setWeekOffset(2);
		RequestAvailabilityWindow month = window(CalendarExpressionType.RELATIVE_MONTH);
		month.setMonthOffset(2);

		assertThat(resolver.resolve(week, CONFIRMATION, Instant.parse("2026-12-01T00:00:00Z")))
			.containsExactly(interval("2026-09-07T00:00:00Z", "2026-09-14T00:00:00Z"));
		assertThat(resolver.resolve(month, CONFIRMATION, Instant.parse("2026-12-01T00:00:00Z")))
			.containsExactly(interval("2026-10-01T00:00:00Z", "2026-11-01T00:00:00Z"));
	}

	@Test
	void resolvesSecondAndLastCalendarRowsClippedToMonth() {
		RequestAvailabilityWindow second = window(CalendarExpressionType.RELATIVE_MONTH);
		second.setMonthOffset(1);
		second.setOrdinalWeek(OrdinalWeek.SECOND);
		RequestAvailabilityWindow last = window(CalendarExpressionType.RELATIVE_MONTH);
		last.setMonthOffset(1);
		last.setOrdinalWeek(OrdinalWeek.LAST);

		assertThat(resolver.resolve(second, CONFIRMATION, HORIZON_END))
			.containsExactly(interval("2026-09-07T00:00:00Z", "2026-09-14T00:00:00Z"));
		assertThat(resolver.resolve(last, CONFIRMATION, HORIZON_END))
			.containsExactly(interval("2026-09-28T00:00:00Z", "2026-10-01T00:00:00Z"));
	}

	@Test
	void resolvesEndOfWorkingWeekFromThursdayAndClipsToFutureGrid() {
		RequestAvailabilityWindow window = window(CalendarExpressionType.END_OF_WEEK);
		window.setWeekOffset(0);

		assertThat(resolver.resolve(window, CONFIRMATION, HORIZON_END))
			.containsExactly(interval("2026-08-27T10:45:00Z", "2026-08-31T00:00:00Z"));
	}

	@Test
	void resolvesPastNamedMonthInNextYearAndThenClipsItOutsideHorizon() {
		RequestAvailabilityWindow window = window(CalendarExpressionType.NAMED_MONTH);
		window.setMonthOfYear(7);

		assertThat(resolver.resolve(window, CONFIRMATION, HORIZON_END)).isEmpty();
	}

	@Test
	void dropsAmbiguousLocalTimeAtDstOverlap() {
		AvailabilityWindowResolver amsterdamResolver = resolver(ZoneId.of("Europe/Amsterdam"));
		RequestAvailabilityWindow window = window(CalendarExpressionType.EXPLICIT_DATE);
		window.setExplicitDate(java.time.LocalDate.of(2026, 10, 25));
		window.setStartTime(LocalTime.of(2, 0));
		window.setEndTime(LocalTime.of(3, 0));

		assertThat(amsterdamResolver.resolve(window, Instant.parse("2026-08-27T10:33:05Z"),
				Instant.parse("2026-11-01T00:00:00Z")))
			.isEmpty();
	}

	@Test
	void clipsAllowedRangeAtBothHorizonBoundaries() {
		RequestAvailabilityWindow window = window(CalendarExpressionType.RELATIVE_MONTH);
		window.setMonthOffset(0);

		assertThat(resolver.resolve(window, CONFIRMATION, Instant.parse("2026-08-29T00:00:00Z")))
			.containsExactly(interval("2026-08-27T10:45:00Z", "2026-08-29T00:00:00Z"));
	}

	@Test
	void dropsNonexistentLocalTimeAtDstGap() {
		AvailabilityWindowResolver amsterdamResolver = resolver(ZoneId.of("Europe/Amsterdam"));
		RequestAvailabilityWindow window = window(CalendarExpressionType.EXPLICIT_DATE);
		window.setExplicitDate(java.time.LocalDate.of(2027, 3, 28));
		window.setStartTime(LocalTime.of(2, 0));
		window.setEndTime(LocalTime.of(3, 0));

		assertThat(amsterdamResolver.resolve(window, CONFIRMATION, Instant.parse("2027-04-01T00:00:00Z"))).isEmpty();
	}

	private AvailabilityWindowResolver resolver(ZoneId zoneId) {
		SchedulingTimeService timeService = new SchedulingTimeService(Clock.fixed(CONFIRMATION, zoneId), zoneId);
		return new AvailabilityWindowResolver(timeService, null, null);
	}

	private RequestAvailabilityWindow window(CalendarExpressionType expression) {
		RequestAvailabilityWindow window = new RequestAvailabilityWindow();
		window.setKind(AvailabilityWindowKind.ALLOWED);
		window.setExpressionType(expression);
		return window;
	}

	private AvailabilityWindowResolver.ResolvedInterval interval(String start, String end) {
		return new AvailabilityWindowResolver.ResolvedInterval(AvailabilityWindowKind.ALLOWED, Instant.parse(start),
				Instant.parse(end));
	}

}
