/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.model.Appointment;
import org.springframework.samples.petclinic.scheduling.model.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.model.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.model.Hold;
import org.springframework.samples.petclinic.scheduling.model.HoldRepository;
import org.springframework.samples.petclinic.scheduling.model.HoldStatus;
import org.springframework.samples.petclinic.scheduling.model.RequestState;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequestRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class SchedulingViewFetchTests {

	@Autowired
	private AppointmentRepository appointments;

	@Autowired
	private HoldRepository holds;

	@Autowired
	private SchedulingRequestRepository schedulingRequests;

	@Autowired
	private OwnerRepository owners;

	@Autowired
	private VetRepository vets;

	@Autowired
	private EntityManager entityManager;

	@Test
	void appointmentViewQueriesInitializeRenderedAssociations() {
		Owner owner = this.owners.findById(1).orElseThrow();
		Pet pet = owner.getPets().get(0);
		Vet vet = this.vets.findById(1).orElseThrow();
		SchedulingRequest request = saveRequest(owner, pet, RequestState.CONFIRMED);

		Appointment appointment = new Appointment();
		appointment.setOwner(owner);
		appointment.setPet(pet);
		appointment.setVet(vet);
		appointment.setSchedulingRequest(request);
		appointment.setStartTime(LocalDateTime.now().plusDays(10));
		appointment.setEndTime(appointment.getStartTime().plusMinutes(30));
		appointment.setStatus(AppointmentStatus.BOOKED);
		Appointment saved = this.appointments.saveAndFlush(appointment);
		Integer appointmentId = saved.getId();
		Integer requestId = request.getId();

		this.entityManager.clear();
		assertAppointmentDetailsLoaded(this.appointments.findByIdWithDetails(appointmentId).orElseThrow());

		this.entityManager.clear();
		assertAppointmentDetailsLoaded(this.appointments.findBySchedulingRequestId(requestId).orElseThrow());

		this.entityManager.clear();
		assertAppointmentDetailsLoaded(this.appointments.findByStatusOrderByStartTimeAsc(AppointmentStatus.BOOKED)
			.stream()
			.filter(candidate -> candidate.getId().equals(appointmentId))
			.findFirst()
			.orElseThrow());

		this.entityManager.clear();
		assertAppointmentDetailsLoaded(this.appointments.findAllByOrderByStartTimeAsc()
			.stream()
			.filter(candidate -> candidate.getId().equals(appointmentId))
			.findFirst()
			.orElseThrow());
	}

	@Test
	void activeHoldQueryInitializesVetAndSpecialties() {
		Owner owner = this.owners.findById(2).orElseThrow();
		Pet pet = owner.getPets().get(0);
		Vet vet = this.vets.findById(2).orElseThrow();
		SchedulingRequest request = saveRequest(owner, pet, RequestState.SLOT_HELD);

		Hold hold = new Hold();
		hold.setSchedulingRequest(request);
		hold.setVet(vet);
		hold.setStartTime(LocalDateTime.now().plusDays(11));
		hold.setEndTime(hold.getStartTime().plusMinutes(30));
		hold.setExpiresAt(Instant.now().plusSeconds(3600));
		hold.setStatus(HoldStatus.ACTIVE);
		this.holds.saveAndFlush(hold);

		this.entityManager.clear();
		Hold loaded = this.holds.findBySchedulingRequestIdAndStatus(request.getId(), HoldStatus.ACTIVE).orElseThrow();
		PersistenceUnitUtil persistenceUnitUtil = this.entityManager.getEntityManagerFactory().getPersistenceUnitUtil();

		assertThat(persistenceUnitUtil.isLoaded(loaded, "vet")).isTrue();
		assertThat(persistenceUnitUtil.isLoaded(loaded.getVet(), "specialties")).isTrue();

		this.entityManager.clear();
		assertThat(loaded.getVet().getFirstName()).isNotBlank();
		assertThat(loaded.getVet().getSpecialties()).isNotNull();
	}

	private SchedulingRequest saveRequest(Owner owner, Pet pet, RequestState state) {
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(owner);
		request.setPet(pet);
		request.setRawText("Routine visit");
		request.setState(state);
		return this.schedulingRequests.saveAndFlush(request);
	}

	private void assertAppointmentDetailsLoaded(Appointment appointment) {
		PersistenceUnitUtil persistenceUnitUtil = this.entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
		assertThat(persistenceUnitUtil.isLoaded(appointment, "owner")).isTrue();
		assertThat(persistenceUnitUtil.isLoaded(appointment, "pet")).isTrue();
		assertThat(persistenceUnitUtil.isLoaded(appointment.getPet(), "type")).isTrue();
		assertThat(persistenceUnitUtil.isLoaded(appointment, "vet")).isTrue();

		this.entityManager.clear();
		assertThat(appointment.getOwner().getFirstName()).isNotBlank();
		assertThat(appointment.getPet().getName()).isNotBlank();
		assertThat(appointment.getPet().getType().getName()).isNotBlank();
		assertThat(appointment.getVet().getFirstName()).isNotBlank();
	}

}
