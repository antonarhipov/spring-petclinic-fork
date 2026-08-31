package org.springframework.samples.petclinic.appointment;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.samples.petclinic.appointment.DirectBookingService.DirectBookingRequest;
import org.springframework.samples.petclinic.appointment.StaffAppointmentCancellationService.StaffCancellationCommand;
import org.springframework.samples.petclinic.appointment.StaffAppointmentReschedulingService.RescheduleAppointmentCommand;
import org.springframework.samples.petclinic.audit.ProtectedPayloadRepository;
import org.springframework.samples.petclinic.audit.ProtectedPayloadService;
import org.springframework.samples.petclinic.availability.ClinicPolicy;
import org.springframework.samples.petclinic.availability.ClinicPolicyRepository;
import org.springframework.samples.petclinic.availability.EffectiveAvailabilityService;
import org.springframework.samples.petclinic.availability.RecurringShift;
import org.springframework.samples.petclinic.availability.RecurringShiftRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StaffAppointmentConvergenceTests {

	@Autowired
	private DirectBookingService directBookingService;

	@Autowired
	private StaffAppointmentReschedulingService reschedulingService;

	@Autowired
	private StaffAppointmentCancellationService cancellationService;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private AppointmentChangeEventRepository changeEventRepository;

	@Autowired
	private RecurringShiftRepository recurringShiftRepository;

	@Autowired
	private ClinicPolicyRepository clinicPolicyRepository;

	@Autowired
	private EffectiveAvailabilityService effectiveAvailabilityService;

	@Autowired
	private ProtectedPayloadRepository payloadRepository;

	@Autowired
	private ProtectedPayloadService payloadService;

	@Autowired
	private Clock clock;

	@Test
	void staffBookingMayUseFutureSlotInsideOwnerNoticeWindow() {
		Instant startAt = nextStaffSlotInsideOwnerNotice();
		Instant endAt = startAt.plus(Duration.ofMinutes(30));
		ensureFullDayShift(1, startAt);

		Appointment saved = this.directBookingService.bookDirectly(new DirectBookingRequest(1, 1, 1, startAt, endAt,
				true, "PHONE", "OWNER_REQUEST", "Owner called for the earliest available slot", 1L, null));

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getStartAt()).isEqualTo(startAt);
	}

	@Test
	void rescheduleExcludesCurrentAppointmentAndPersistsStructuredBeforeAfterSnapshot() {
		Instant startAt = futureSlotAtTen();
		Instant endAt = startAt.plus(Duration.ofMinutes(30));
		ensureFullDayShift(1, startAt);
		Appointment appointment = this.appointmentRepository
			.saveAndFlush(new Appointment(1, 1, 1, startAt, endAt, clinicZone().getId()));

		Appointment saved = this.reschedulingService.rescheduleAppointment(new RescheduleAppointmentCommand(
				appointment.getId(), 1L, 1, startAt, endAt, true, "PHONE", "OWNER_REQUEST",
				"Owner reconfirmed the existing slot", "Your appointment time remains confirmed."));

		assertThat(saved.getStartAt()).isEqualTo(startAt);
		AppointmentChangeEvent event = this.changeEventRepository.findByAppointmentIdOrderByOccurredAtAsc(saved.getId())
			.stream()
			.filter(candidate -> "APPOINTMENT_RESCHEDULED".equals(candidate.getEventType()))
			.findFirst()
			.orElseThrow();
		assertThat(event.getProtectedSnapshotPayloadId()).isNotNull();
		String snapshot = this.payloadService
			.decryptToString(this.payloadRepository.findById(event.getProtectedSnapshotPayloadId()).orElseThrow());
		assertThat(snapshot).contains("\"before\"", "\"after\"", startAt.toString());
	}

	@Test
	void staffCancellationWithoutPriorAgreementRequiresAndRecordsContactAttempt() {
		Instant startAt = futureSlotAtTen();
		Appointment appointment = this.appointmentRepository.saveAndFlush(
				new Appointment(1, 1, 1, startAt, startAt.plus(Duration.ofMinutes(30)), clinicZone().getId()));
		StaffCancellationCommand withoutContact = new StaffCancellationCommand(appointment.getId(), 1L,
				"VET_UNAVAILABLE", "Veterinarian became unavailable", "The clinic needs to cancel this visit.", false);

		assertThatThrownBy(() -> this.cancellationService.cancelAppointmentByStaff(withoutContact))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("contact attempt");

		this.cancellationService.cancelAppointmentByStaff(new StaffCancellationCommand(appointment.getId(), 1L,
				"VET_UNAVAILABLE", "Veterinarian became unavailable", "The clinic needs to cancel this visit.", true));
		assertThat(this.changeEventRepository.findByAppointmentIdOrderByOccurredAtAsc(appointment.getId()))
			.extracting(AppointmentChangeEvent::getEventType)
			.contains("OWNER_CONTACT_ATTEMPT", "STAFF_CANCELLED");
	}

	@Test
	void staffCancellationRejectsUnknownReasonCategory() {
		Instant startAt = futureSlotAtTen();
		Appointment appointment = this.appointmentRepository.saveAndFlush(
				new Appointment(1, 1, 1, startAt, startAt.plus(Duration.ofMinutes(30)), clinicZone().getId()));

		assertThatThrownBy(() -> this.cancellationService.cancelAppointmentByStaff(
				new StaffCancellationCommand(appointment.getId(), 1L, "MADE_UP", "Reason", "Owner explanation", true)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("valid cancellation reason");
	}

	private Instant nextStaffSlotInsideOwnerNotice() {
		ClinicPolicy policy = this.effectiveAvailabilityService.getClinicPolicy();
		policy.setOwnerNoticeMinutes(10000);
		this.clinicPolicyRepository.saveAndFlush(policy);
		return futureSlotAtTen();
	}

	private Instant futureSlotAtTen() {
		LocalDate date = LocalDate.now(this.clock.withZone(clinicZone())).plusDays(3);
		return date.atTime(10, 0).atZone(clinicZone()).toInstant();
	}

	private void ensureFullDayShift(Integer vetId, Instant instant) {
		DayOfWeek weekday = ZonedDateTime.ofInstant(instant, clinicZone()).getDayOfWeek();
		this.recurringShiftRepository.save(new RecurringShift(vetId, weekday, LocalTime.MIN, LocalTime.of(23, 59)));
	}

	private ZoneId clinicZone() {
		return this.effectiveAvailabilityService.getClinicZoneId();
	}

}
