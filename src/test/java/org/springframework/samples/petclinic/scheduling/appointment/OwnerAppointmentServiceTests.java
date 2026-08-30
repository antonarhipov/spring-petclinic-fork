package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OwnerAppointmentServiceTests {

	@Test
	void cancelBeforeStartLeavesRequestClosed() {
		AppointmentRepository appointments = mock(AppointmentRepository.class);
		OwnerRepository owners = mock(OwnerRepository.class);
		SchedulingRequestRepository requests = mock(SchedulingRequestRepository.class);
		AppointmentLifecycleService lifecycle = mock(AppointmentLifecycleService.class);
		Appointment appointment = new Appointment();
		ReflectionTestUtils.setField(appointment, "id", 3L);
		appointment.setPetId(1);
		appointment.setRequestId(9L);
		appointment.setStartAt(Instant.parse("2026-03-16T15:00:00Z"));
		appointment.setStatus(AppointmentStatus.CONFIRMED);
		when(appointments.findById(3L)).thenReturn(Optional.of(appointment));
		Owner owner = new Owner();
		Pet pet = new Pet();
		owner.addPet(pet);
		pet.setId(1);
		when(owners.findById(1)).thenReturn(Optional.of(owner));
		when(lifecycle.cancel(3L, 8L, "OWNER", AppointmentReasonCategory.OWNER_REQUESTED, "cannot attend"))
			.thenReturn(appointment);
		SchedulingRequest request = new SchedulingRequest();
		request.setState(RequestState.CONFIRMED);
		when(requests.findById(9L)).thenReturn(Optional.of(request));
		OwnerAppointmentService service = new OwnerAppointmentService(appointments, owners, requests, lifecycle,
				Clock.fixed(Instant.parse("2026-03-16T12:00:00Z"), ZoneOffset.UTC));
		service.cancelBeforeStart(3L, 1, 8L, "cannot attend");
		assertThat(request.getState()).isEqualTo(RequestState.CLOSED);
	}

	@Test
	void cancelAfterStartIsRejected() {
		AppointmentRepository appointments = mock(AppointmentRepository.class);
		OwnerRepository owners = mock(OwnerRepository.class);
		Appointment appointment = new Appointment();
		appointment.setPetId(1);
		appointment.setStartAt(Instant.parse("2026-03-16T11:00:00Z"));
		when(appointments.findById(3L)).thenReturn(Optional.of(appointment));
		Owner owner = new Owner();
		Pet pet = new Pet();
		owner.addPet(pet);
		pet.setId(1);
		when(owners.findById(1)).thenReturn(Optional.of(owner));
		OwnerAppointmentService service = new OwnerAppointmentService(appointments, owners,
				mock(SchedulingRequestRepository.class), mock(AppointmentLifecycleService.class),
				Clock.fixed(Instant.parse("2026-03-16T12:00:00Z"), ZoneOffset.UTC));
		assertThatThrownBy(() -> service.cancelBeforeStart(3L, 1, 8L, "late")).isInstanceOf(LifecycleException.class);
	}

}
