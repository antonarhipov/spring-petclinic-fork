package org.springframework.samples.petclinic.staff;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.samples.petclinic.appointment.Appointment;
import org.springframework.samples.petclinic.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.calendar.ClinicSettings;
import org.springframework.samples.petclinic.calendar.ClinicSettingsRepository;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.vet.Vet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AppointmentLifecycleServiceTests {

	@Mock
	private AppointmentRepository appointmentRepository;

	@Mock
	private OwnerRepository ownerRepository;

	@Mock
	private ClinicSettingsRepository settingsRepository;

	private AppointmentLifecycleService service;

	private Appointment appointment;

	private Owner owner;

	@BeforeEach
	void setUp() {
		this.service = new AppointmentLifecycleService(this.appointmentRepository, this.ownerRepository,
				this.settingsRepository);
		this.owner = new Owner();
		this.owner.setId(1);
		Pet pet = new Pet();
		this.owner.addPet(pet);
		pet.setId(2);
		pet.setName("Leo");
		Vet vet = new Vet();
		vet.setFirstName("James");
		vet.setLastName("Carter");
		this.appointment = new Appointment();
		this.appointment.setId(3);
		this.appointment.setPet(pet);
		this.appointment.setVet(vet);
		this.appointment.setStartInstant(Instant.parse("2026-10-25T08:30:00Z"));
		this.appointment.setDurationMin(30);
		this.appointment.setStatus(AppointmentStatus.SCHEDULED);
		given(this.appointmentRepository.findByIdForUpdate(3)).willReturn(Optional.of(this.appointment));
	}

	@Test
	void cancellationRequiresAndStoresReason() {
		assertThatThrownBy(() -> this.service.cancel(3, " ")).isInstanceOf(IllegalArgumentException.class);

		this.service.cancel(3, " owner called ");

		assertThat(this.appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
		assertThat(this.appointment.getReason()).isEqualTo("owner called");
	}

	@Test
	void completionCreatesVisitAndNoShowDoesNot() {
		ClinicSettings settings = new ClinicSettings();
		settings.setZoneId("Europe/Amsterdam");
		this.appointment.setReason("Annual exam");
		given(this.ownerRepository.findByPetId(2)).willReturn(Optional.of(this.owner));
		given(this.settingsRepository.getClinicSettings()).willReturn(settings);

		this.service.complete(3);

		assertThat(this.appointment.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
		assertThat(this.appointment.getPet().getVisits()).singleElement().satisfies(visit -> {
			assertThat(visit.getDate()).isEqualTo(LocalDate.of(2026, 10, 25));
			assertThat(visit.getDescription()).isEqualTo("Annual exam");
		});
		verify(this.ownerRepository).save(this.owner);

		int visitCount = this.appointment.getPet().getVisits().size();
		this.appointment.setStatus(AppointmentStatus.SCHEDULED);
		this.service.markNoShow(3);
		assertThat(this.appointment.getStatus()).isEqualTo(AppointmentStatus.NO_SHOW);
		assertThat(this.appointment.getPet().getVisits()).hasSize(visitCount);
	}

	@Test
	void rejectsLifecycleChangesFromTerminalState() {
		this.appointment.setStatus(AppointmentStatus.COMPLETED);

		assertThatThrownBy(() -> this.service.markNoShow(3)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Only scheduled");
	}

}
