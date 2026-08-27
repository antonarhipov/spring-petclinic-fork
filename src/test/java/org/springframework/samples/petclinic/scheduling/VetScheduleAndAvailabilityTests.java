package org.springframework.samples.petclinic.scheduling;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.samples.petclinic.scheduling.dto.TimeInterval;
import org.springframework.samples.petclinic.scheduling.model.Appointment;
import org.springframework.samples.petclinic.scheduling.model.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.model.CalendarConflict;
import org.springframework.samples.petclinic.scheduling.model.ClinicClosure;
import org.springframework.samples.petclinic.scheduling.model.ConflictStatus;
import org.springframework.samples.petclinic.scheduling.model.ConflictType;
import org.springframework.samples.petclinic.scheduling.model.ScheduleExceptionType;
import org.springframework.samples.petclinic.scheduling.model.ShiftType;
import org.springframework.samples.petclinic.scheduling.model.VetSchedule;
import org.springframework.samples.petclinic.scheduling.model.VetScheduleException;
import org.springframework.samples.petclinic.scheduling.repository.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.repository.CalendarConflictRepository;
import org.springframework.samples.petclinic.scheduling.repository.ClinicClosureRepository;
import org.springframework.samples.petclinic.scheduling.repository.VetScheduleExceptionRepository;
import org.springframework.samples.petclinic.scheduling.repository.VetScheduleRepository;
import org.springframework.samples.petclinic.scheduling.service.AuditService;
import org.springframework.samples.petclinic.scheduling.service.CalendarConflictService;
import org.springframework.samples.petclinic.scheduling.service.EffectiveAvailabilityService;
import org.springframework.samples.petclinic.scheduling.service.SchedulingTimeService;
import org.springframework.samples.petclinic.vet.Vet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VetScheduleAndAvailabilityTests {

	@Mock
	private VetScheduleRepository vetScheduleRepository;

	@Mock
	private VetScheduleExceptionRepository vetScheduleExceptionRepository;

	@Mock
	private ClinicClosureRepository clinicClosureRepository;

	@Mock
	private AppointmentRepository appointmentRepository;

	@Mock
	private CalendarConflictRepository calendarConflictRepository;

	@Mock
	private AuditService auditService;

	private SchedulingTimeService timeService;

	private EffectiveAvailabilityService availabilityService;

	private CalendarConflictService conflictService;

	private final ZoneId zoneId = ZoneId.of("UTC");

	private final Clock clock = Clock.fixed(Instant.parse("2026-08-27T10:00:00Z"), zoneId);

	@BeforeEach
	void setUp() {
		timeService = new SchedulingTimeService(clock, zoneId);
		availabilityService = new EffectiveAvailabilityService(vetScheduleRepository, vetScheduleExceptionRepository,
				clinicClosureRepository, timeService);
		conflictService = new CalendarConflictService(calendarConflictRepository, appointmentRepository,
				availabilityService, auditService, timeService);
	}

	@Test
	void testEffectiveAvailabilityRegularWeeklySchedule() {
		LocalDate thursday = LocalDate.of(2026, 8, 27); // Thursday (day 4)
		Integer vetId = 1;

		VetSchedule schedule = new VetSchedule();
		schedule.setDayOfWeekValue(4);
		schedule.setStartTime(LocalTime.of(9, 0));
		schedule.setEndTime(LocalTime.of(12, 0));
		schedule.setShiftType(ShiftType.REGULAR);

		when(clinicClosureRepository.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(thursday, thursday))
			.thenReturn(Collections.emptyList());
		when(vetScheduleExceptionRepository.findByVetIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(vetId,
				thursday, thursday))
			.thenReturn(Collections.emptyList());
		when(vetScheduleRepository.findByVetIdAndDayOfWeek(vetId, 4)).thenReturn(List.of(schedule));

		List<TimeInterval> slots = availabilityService.getEffectiveSlotsForDate(vetId, thursday);

		// 09:00 to 12:00 in 15-minute intervals -> 12 slots
		assertThat(slots).hasSize(12);
		assertThat(slots.get(0).getStart()).isEqualTo(Instant.parse("2026-08-27T09:00:00Z"));
		assertThat(slots.get(slots.size() - 1).getEnd()).isEqualTo(Instant.parse("2026-08-27T12:00:00Z"));
	}

	@Test
	void testEffectiveAvailabilityClinicClosureOverridesAll() {
		LocalDate date = LocalDate.of(2026, 8, 27);
		Integer vetId = 1;

		ClinicClosure closure = new ClinicClosure();
		closure.setStartDate(date);
		closure.setEndDate(date);
		closure.setReason("Holiday");

		when(clinicClosureRepository.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(date, date))
			.thenReturn(List.of(closure));

		List<TimeInterval> slots = availabilityService.getEffectiveSlotsForDate(vetId, date);
		assertThat(slots).isEmpty();
	}

	@Test
	void testEffectiveAvailabilityLeaveOverridesWeeklySchedule() {
		LocalDate date = LocalDate.of(2026, 8, 27);
		Integer vetId = 1;

		VetScheduleException leave = new VetScheduleException();
		leave.setStartDate(date);
		leave.setEndDate(date);
		leave.setExceptionType(ScheduleExceptionType.LEAVE);

		when(clinicClosureRepository.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(date, date))
			.thenReturn(Collections.emptyList());
		when(vetScheduleExceptionRepository.findByVetIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(vetId, date,
				date))
			.thenReturn(List.of(leave));

		List<TimeInterval> slots = availabilityService.getEffectiveSlotsForDate(vetId, date);
		assertThat(slots).isEmpty();
	}

	@Test
	void testEffectiveAvailabilityModifiedHoursAndExtraHours() {
		LocalDate date = LocalDate.of(2026, 8, 27);
		Integer vetId = 1;

		VetScheduleException mod = new VetScheduleException();
		mod.setStartDate(date);
		mod.setEndDate(date);
		mod.setStartTime(LocalTime.of(10, 0));
		mod.setEndTime(LocalTime.of(12, 0));
		mod.setExceptionType(ScheduleExceptionType.MODIFIED_HOURS);

		VetScheduleException extra = new VetScheduleException();
		extra.setStartDate(date);
		extra.setEndDate(date);
		extra.setStartTime(LocalTime.of(14, 0));
		extra.setEndTime(LocalTime.of(16, 0));
		extra.setExceptionType(ScheduleExceptionType.EXTRA_HOURS);

		when(clinicClosureRepository.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(date, date))
			.thenReturn(Collections.emptyList());
		when(vetScheduleExceptionRepository.findByVetIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(vetId, date,
				date))
			.thenReturn(List.of(mod, extra));

		List<TimeInterval> slots = availabilityService.getEffectiveSlotsForDate(vetId, date);
		// 10:00-12:00 (8 slots) + 14:00-16:00 (8 slots) = 16 slots
		assertThat(slots).hasSize(16);
	}

	@Test
	void testCalendarConflictWithoutReasonIsRejected() {
		LocalDate date = LocalDate.of(2026, 8, 27);
		Vet vet = new Vet();
		vet.setId(1);

		Appointment booked = new Appointment();
		booked.setId(10);
		booked.setVet(vet);
		booked.setStatus(AppointmentStatus.BOOKED);
		booked.setStartTime(Instant.parse("2026-08-27T10:00:00Z"));
		booked.setEndTime(Instant.parse("2026-08-27T10:30:00Z"));

		when(appointmentRepository.findAll()).thenReturn(List.of(booked));

		// Missing reason must throw IllegalArgumentException
		assertThatThrownBy(() -> conflictService.detectAndRecordClosureConflicts(date, date, "", "staff"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Mandatory reason required");
	}

	@Test
	void testCalendarConflictWithReasonIsRecordedNonDestructively() {
		LocalDate date = LocalDate.of(2026, 8, 27);
		Vet vet = new Vet();
		vet.setId(1);

		Appointment booked = new Appointment();
		booked.setId(10);
		booked.setVet(vet);
		booked.setStatus(AppointmentStatus.BOOKED);
		booked.setStartTime(Instant.parse("2026-08-27T10:00:00Z"));
		booked.setEndTime(Instant.parse("2026-08-27T10:30:00Z"));

		when(appointmentRepository.findAll()).thenReturn(List.of(booked));
		when(calendarConflictRepository.findByAppointmentId(10)).thenReturn(Collections.emptyList());

		conflictService.detectAndRecordClosureConflicts(date, date, "Emergency Maintenance", "staff");

		// Conflict saved with UNRESOLVED status
		verify(calendarConflictRepository).save(any(CalendarConflict.class));
		// Appointment status was NOT changed
		assertThat(booked.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
	}

}
