package org.springframework.samples.petclinic.scheduling.availability;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.appointment.Hold;
import org.springframework.samples.petclinic.scheduling.appointment.HoldRepository;
import org.springframework.samples.petclinic.scheduling.appointment.HoldStatus;
import org.springframework.samples.petclinic.scheduling.appointment.Offer;
import org.springframework.samples.petclinic.scheduling.appointment.OfferRepository;
import org.springframework.samples.petclinic.scheduling.appointment.OfferStatus;
import org.springframework.samples.petclinic.scheduling.appointment.ReservationBlock;
import org.springframework.samples.petclinic.scheduling.appointment.ReservationBlockRepository;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AvailabilityCommandServiceTests {

	@Test
	void leaveOverlappingConfirmedAppointmentIsRejected() {
		AvailabilityConflictGuard guard = guardWithAppointment();
		AvailabilityCommandService service = service(guard);
		assertThatThrownBy(() -> service.addLeave(1, LocalDate.parse("2026-03-16"), LocalDate.parse("2026-03-16"), 9L))
			.isInstanceOf(AvailabilityConflictException.class);
	}

	@Test
	void shiftIsSavedOnFifteenMinuteGrid() {
		VetRecurringShiftRepository shifts = mock(VetRecurringShiftRepository.class);
		when(shifts.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));
		AvailabilityCommandService service = new AvailabilityCommandService(shifts,
				mock(VetDateExceptionRepository.class), mock(VetLeaveRepository.class),
				mock(ClinicClosureRepository.class), policies(), mock(AvailabilityConflictGuard.class), versions(),
				mock(CapacityAuditService.class), mock(HoldRepository.class), mock(OfferRepository.class),
				mock(ReservationBlockRepository.class));
		VetRecurringShift shift = service.addShift(1, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), 2L);
		assertThat(shift.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
		assertThatThrownBy(() -> service.addShift(1, DayOfWeek.MONDAY, LocalTime.of(9, 7), LocalTime.of(12, 0), 2L))
			.isInstanceOf(PolicyValidationException.class);
	}

	@Test
	void veterinarianAvailableUsesShiftCoverage() {
		VetRecurringShiftRepository shifts = mock(VetRecurringShiftRepository.class);
		VetRecurringShift shift = new VetRecurringShift();
		shift.setVeterinarianId(1);
		shift.setDayOfWeek(DayOfWeek.MONDAY);
		shift.setStartLocalTime(LocalTime.of(9, 0));
		shift.setEndLocalTime(LocalTime.of(17, 0));
		when(shifts.findByVeterinarianId(1)).thenReturn(List.of(shift));
		when(mock(VetDateExceptionRepository.class).findByVeterinarianIdAndExceptionDate(
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
			.thenReturn(Optional.empty());
		VetDateExceptionRepository exceptions = mock(VetDateExceptionRepository.class);
		when(exceptions.findByVeterinarianIdAndExceptionDate(org.mockito.ArgumentMatchers.eq(1),
				org.mockito.ArgumentMatchers.any()))
			.thenReturn(Optional.empty());
		ClinicClosureRepository closures = mock(ClinicClosureRepository.class);
		when(closures.findByPolicyId(1L)).thenReturn(List.of());
		VetLeaveRepository leaves = mock(VetLeaveRepository.class);
		when(leaves.findByVeterinarianId(1)).thenReturn(List.of());
		AvailabilityCommandService service = new AvailabilityCommandService(shifts, exceptions, leaves, closures,
				policies(), mock(AvailabilityConflictGuard.class), versions(), mock(CapacityAuditService.class),
				mock(HoldRepository.class), mock(OfferRepository.class), mock(ReservationBlockRepository.class));
		Instant start = Instant.parse("2026-03-16T13:00:00Z");
		Instant end = Instant.parse("2026-03-16T13:30:00Z");
		assertThat(service.veterinarianAvailable(1, start, end)).isTrue();
	}

	@Test
	void closureBeatsLeaveExceptionAndShift() {
		AvailabilityCommandService service = availabilityWith(shift(), Optional.empty(), List.of(leave()),
				List.of(closure()));
		assertThat(service.veterinarianAvailable(1, Instant.parse("2026-03-16T13:00:00Z"),
				Instant.parse("2026-03-16T13:30:00Z")))
			.isFalse();
	}

	@Test
	void leaveBeatsExceptionAndShift() {
		AvailabilityCommandService service = availabilityWith(shift(), Optional.of(exception(List.of(interval(9, 17)))),
				List.of(leave()), List.of());
		assertThat(service.veterinarianAvailable(1, Instant.parse("2026-03-16T13:00:00Z"),
				Instant.parse("2026-03-16T13:30:00Z")))
			.isFalse();
	}

	@Test
	void dateExceptionOverridesRecurringShiftIncludingZeroIntervals() {
		AvailabilityCommandService zero = availabilityWith(shift(), Optional.of(exception(List.of())), List.of(),
				List.of());
		assertThat(zero.veterinarianAvailable(1, Instant.parse("2026-03-16T13:00:00Z"),
				Instant.parse("2026-03-16T13:30:00Z")))
			.isFalse();
		AvailabilityCommandService eveningOnly = availabilityWith(shift(),
				Optional.of(exception(List.of(interval(15, 17)))), List.of(), List.of());
		assertThat(eveningOnly.veterinarianAvailable(1, Instant.parse("2026-03-16T13:00:00Z"),
				Instant.parse("2026-03-16T13:30:00Z")))
			.isFalse();
	}

	@Test
	void splitShiftsCoverOnlyMatchingIntervals() {
		VetRecurringShift morning = shift();
		morning.setEndLocalTime(LocalTime.of(12, 0));
		VetRecurringShift afternoon = shift();
		afternoon.setStartLocalTime(LocalTime.of(13, 0));
		AvailabilityCommandService service = availabilityWith(List.of(morning, afternoon), Optional.empty(), List.of(),
				List.of());
		assertThat(service.veterinarianAvailable(1, Instant.parse("2026-03-16T16:00:00Z"),
				Instant.parse("2026-03-16T16:30:00Z")))
			.isTrue();
		assertThat(service.veterinarianAvailable(1, Instant.parse("2026-03-16T17:00:00Z"),
				Instant.parse("2026-03-16T17:30:00Z")))
			.isFalse();
	}

	@Test
	void slotMustStayOnSameLocalDay() {
		AvailabilityCommandService service = availabilityWith(shift(), Optional.empty(), List.of(), List.of());
		assertThat(service.veterinarianAvailable(1, Instant.parse("2026-03-16T23:00:00Z"),
				Instant.parse("2026-03-17T00:30:00Z")))
			.isFalse();
	}

	@Test
	void dstSafeResolutionUsesClinicZoneLocalTimes() {
		AvailabilityRepository policies = mock(AvailabilityRepository.class);
		ClinicSchedulingPolicy policy = new ClinicSchedulingPolicy();
		ReflectionTestUtils.setField(policy, "id", 1L);
		policy.setZoneId("America/Chicago");
		when(policies.currentPolicy()).thenReturn(policy);
		VetRecurringShiftRepository shifts = mock(VetRecurringShiftRepository.class);
		VetRecurringShift friday = shift();
		friday.setDayOfWeek(DayOfWeek.FRIDAY);
		when(shifts.findByVeterinarianId(1)).thenReturn(List.of(friday, shift()));
		VetDateExceptionRepository exceptions = mock(VetDateExceptionRepository.class);
		when(exceptions.findByVeterinarianIdAndExceptionDate(org.mockito.ArgumentMatchers.eq(1),
				org.mockito.ArgumentMatchers.any()))
			.thenReturn(Optional.empty());
		AvailabilityCommandService service = new AvailabilityCommandService(shifts, exceptions, emptyLeaves(),
				emptyClosures(), policies, mock(AvailabilityConflictGuard.class), versions(policies),
				mock(CapacityAuditService.class), mock(HoldRepository.class), mock(OfferRepository.class),
				mock(ReservationBlockRepository.class));
		ZoneId chicago = ZoneId.of("America/Chicago");
		Instant beforeDst = ZonedDateTime.of(2026, 3, 6, 9, 0, 0, 0, chicago).toInstant();
		Instant afterDst = ZonedDateTime.of(2026, 3, 9, 9, 0, 0, 0, chicago).toInstant();
		assertThat(service.veterinarianAvailable(1, beforeDst, beforeDst.plusSeconds(1800))).isTrue();
		assertThat(service.veterinarianAvailable(1, afterDst, afterDst.plusSeconds(1800))).isTrue();
		assertThat(beforeDst).isNotEqualTo(afterDst.minusSeconds(2 * 24 * 3600));
	}

	@Test
	void addDateExceptionCanDeliberatelyReleaseOverlappingHolds() {
		AvailabilityConflictGuard guard = mock(AvailabilityConflictGuard.class);
		when(guard.findConflicts(org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any()))
			.thenReturn(List.of("HOLD:9"));
		HoldRepository holds = mock(HoldRepository.class);
		Hold hold = new Hold();
		ReflectionTestUtils.setField(hold, "id", 9L);
		hold.setOfferId(11L);
		hold.setState(HoldStatus.ACTIVE);
		when(holds.findById(9L)).thenReturn(Optional.of(hold));
		ReservationBlockRepository blocks = mock(ReservationBlockRepository.class);
		ReservationBlock block = new ReservationBlock();
		when(blocks.findByHoldId(9L)).thenReturn(List.of(block));
		OfferRepository offers = mock(OfferRepository.class);
		Offer offer = new Offer();
		offer.setStatus(OfferStatus.HELD);
		when(offers.findById(11L)).thenReturn(Optional.of(offer));
		VetDateExceptionRepository exceptions = mock(VetDateExceptionRepository.class);
		when(exceptions.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));
		AvailabilityCommandService service = new AvailabilityCommandService(mock(VetRecurringShiftRepository.class),
				exceptions, mock(VetLeaveRepository.class), mock(ClinicClosureRepository.class), policies(), guard,
				versions(), mock(CapacityAuditService.class), holds, offers, blocks);
		service.addDateException(1, LocalDate.parse("2026-03-16"), List.of(), 4L, true);
		verify(blocks).deleteAll(List.of(block));
		assertThat(hold.getState()).isEqualTo(HoldStatus.RELEASED);
		assertThat(hold.getReleaseReason()).isEqualTo("CAPACITY_CHANGE");
		assertThat(offer.getStatus()).isEqualTo(OfferStatus.EXPIRED);
		verify(guard, org.mockito.Mockito.never()).assertNoConflicts(org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	private AvailabilityCommandService service(AvailabilityConflictGuard guard) {
		return new AvailabilityCommandService(mock(VetRecurringShiftRepository.class),
				mock(VetDateExceptionRepository.class), mock(VetLeaveRepository.class),
				mock(ClinicClosureRepository.class), policies(), guard, versions(), mock(CapacityAuditService.class),
				mock(HoldRepository.class), mock(OfferRepository.class), mock(ReservationBlockRepository.class));
	}

	private AvailabilityConflictGuard guardWithAppointment() {
		AppointmentRepository appointments = mock(AppointmentRepository.class);
		Appointment appointment = new Appointment();
		ReflectionTestUtils.setField(appointment, "id", 44L);
		appointment.setVeterinarianId(1);
		appointment.setStartAt(Instant.parse("2026-03-16T13:00:00Z"));
		appointment.setEndAt(Instant.parse("2026-03-16T13:30:00Z"));
		when(appointments.findByStatus(AppointmentStatus.CONFIRMED)).thenReturn(List.of(appointment));
		HoldRepository holds = mock(HoldRepository.class);
		when(holds.findByState(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
		return new AvailabilityConflictGuard(appointments, holds, mock(OfferRepository.class), policies());
	}

	private AvailabilityRepository policies() {
		AvailabilityRepository policies = mock(AvailabilityRepository.class);
		ClinicSchedulingPolicy policy = new ClinicSchedulingPolicy();
		ReflectionTestUtils.setField(policy, "id", 1L);
		policy.setZoneId("UTC");
		policy.setConfigurationVersion(1);
		when(policies.currentPolicy()).thenReturn(policy);
		when(policies.save(policy)).thenReturn(policy);
		return policies;
	}

	private ConfigurationVersionService versions() {
		return versions(policies());
	}

	private ConfigurationVersionService versions(AvailabilityRepository policies) {
		return new ConfigurationVersionService(policies, java.time.Clock.systemUTC());
	}

	private AvailabilityCommandService availabilityWith(VetRecurringShift shift, Optional<VetDateException> exception,
			List<VetLeave> leaves, List<ClinicClosure> closures) {
		return availabilityWith(List.of(shift), exception, leaves, closures);
	}

	private AvailabilityCommandService availabilityWith(List<VetRecurringShift> shifts,
			Optional<VetDateException> exception, List<VetLeave> leaves, List<ClinicClosure> closures) {
		VetRecurringShiftRepository shiftRepo = mock(VetRecurringShiftRepository.class);
		when(shiftRepo.findByVeterinarianId(1)).thenReturn(shifts);
		VetDateExceptionRepository exceptions = mock(VetDateExceptionRepository.class);
		when(exceptions.findByVeterinarianIdAndExceptionDate(org.mockito.ArgumentMatchers.eq(1),
				org.mockito.ArgumentMatchers.any()))
			.thenReturn(exception);
		VetLeaveRepository leaveRepo = mock(VetLeaveRepository.class);
		when(leaveRepo.findByVeterinarianId(1)).thenReturn(leaves);
		ClinicClosureRepository closureRepo = mock(ClinicClosureRepository.class);
		when(closureRepo.findByPolicyId(1L)).thenReturn(closures);
		return new AvailabilityCommandService(shiftRepo, exceptions, leaveRepo, closureRepo, policies(),
				mock(AvailabilityConflictGuard.class), versions(), mock(CapacityAuditService.class),
				mock(HoldRepository.class), mock(OfferRepository.class), mock(ReservationBlockRepository.class));
	}

	private VetRecurringShift shift() {
		VetRecurringShift shift = new VetRecurringShift();
		shift.setVeterinarianId(1);
		shift.setDayOfWeek(DayOfWeek.MONDAY);
		shift.setStartLocalTime(LocalTime.of(9, 0));
		shift.setEndLocalTime(LocalTime.of(17, 0));
		return shift;
	}

	private VetLeave leave() {
		VetLeave leave = new VetLeave();
		leave.setVeterinarianId(1);
		leave.setStartLocalDate(LocalDate.parse("2026-03-16"));
		leave.setEndLocalDate(LocalDate.parse("2026-03-16"));
		return leave;
	}

	private ClinicClosure closure() {
		ClinicClosure closure = new ClinicClosure();
		closure.setPolicyId(1L);
		closure.setStartLocalDate(LocalDate.parse("2026-03-16"));
		closure.setEndLocalDate(LocalDate.parse("2026-03-16"));
		return closure;
	}

	private VetDateException exception(List<VetDateExceptionInterval> intervals) {
		VetDateException exception = new VetDateException();
		exception.setVeterinarianId(1);
		exception.setExceptionDate(LocalDate.parse("2026-03-16"));
		exception.getIntervals().addAll(intervals);
		return exception;
	}

	private VetDateExceptionInterval interval(int startHour, int endHour) {
		VetDateExceptionInterval interval = new VetDateExceptionInterval();
		interval.setStartLocalTime(LocalTime.of(startHour, 0));
		interval.setEndLocalTime(LocalTime.of(endHour, 0));
		return interval;
	}

	private VetLeaveRepository emptyLeaves() {
		VetLeaveRepository leaves = mock(VetLeaveRepository.class);
		when(leaves.findByVeterinarianId(1)).thenReturn(List.of());
		return leaves;
	}

	private ClinicClosureRepository emptyClosures() {
		ClinicClosureRepository closures = mock(ClinicClosureRepository.class);
		when(closures.findByPolicyId(1L)).thenReturn(List.of());
		return closures;
	}

}
