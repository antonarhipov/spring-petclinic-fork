package org.springframework.samples.petclinic.scheduling.web.owner;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.appointment.OfferRepository;
import org.springframework.samples.petclinic.scheduling.request.OwnerResourceNotFoundException;
import org.springframework.samples.petclinic.scheduling.request.OwnerSchedulingQueryService;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OwnerHistoryProjectionTests {

	@Test
	void historyOmitsStaffReasonAndScores() {
		OwnerRepository owners = mock(OwnerRepository.class);
		AppointmentRepository appointments = mock(AppointmentRepository.class);
		Owner owner = new Owner();
		owner.setId(1);
		Pet pet = new Pet();
		owner.addPet(pet);
		pet.setId(1);
		when(owners.findById(1)).thenReturn(Optional.of(owner));
		Appointment appointment = new Appointment();
		ReflectionTestUtils.setField(appointment, "id", 8L);
		appointment.setPetId(1);
		appointment.setVeterinarianId(1);
		appointment.setStartAt(Instant.parse("2026-03-16T13:00:00Z"));
		appointment.setEndAt(Instant.parse("2026-03-16T13:30:00Z"));
		appointment.setStatus(AppointmentStatus.CONFIRMED);
		appointment.setStaffReasonNote("internal");
		when(appointments.findByPetIdInOrderByStartAtDesc(List.of(1))).thenReturn(List.of(appointment));
		when(mock(SchedulingRequestRepository.class).findByOwnerIdOrderByUpdatedAtDesc(1)).thenReturn(List.of());
		OwnerSchedulingQueryService service = new OwnerSchedulingQueryService(owners, appointments,
				mock(SchedulingRequestRepository.class), mock(OfferRepository.class), mock(VetRepository.class));
		OwnerSchedulingQueryService.OwnerHistoryView history = service.history(1);
		assertThat(history.appointments()).hasSize(1);
		assertThat(history.appointments().get(0).status()).isEqualTo("CONFIRMED");
	}

	@Test
	void crossOwnerAppointmentIsNotFound() {
		OwnerRepository owners = mock(OwnerRepository.class);
		Owner owner = new Owner();
		owner.setId(1);
		when(owners.findById(1)).thenReturn(Optional.of(owner));
		AppointmentRepository appointments = mock(AppointmentRepository.class);
		when(appointments.findByPetIdInOrderByStartAtDesc(List.of())).thenReturn(List.of());
		OwnerSchedulingQueryService service = new OwnerSchedulingQueryService(owners, appointments,
				mock(SchedulingRequestRepository.class), mock(OfferRepository.class), mock(VetRepository.class));
		assertThatThrownBy(() -> service.appointment(1, 99L)).isInstanceOf(OwnerResourceNotFoundException.class);
	}

}
