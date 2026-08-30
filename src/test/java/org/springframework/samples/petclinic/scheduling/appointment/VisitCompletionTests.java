package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.Visit;
import org.springframework.samples.petclinic.owner.VisitRepository;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityCommandService;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityRepository;
import org.springframework.samples.petclinic.scheduling.availability.ClinicSchedulingPolicy;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VisitCompletionTests {

	@Test
	void completeCreatesExactlyOneLinkedVisit() {
		AppointmentRepository appointments = mock(AppointmentRepository.class);
		VisitRepository visits = mock(VisitRepository.class);
		OwnerRepository owners = mock(OwnerRepository.class);
		Appointment appointment = appointment();
		when(appointments.findById(7L)).thenReturn(Optional.of(appointment));
		when(visits.findByAppointmentId(7L)).thenReturn(Optional.empty());
		Owner owner = new Owner();
		Pet pet = new Pet();
		owner.addPet(pet);
		pet.setId(1);
		when(owners.findByPetId(1)).thenReturn(Optional.of(owner));
		AppointmentLifecycleService service = service(appointments, visits, owners);
		service.complete(7L, 4L, "Exam complete");
		assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
		assertThat(pet.getVisits()).hasSize(1);
		Visit visit = pet.getVisits().iterator().next();
		assertThat(visit.getAppointmentId()).isEqualTo(7L);
		assertThat(visit.getVetId()).isEqualTo(1);
	}

	@Test
	void noShowCreatesNoVisit() {
		AppointmentRepository appointments = mock(AppointmentRepository.class);
		VisitRepository visits = mock(VisitRepository.class);
		when(appointments.findById(7L)).thenReturn(Optional.of(appointment()));
		AppointmentLifecycleService service = service(appointments, visits, mock(OwnerRepository.class));
		service.markNoShow(7L, 4L, "did not arrive");
		verify(visits, never()).save(org.mockito.ArgumentMatchers.any());
	}

	private AppointmentLifecycleService service(AppointmentRepository appointments, VisitRepository visits,
			OwnerRepository owners) {
		AvailabilityRepository policies = mock(AvailabilityRepository.class);
		ClinicSchedulingPolicy policy = new ClinicSchedulingPolicy();
		policy.setZoneId("UTC");
		when(policies.currentPolicy()).thenReturn(policy);
		return new AppointmentLifecycleService(appointments, mock(ReservationBlockRepository.class),
				mock(OccupancyQueryService.class), mock(AvailabilityCommandService.class), policies, visits, owners,
				mock(AppointmentAuditService.class), mock(StaffBookingService.class),
				Clock.fixed(Instant.parse("2026-03-16T14:00:00Z"), ZoneOffset.UTC));
	}

	private Appointment appointment() {
		Appointment appointment = new Appointment();
		ReflectionTestUtils.setField(appointment, "id", 7L);
		appointment.setPetId(1);
		appointment.setVeterinarianId(1);
		appointment.setStartAt(Instant.parse("2026-03-16T13:00:00Z"));
		appointment.setEndAt(Instant.parse("2026-03-16T13:30:00Z"));
		appointment.setStatus(AppointmentStatus.CONFIRMED);
		return appointment;
	}

}
