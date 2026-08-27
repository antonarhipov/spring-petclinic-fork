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
package org.springframework.samples.petclinic.appointment;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.Visit;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Repository;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(includeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = Repository.class))
class AppointmentRepositoryTests {

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private AppointmentRequestRepository appointmentRequestRepository;

	@Autowired
	private SlotHoldRepository slotHoldRepository;

	@Autowired
	private RejectedSuggestionRepository rejectedSuggestionRepository;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private VetRepository vetRepository;

	@Test
	void shouldPersistAndQueryDirectStaffAppointment() {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().iterator().next();
		Vet vet = this.vetRepository.findById(1).orElseThrow();

		Instant start = Instant.parse("2026-09-01T09:00:00Z");

		Appointment appointment = new Appointment();
		appointment.setRequest(null);
		appointment.setPet(pet);
		appointment.setVet(vet);
		appointment.setStartInstant(start);
		appointment.setDurationMin(30);
		appointment.setStatus(AppointmentStatus.SCHEDULED);
		appointment.setReason("Direct booking checkup");

		Appointment saved = this.appointmentRepository.save(appointment);
		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getRequest()).isNull();
		assertThat(saved.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
		assertThat(saved.getEndInstant()).isEqualTo(Instant.parse("2026-09-01T09:30:00Z"));

		List<Appointment> byVet = this.appointmentRepository.findByVetId(vet.getId());
		assertThat(byVet).extracting(Appointment::getId).contains(saved.getId());

		List<Appointment> notCancelled = this.appointmentRepository.findByVetIdAndStatusNot(vet.getId(),
				AppointmentStatus.CANCELLED);
		assertThat(notCancelled).extracting(Appointment::getId).contains(saved.getId());
	}

	@Test
	void shouldPersistAppointmentRequestAndChildRecords() {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().iterator().next();
		Vet vet = this.vetRepository.findById(1).orElseThrow();

		AppointmentRequest request = new AppointmentRequest();
		request.setOwner(owner);
		request.setPet(pet);
		request.setFreeText("Need annual shot for my pet");
		request.setStatus(AppointmentRequestStatus.DRAFT);
		request.setConsentFlag(false);

		AppointmentRequest savedReq = this.appointmentRequestRepository.save(request);
		assertThat(savedReq.getId()).isNotNull();
		assertThat(savedReq.getStatus()).isEqualTo(AppointmentRequestStatus.DRAFT);

		Instant holdStart = Instant.parse("2026-09-01T10:00:00Z");
		SlotHold hold = new SlotHold();
		hold.setVet(vet);
		hold.setRequest(savedReq);
		hold.setStartInstant(holdStart);
		hold.setExpiresAt(Instant.parse("2026-09-01T10:10:00Z"));
		SlotHold savedHold = this.slotHoldRepository.save(hold);
		assertThat(savedHold.getId()).isNotNull();

		RejectedSuggestion rej = new RejectedSuggestion();
		rej.setRequest(savedReq);
		rej.setVet(vet);
		rej.setStartInstant(holdStart);
		RejectedSuggestion savedRej = this.rejectedSuggestionRepository.save(rej);
		assertThat(savedRej.getId()).isNotNull();

		List<RejectedSuggestion> rejected = this.rejectedSuggestionRepository.findByRequestId(savedReq.getId());
		assertThat(rejected).hasSize(1);
	}

	@Test
	void appointmentOverlapMethodShouldCorrectlyDetectCollisions() {
		Appointment app = new Appointment();
		app.setStartInstant(Instant.parse("2026-09-01T09:00:00Z"));
		app.setDurationMin(30); // 09:00 - 09:30

		// Exact match -> overlap
		assertThat(app.overlaps(Instant.parse("2026-09-01T09:00:00Z"), 30)).isTrue();
		// Partial overlap inside -> overlap
		assertThat(app.overlaps(Instant.parse("2026-09-01T09:15:00Z"), 30)).isTrue();
		// Partial overlap before -> overlap
		assertThat(app.overlaps(Instant.parse("2026-09-01T08:45:00Z"), 30)).isTrue();
		// Contiguous immediately before -> NO overlap
		assertThat(app.overlaps(Instant.parse("2026-09-01T08:30:00Z"), 30)).isFalse();
		// Contiguous immediately after -> NO overlap
		assertThat(app.overlaps(Instant.parse("2026-09-01T09:30:00Z"), 30)).isFalse();
		// Far after -> NO overlap
		assertThat(app.overlaps(Instant.parse("2026-09-01T10:00:00Z"), 30)).isFalse();
	}

	@Test
	void existingVisitsRemainDistinctAndFunctional() {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().iterator().next();

		int initialVisits = pet.getVisits().size();

		Visit visit = new Visit();
		visit.setDate(LocalDate.now());
		visit.setDescription("Rabies check");
		pet.addVisit(visit);
		this.ownerRepository.save(owner);

		Owner reloaded = this.ownerRepository.findById(1).orElseThrow();
		Pet reloadedPet = reloaded.getPet(pet.getId());
		assertThat(reloadedPet.getVisits()).hasSize(initialVisits + 1);
	}

}
