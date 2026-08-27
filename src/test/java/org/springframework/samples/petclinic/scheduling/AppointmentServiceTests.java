package org.springframework.samples.petclinic.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.PetRepository;
import org.springframework.samples.petclinic.owner.Visit;
import org.springframework.samples.petclinic.owner.VisitRepository;
import org.springframework.samples.petclinic.scheduling.dto.AppointmentDto;
import org.springframework.samples.petclinic.scheduling.model.Appointment;
import org.springframework.samples.petclinic.scheduling.model.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.model.AuditEventType;
import org.springframework.samples.petclinic.scheduling.model.BookingSource;
import org.springframework.samples.petclinic.scheduling.model.CareType;
import org.springframework.samples.petclinic.scheduling.model.ClinicSettings;
import org.springframework.samples.petclinic.scheduling.model.ConflictStatus;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.repository.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.repository.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.service.AppointmentService;
import org.springframework.samples.petclinic.scheduling.service.AuditService;
import org.springframework.samples.petclinic.scheduling.service.CalendarConflictService;
import org.springframework.samples.petclinic.scheduling.service.ClinicSettingsService;
import org.springframework.samples.petclinic.scheduling.service.LiveFeasibilityService;
import org.springframework.samples.petclinic.scheduling.service.LockCoordinator;
import org.springframework.samples.petclinic.scheduling.service.NotificationService;
import org.springframework.samples.petclinic.scheduling.service.SchedulingTimeService;
import org.springframework.samples.petclinic.system.ResourceNotFoundException;
import org.springframework.samples.petclinic.vet.Specialty;
import org.springframework.samples.petclinic.vet.SpecialtyRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTests {

	@Mock
	private AppointmentRepository appointmentRepository;

	@Mock
	private OwnerRepository ownerRepository;

	@Mock
	private PetRepository petRepository;

	@Mock
	private VetRepository vetRepository;

	@Mock
	private SpecialtyRepository specialtyRepository;

	@Mock
	private SchedulingRequestRepository schedulingRequestRepository;

	@Mock
	private VisitRepository visitRepository;

	@Mock
	private LockCoordinator lockCoordinator;

	@Mock
	private LiveFeasibilityService liveFeasibilityService;

	@Mock
	private CalendarConflictService calendarConflictService;

	@Mock
	private ClinicSettingsService clinicSettingsService;

	@Mock
	private SchedulingTimeService timeService;

	@Mock
	private AuditService auditService;

	@Mock
	private NotificationService notificationService;

	private AppointmentService appointmentService;

	private final Instant NOW = Instant.parse("2026-08-28T10:00:00Z");

	private Owner owner;

	private Pet pet;

	private Vet vet;

	private ClinicSettings settings;

	@BeforeEach
	void setUp() {
		appointmentService = new AppointmentService(appointmentRepository, ownerRepository, petRepository,
				vetRepository, specialtyRepository, schedulingRequestRepository, visitRepository, lockCoordinator,
				liveFeasibilityService, calendarConflictService, clinicSettingsService, timeService, auditService,
				notificationService);

		owner = new Owner();
		owner.setId(1);
		owner.setFirstName("George");
		owner.setLastName("Franklin");

		pet = new Pet();
		pet.setId(1);
		pet.setName("Leo");

		vet = new Vet();
		vet.setId(1);
		vet.setFirstName("James");
		vet.setLastName("Carter");
		vet.setActive(true);

		settings = new ClinicSettings();
		settings.setMinDurationMinutes(15);
		settings.setDefaultDurationMinutes(30);
		settings.setMaxDurationMinutes(120);
		settings.setStaffBookingHorizonDays(90);
		settings.setOwnerBookingHorizonDays(30);
	}

	@Test
	void testGetOwnerUpcomingAppointments() {
		given(timeService.now()).willReturn(NOW);
		Appointment appt = new Appointment();
		appt.setId(1);
		appt.setOwner(owner);
		appt.setPet(pet);
		appt.setVet(vet);
		appt.setStartTime(NOW.plus(Duration.ofHours(2)));
		appt.setEndTime(NOW.plus(Duration.ofHours(3)));
		appt.setStatus(AppointmentStatus.BOOKED);
		appt.setCareType(CareType.GENERAL);

		given(appointmentRepository.findByOwnerIdAndStatusAndStartTimeGreaterThanEqualOrderByStartTimeAsc(1,
				AppointmentStatus.BOOKED, NOW))
			.willReturn(List.of(appt));

		List<AppointmentDto> result = appointmentService.getOwnerUpcomingAppointments(1);
		assertThat(result).hasSize(1);
		assertThat(result.get(0).getId()).isEqualTo(1);
		assertThat(result.get(0).getPetName()).isEqualTo("Leo");
	}

	@Test
	void testCancelAppointmentByOwnerSuccess() {
		given(timeService.now()).willReturn(NOW);
		Appointment appt = new Appointment();
		appt.setId(1);
		appt.setOwner(owner);
		appt.setPet(pet);
		appt.setVet(vet);
		appt.setStartTime(NOW.plus(Duration.ofHours(2)));
		appt.setEndTime(NOW.plus(Duration.ofHours(3)));
		appt.setStatus(AppointmentStatus.BOOKED);
		appt.setCareType(CareType.GENERAL);

		given(appointmentRepository.findByIdAndOwnerId(1, 1)).willReturn(Optional.of(appt));
		given(appointmentRepository.findById(1)).willReturn(Optional.of(appt));
		given(appointmentRepository.save(any(Appointment.class))).willAnswer(inv -> inv.getArgument(0));

		AppointmentDto cancelled = appointmentService.cancelAppointmentByOwner(1, 1, "Schedule conflict", "owner1");
		assertThat(cancelled.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
		assertThat(cancelled.getCancellationReason()).isEqualTo("Schedule conflict");
		verify(calendarConflictService).resolveConflictsForAppointment(1, ConflictStatus.CANCELLED, "owner1");
		verify(auditService).recordEvent(eq(AuditEventType.APPOINTMENT_CANCELLED), any(), any(), any(), any(), any(),
				any());
	}

	@Test
	void testCancelAppointmentByOwnerFailsIfPastStartTime() {
		given(timeService.now()).willReturn(NOW);
		Appointment appt = new Appointment();
		appt.setId(1);
		appt.setOwner(owner);
		appt.setPet(pet);
		appt.setVet(vet);
		appt.setStartTime(NOW.minus(Duration.ofMinutes(10)));
		appt.setEndTime(NOW.plus(Duration.ofMinutes(20)));
		appt.setStatus(AppointmentStatus.BOOKED);
		appt.setCareType(CareType.GENERAL);

		given(appointmentRepository.findByIdAndOwnerId(1, 1)).willReturn(Optional.of(appt));
		given(appointmentRepository.findById(1)).willReturn(Optional.of(appt));

		assertThatThrownBy(() -> appointmentService.cancelAppointmentByOwner(1, 1, "Late cancel", "owner1"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Cannot cancel an appointment at or after its scheduled start time");
	}

	@Test
	void testDirectBookByStaffSuccess() {
		given(timeService.now()).willReturn(NOW);
		given(timeService.quantizeToNext15MinuteBoundary(NOW)).willReturn(NOW);
		given(clinicSettingsService.getSettings()).willReturn(settings);
		Instant staffHorizonEnd = NOW.plus(Duration.ofDays(90));
		given(timeService.calculateStaffHorizonEnd(NOW, 90)).willReturn(staffHorizonEnd);

		Instant slotStart = Instant.parse("2026-08-28T14:00:00Z");
		given(ownerRepository.findById(1)).willReturn(Optional.of(owner));
		given(petRepository.findById(1)).willReturn(Optional.of(pet));
		given(vetRepository.findById(1)).willReturn(Optional.of(vet));
		given(schedulingRequestRepository.findByPetIdAndStatusNotIn(eq(1), any())).willReturn(Collections.emptyList());
		given(liveFeasibilityService.checkFeasibility(1, 1, 1, slotStart, slotStart.plus(Duration.ofMinutes(30)),
				CareType.GENERAL, null, staffHorizonEnd, null))
			.willReturn(true);
		given(appointmentRepository.save(any(Appointment.class))).willAnswer(inv -> inv.getArgument(0));

		AppointmentDto booked = appointmentService.directBookByStaff(1, 1, 1, slotStart, 30, CareType.GENERAL, null,
				"Phone booking agreed with client", "staff1");

		assertThat(booked.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
		assertThat(booked.getBookingSource()).isEqualTo(BookingSource.STAFF_DIRECT);
		assertThat(booked.getOfflineReason()).isEqualTo("Phone booking agreed with client");
	}

	@Test
	void testDirectBookByStaffRejectsUnresolvedRequestForPet() {
		given(timeService.now()).willReturn(NOW);
		given(timeService.quantizeToNext15MinuteBoundary(NOW)).willReturn(NOW);
		given(clinicSettingsService.getSettings()).willReturn(settings);
		given(timeService.calculateStaffHorizonEnd(NOW, 90)).willReturn(NOW.plus(Duration.ofDays(90)));

		Instant slotStart = Instant.parse("2026-08-28T14:00:00Z");
		given(ownerRepository.findById(1)).willReturn(Optional.of(owner));
		given(petRepository.findById(1)).willReturn(Optional.of(pet));
		given(vetRepository.findById(1)).willReturn(Optional.of(vet));
		given(schedulingRequestRepository.findByPetIdAndStatusNotIn(eq(1), any()))
			.willReturn(List.of(new SchedulingRequest()));

		assertThatThrownBy(() -> appointmentService.directBookByStaff(1, 1, 1, slotStart, 30, CareType.GENERAL, null,
				"Phone booking", "staff1"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Pet has an unresolved scheduling request");
	}

	@Test
	void testRescheduleAppointmentSuccess() {
		given(timeService.now()).willReturn(NOW);
		given(timeService.quantizeToNext15MinuteBoundary(NOW)).willReturn(NOW);
		given(clinicSettingsService.getSettings()).willReturn(settings);
		Instant staffHorizonEnd = NOW.plus(Duration.ofDays(90));
		given(timeService.calculateStaffHorizonEnd(NOW, 90)).willReturn(staffHorizonEnd);

		Appointment appt = new Appointment();
		appt.setId(1);
		appt.setOwner(owner);
		appt.setPet(pet);
		appt.setVet(vet);
		appt.setStartTime(NOW.plus(Duration.ofHours(2)));
		appt.setEndTime(NOW.plus(Duration.ofHours(3)));
		appt.setStatus(AppointmentStatus.BOOKED);
		appt.setCareType(CareType.GENERAL);

		given(appointmentRepository.findById(1)).willReturn(Optional.of(appt));
		given(vetRepository.findById(1)).willReturn(Optional.of(vet));

		Instant newStart = Instant.parse("2026-08-29T14:00:00Z");
		given(liveFeasibilityService.checkFeasibility(1, 1, 1, newStart, newStart.plus(Duration.ofMinutes(60)),
				CareType.GENERAL, null, staffHorizonEnd, null, 1))
			.willReturn(true);
		given(appointmentRepository.save(any(Appointment.class))).willAnswer(inv -> inv.getArgument(0));

		AppointmentDto rescheduled = appointmentService.rescheduleAppointment(1, 1, newStart, 60,
				"Owner requested next day", "staff1");

		assertThat(rescheduled.getStartTime()).isEqualTo(newStart);
		assertThat(rescheduled.getDurationMinutes()).isEqualTo(60);
		verify(calendarConflictService).resolveConflictsForAppointment(1, ConflictStatus.RESCHEDULED, "staff1");
	}

	@Test
	void testCompleteAppointmentCreatesExactlyOneVisit() {
		Instant end = NOW.minus(Duration.ofMinutes(30));
		given(timeService.now()).willReturn(NOW);
		given(timeService.getClinicZoneId()).willReturn(ZoneId.of("UTC"));

		Appointment appt = new Appointment();
		appt.setId(1);
		appt.setOwner(owner);
		appt.setPet(pet);
		appt.setVet(vet);
		appt.setStartTime(end.minus(Duration.ofMinutes(30)));
		appt.setEndTime(end);
		appt.setStatus(AppointmentStatus.BOOKED);
		appt.setCareType(CareType.GENERAL);

		given(appointmentRepository.findById(1)).willReturn(Optional.of(appt));
		given(visitRepository.save(any(Visit.class))).willAnswer(inv -> {
			Visit v = inv.getArgument(0);
			v.setId(10);
			return v;
		});
		given(appointmentRepository.save(any(Appointment.class))).willAnswer(inv -> inv.getArgument(0));

		AppointmentDto completed = appointmentService.completeAppointment(1, "Regular dental checkup completed",
				"staff1");
		assertThat(completed.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
		assertThat(completed.getVisitId()).isEqualTo(10);
		assertThat(completed.getCompletionNotes()).isEqualTo("Regular dental checkup completed");
		assertThat(pet.getVisits()).hasSize(1);
		Visit v = pet.getVisits().iterator().next();
		assertThat(v.getDescription()).isEqualTo("Regular dental checkup completed");
	}

	@Test
	void testCompletingAppointmentTwiceReusesTheExistingVisit() {
		Instant end = NOW.minus(Duration.ofMinutes(30));
		given(timeService.now()).willReturn(NOW);
		given(timeService.getClinicZoneId()).willReturn(ZoneId.of("UTC"));

		Appointment appt = new Appointment();
		appt.setId(1);
		appt.setOwner(owner);
		appt.setPet(pet);
		appt.setVet(vet);
		appt.setStartTime(end.minus(Duration.ofMinutes(30)));
		appt.setEndTime(end);
		appt.setStatus(AppointmentStatus.BOOKED);
		appt.setCareType(CareType.GENERAL);

		given(appointmentRepository.findById(1)).willReturn(Optional.of(appt));
		given(visitRepository.save(any(Visit.class))).willAnswer(inv -> {
			Visit visit = inv.getArgument(0);
			visit.setId(10);
			return visit;
		});
		given(appointmentRepository.save(any(Appointment.class))).willAnswer(inv -> inv.getArgument(0));

		AppointmentDto firstCompletion = appointmentService.completeAppointment(1, "Initial completion", "staff1");
		AppointmentDto secondCompletion = appointmentService.completeAppointment(1, "Repeated completion", "staff1");

		assertThat(firstCompletion.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
		assertThat(secondCompletion.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
		assertThat(secondCompletion.getVisitId()).isEqualTo(firstCompletion.getVisitId()).isEqualTo(10);
		assertThat(appt.getVisit()).isSameAs(pet.getVisits().iterator().next());
		verify(visitRepository, times(1)).save(any(Visit.class));
	}

	@Test
	void testCompletingNoShowAppointmentCreatesVisit() {
		Instant end = NOW.minus(Duration.ofMinutes(30));
		given(timeService.now()).willReturn(NOW);
		given(timeService.getClinicZoneId()).willReturn(ZoneId.of("UTC"));

		Appointment appt = new Appointment();
		appt.setId(2);
		appt.setOwner(owner);
		appt.setPet(pet);
		appt.setVet(vet);
		appt.setStartTime(end.minus(Duration.ofMinutes(30)));
		appt.setEndTime(end);
		appt.setStatus(AppointmentStatus.NO_SHOW);
		appt.setCareType(CareType.GENERAL);

		given(appointmentRepository.findById(2)).willReturn(Optional.of(appt));
		given(visitRepository.save(any(Visit.class))).willAnswer(inv -> {
			Visit visit = inv.getArgument(0);
			visit.setId(11);
			return visit;
		});
		given(appointmentRepository.save(any(Appointment.class))).willAnswer(inv -> inv.getArgument(0));

		AppointmentDto completed = appointmentService.completeAppointment(2, "Completed after no-show review",
				"staff1");

		assertThat(completed.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
		assertThat(completed.getVisitId()).isEqualTo(11);
		assertThat(completed.getCompletionNotes()).isEqualTo("Completed after no-show review");
		assertThat(pet.getVisits()).hasSize(1);
		assertThat(pet.getVisits().iterator().next().getDescription()).isEqualTo("Completed after no-show review");
		verify(visitRepository, times(1)).save(any(Visit.class));
	}

	@Test
	void testCompleteAppointmentFailsBeforeEndTime() {
		Instant futureEnd = NOW.plus(Duration.ofMinutes(30));
		given(timeService.now()).willReturn(NOW);

		Appointment appt = new Appointment();
		appt.setId(1);
		appt.setStartTime(NOW);
		appt.setEndTime(futureEnd);
		appt.setStatus(AppointmentStatus.BOOKED);

		given(appointmentRepository.findById(1)).willReturn(Optional.of(appt));

		assertThatThrownBy(() -> appointmentService.completeAppointment(1, "Notes", "staff1"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Cannot complete an appointment before its scheduled end time");
	}

	@Test
	void testMarkNoShowSuccess() {
		Instant pastEnd = NOW.minus(Duration.ofMinutes(15));
		given(timeService.now()).willReturn(NOW);

		Appointment appt = new Appointment();
		appt.setId(1);
		appt.setOwner(owner);
		appt.setPet(pet);
		appt.setVet(vet);
		appt.setStartTime(pastEnd.minus(Duration.ofMinutes(30)));
		appt.setEndTime(pastEnd);
		appt.setStatus(AppointmentStatus.BOOKED);

		given(appointmentRepository.findById(1)).willReturn(Optional.of(appt));
		given(appointmentRepository.save(any(Appointment.class))).willAnswer(inv -> inv.getArgument(0));

		AppointmentDto noShow = appointmentService.markNoShow(1, "staff1");
		assertThat(noShow.getStatus()).isEqualTo(AppointmentStatus.NO_SHOW);
		assertThat(pet.getVisits()).isEmpty();
	}

}
