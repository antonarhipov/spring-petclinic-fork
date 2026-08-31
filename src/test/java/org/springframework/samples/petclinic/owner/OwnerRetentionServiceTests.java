package org.springframework.samples.petclinic.owner;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.samples.petclinic.appointment.Appointment;
import org.springframework.samples.petclinic.appointment.AppointmentRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OwnerRetentionServiceTests {

	@Autowired
	private OwnerRetentionService retentionService;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Test
	void blocksPetDeletionWhenCareHistoryExists() {
		Owner owner = this.ownerRepository.findAll()
			.stream()
			.filter(candidate -> !candidate.getPets().isEmpty())
			.findFirst()
			.orElseThrow();
		Pet pet = owner.getPets().iterator().next();
		this.appointmentRepository.saveAndFlush(new Appointment(owner.getId(), pet.getId(), 1,
				Instant.parse("2026-09-10T09:00:00Z"), Instant.parse("2026-09-10T09:30:00Z"), "UTC"));

		assertThatThrownBy(() -> this.retentionService.deletePet(owner.getId(), pet.getId()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("history must be retained");
	}

	@Test
	void blocksOwnerDeletionWhenCareHistoryExists() {
		Owner owner = this.ownerRepository.findAll()
			.stream()
			.filter(candidate -> !candidate.getPets().isEmpty())
			.findFirst()
			.orElseThrow();
		Pet pet = owner.getPets().iterator().next();
		this.appointmentRepository.saveAndFlush(new Appointment(owner.getId(), pet.getId(), 1,
				Instant.parse("2026-09-11T09:00:00Z"), Instant.parse("2026-09-11T09:30:00Z"), "UTC"));

		assertThatThrownBy(() -> this.retentionService.deleteOwner(owner.getId()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("history must be retained");
	}

}
