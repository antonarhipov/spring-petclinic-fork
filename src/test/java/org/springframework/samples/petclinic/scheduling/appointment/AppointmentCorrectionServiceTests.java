package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.VisitRepository;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityRepository;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppointmentCorrectionServiceTests {

	@Test
	void confirmedAppointmentCannotBeCorrected() {
		AppointmentRepository appointments = mock(AppointmentRepository.class);
		Appointment appointment = new Appointment();
		appointment.setStatus(AppointmentStatus.CONFIRMED);
		when(appointments.findById(1L)).thenReturn(Optional.of(appointment));
		AppointmentCorrectionService service = new AppointmentCorrectionService(appointments,
				mock(VisitRepository.class), mock(OwnerRepository.class), mock(AvailabilityRepository.class),
				mock(AppointmentAuditService.class), Clock.systemUTC());
		assertThatThrownBy(() -> service.correct(1L, AppointmentStatus.NO_SHOW, 2L, "fix"))
			.isInstanceOf(LifecycleException.class);
	}

	@Test
	void noShowCanBeCorrectedToCompletedStatus() {
		AppointmentRepository appointments = mock(AppointmentRepository.class);
		VisitRepository visits = mock(VisitRepository.class);
		Appointment appointment = new Appointment();
		ReflectionTestUtils.setField(appointment, "id", 1L);
		appointment.setStatus(AppointmentStatus.NO_SHOW);
		appointment.setPetId(1);
		appointment.setVeterinarianId(1);
		appointment.setEndAt(Instant.parse("2026-03-16T13:30:00Z"));
		when(appointments.findById(1L)).thenReturn(Optional.of(appointment));
		when(visits.findByAppointmentId(1L)).thenReturn(Optional.empty());
		when(mock(OwnerRepository.class).findByPetId(1)).thenReturn(Optional.empty());
		OwnerRepository owners = mock(OwnerRepository.class);
		org.springframework.samples.petclinic.owner.Owner owner = new org.springframework.samples.petclinic.owner.Owner();
		org.springframework.samples.petclinic.owner.Pet pet = new org.springframework.samples.petclinic.owner.Pet();
		owner.addPet(pet);
		pet.setId(1);
		when(owners.findByPetId(1)).thenReturn(Optional.of(owner));
		AvailabilityRepository policies = mock(AvailabilityRepository.class);
		org.springframework.samples.petclinic.scheduling.availability.ClinicSchedulingPolicy policy = new org.springframework.samples.petclinic.scheduling.availability.ClinicSchedulingPolicy();
		policy.setZoneId("UTC");
		when(policies.currentPolicy()).thenReturn(policy);
		AppointmentCorrectionService service = new AppointmentCorrectionService(appointments, visits, owners, policies,
				mock(AppointmentAuditService.class),
				Clock.fixed(Instant.parse("2026-03-16T16:00:00Z"), ZoneOffset.UTC));
		service.correct(1L, AppointmentStatus.COMPLETED, 2L, "actually attended");
		assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
		assertThat(appointment.getStaffReasonCategory()).isEqualTo(AppointmentReasonCategory.ERROR_CORRECTION.name());
	}

}
