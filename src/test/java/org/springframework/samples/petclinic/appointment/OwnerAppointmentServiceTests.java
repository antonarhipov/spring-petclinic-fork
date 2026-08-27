package org.springframework.samples.petclinic.appointment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OwnerAppointmentServiceTests {

	private static final Instant NOW = Instant.parse("2026-08-28T10:00:00Z");

	@Mock
	private AppointmentRepository appointmentRepository;

	@Mock
	private AppointmentRequestRepository requestRepository;

	@Mock
	private OwnerRepository ownerRepository;

	private OwnerAppointmentService service;

	private Owner owner;

	private Pet pet;

	@BeforeEach
	void setUp() {
		this.service = new OwnerAppointmentService(this.appointmentRepository, this.requestRepository,
				this.ownerRepository, Clock.fixed(NOW, ZoneOffset.UTC));
		this.owner = new Owner();
		this.owner.setId(1);
		this.pet = new Pet();
		this.owner.addPet(this.pet);
		this.pet.setId(2);
		given(this.ownerRepository.findById(1)).willReturn(Optional.of(this.owner));
	}

	@Test
	void upcomingContainsOnlyOwnersFutureScheduledAppointments() {
		Appointment upcoming = appointment(10, this.pet, NOW.plusSeconds(7200), AppointmentStatus.SCHEDULED);
		Appointment past = appointment(11, this.pet, NOW.minusSeconds(1), AppointmentStatus.SCHEDULED);
		Appointment cancelled = appointment(12, this.pet, NOW.plusSeconds(7200), AppointmentStatus.CANCELLED);
		given(this.appointmentRepository.findByPetIds(List.of(2))).willReturn(List.of(upcoming, past, cancelled));

		assertThat(this.service.getUpcoming(1)).containsExactly(upcoming);
	}

	@Test
	void ownerCanCancelOnlyMoreThanTwentyFourHoursAhead() {
		Appointment outsideCutoff = appointment(10, this.pet, NOW.plusSeconds(24 * 3600 + 1),
				AppointmentStatus.SCHEDULED);
		given(this.appointmentRepository.findByIdForUpdate(10)).willReturn(Optional.of(outsideCutoff));

		this.service.cancel(1, 10);

		assertThat(outsideCutoff.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);

		Appointment atCutoff = appointment(11, this.pet, NOW.plusSeconds(24 * 3600), AppointmentStatus.SCHEDULED);
		given(this.appointmentRepository.findByIdForUpdate(11)).willReturn(Optional.of(atCutoff));
		assertThatThrownBy(() -> this.service.cancel(1, 11)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("contact clinic staff");
	}

	@Test
	void ownerCannotCancelAnotherOwnersAppointment() {
		Pet otherPet = new Pet();
		otherPet.setId(99);
		Appointment other = appointment(12, otherPet, NOW.plusSeconds(172800), AppointmentStatus.SCHEDULED);
		given(this.appointmentRepository.findByIdForUpdate(12)).willReturn(Optional.of(other));

		assertThatThrownBy(() -> this.service.cancel(1, 12)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("does not belong");
	}

	private static Appointment appointment(int id, Pet pet, Instant start, AppointmentStatus status) {
		Appointment appointment = new Appointment();
		appointment.setId(id);
		appointment.setPet(pet);
		appointment.setStartInstant(start);
		appointment.setStatus(status);
		return appointment;
	}

}
