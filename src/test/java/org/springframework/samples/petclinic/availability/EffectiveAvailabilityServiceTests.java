package org.springframework.samples.petclinic.availability;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.samples.petclinic.shared.TimeInterval;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EffectiveAvailabilityServiceTests {

	@Autowired
	private EffectiveAvailabilityService effectiveAvailabilityService;

	@Autowired
	private AvailabilityAdministrationService availabilityAdministrationService;

	@Autowired
	private RecurringShiftRepository recurringShiftRepository;

	@Autowired
	private AvailabilityExceptionDayRepository availabilityExceptionDayRepository;

	@Autowired
	private VeterinarianLeaveRepository veterinarianLeaveRepository;

	@Autowired
	private ClinicClosureRepository clinicClosureRepository;

	private final Integer vetId = 1;

	// 2026-09-07 is a Monday
	private final LocalDate monday = LocalDate.of(2026, 9, 7);

	@BeforeEach
	void setUp() {
		// Clean up non-baseline records for clean test isolation
		this.veterinarianLeaveRepository.deleteAll();
		this.clinicClosureRepository.deleteAll();
		this.availabilityExceptionDayRepository.deleteAll();
	}

	@Test
	void recurringShiftGivesDefaultAvailability() {
		List<LocalTimeInterval> localIntervals = this.effectiveAvailabilityService
			.getEffectiveLocalAvailability(this.vetId, this.monday);
		assertThat(localIntervals).hasSize(1);
		assertThat(localIntervals.get(0).start()).isEqualTo(LocalTime.of(9, 0));
		assertThat(localIntervals.get(0).end()).isEqualTo(LocalTime.of(17, 0));

		List<TimeInterval> intervals = this.effectiveAvailabilityService.getEffectiveAvailability(this.vetId,
				this.monday);
		assertThat(intervals).hasSize(1);
		ZoneId zoneId = this.effectiveAvailabilityService.getClinicZoneId();
		assertThat(intervals.get(0).getStart()).isEqualTo(this.monday.atTime(9, 0).atZone(zoneId).toInstant());
		assertThat(intervals.get(0).getEnd()).isEqualTo(this.monday.atTime(17, 0).atZone(zoneId).toInstant());
	}

	@Test
	void exceptionDayReplacesRecurringShift() {
		// Exception day: vet works only 10:00 - 13:00 on this Monday
		this.availabilityAdministrationService.setAvailabilityExceptionDay(this.vetId, this.monday,
				List.of(new LocalTimeInterval(LocalTime.of(10, 0), LocalTime.of(13, 0))), 1L);

		List<LocalTimeInterval> localIntervals = this.effectiveAvailabilityService
			.getEffectiveLocalAvailability(this.vetId, this.monday);
		assertThat(localIntervals).hasSize(1);
		assertThat(localIntervals.get(0).start()).isEqualTo(LocalTime.of(10, 0));
		assertThat(localIntervals.get(0).end()).isEqualTo(LocalTime.of(13, 0));
	}

	@Test
	void exceptionDayWithZeroIntervalsMakesVetUnavailableAllDay() {
		// Vet is unavailable all day
		this.availabilityAdministrationService.setAvailabilityExceptionDay(this.vetId, this.monday, List.of(), 1L);

		List<LocalTimeInterval> localIntervals = this.effectiveAvailabilityService
			.getEffectiveLocalAvailability(this.vetId, this.monday);
		assertThat(localIntervals).isEmpty();
	}

	@Test
	void veterinarianLeaveOverridesExceptionDayAndRecurringShift() {
		// Set exception day first
		this.availabilityAdministrationService.setAvailabilityExceptionDay(this.vetId, this.monday,
				List.of(new LocalTimeInterval(LocalTime.of(10, 0), LocalTime.of(13, 0))), 1L);

		// Schedule leave for vet covering this date
		this.availabilityAdministrationService.createVeterinarianLeave(this.vetId, this.monday, this.monday.plusDays(2),
				"VACATION", "Vacation note", 1L);

		List<LocalTimeInterval> localIntervals = this.effectiveAvailabilityService
			.getEffectiveLocalAvailability(this.vetId, this.monday);
		assertThat(localIntervals).isEmpty();
	}

	@Test
	void clinicClosureOverridesAllAvailability() {
		// Clinic is closed on this Monday
		this.availabilityAdministrationService.createClinicClosure(this.monday, this.monday, "National Holiday",
				"Closure note", 1L);

		List<LocalTimeInterval> localIntervals = this.effectiveAvailabilityService
			.getEffectiveLocalAvailability(this.vetId, this.monday);
		assertThat(localIntervals).isEmpty();
	}

	@Test
	void isAvailableEnforcesExactIntervalEnclosure() {
		ZoneId zoneId = this.effectiveAvailabilityService.getClinicZoneId();

		// 10:00 - 10:30 on Monday is within 09:00 - 17:00
		boolean available = this.effectiveAvailabilityService.isAvailable(this.vetId,
				this.monday.atTime(10, 0).atZone(zoneId).toInstant(),
				this.monday.atTime(10, 30).atZone(zoneId).toInstant());
		assertThat(available).isTrue();

		// 08:30 - 09:30 crosses outside shift start
		boolean availableEarly = this.effectiveAvailabilityService.isAvailable(this.vetId,
				this.monday.atTime(8, 30).atZone(zoneId).toInstant(),
				this.monday.atTime(9, 30).atZone(zoneId).toInstant());
		assertThat(availableEarly).isFalse();

		// 16:30 - 17:30 crosses outside shift end
		boolean availableLate = this.effectiveAvailabilityService.isAvailable(this.vetId,
				this.monday.atTime(16, 30).atZone(zoneId).toInstant(),
				this.monday.atTime(17, 30).atZone(zoneId).toInstant());
		assertThat(availableLate).isFalse();
	}

}
