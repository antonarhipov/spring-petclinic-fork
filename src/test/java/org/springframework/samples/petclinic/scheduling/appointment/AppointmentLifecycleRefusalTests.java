/*
 * Copyright 2012-2026 the original author or authors.
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

package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.TestClockConfig;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Refusal and timing guard tests for AppointmentLifecycleService (RULE-16, AC-124,
 * AC-136).
 */
@SpringBootTest
@Import(TestClockConfig.class)
@Transactional
class AppointmentLifecycleRefusalTests {

	@Autowired
	private AppointmentLifecycleService lifecycleService;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private AppointmentChangeRepository changeRepository;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private VetRepository vetRepository;

	@Autowired
	private Clock clock;

	private Pet testPet;

	private Vet testVet;

	@BeforeEach
	void setUp() {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		this.testPet = owner.getPet(1);
		this.testVet = this.vetRepository.findAll().iterator().next();
	}

	private Appointment createPersistedAppointment(AppointmentStatus status, ZonedDateTime startTime) {
		Appointment app = new Appointment();
		app.setPet(this.testPet);
		app.setVet(this.testVet);
		app.setStartTime(startTime);
		app.setDuration(30);
		app.setStatus(status);
		app.setReason("Annual wellness exam");
		return this.appointmentRepository.saveAndFlush(app);
	}

	@Test
	void confirmedBeforeStartRefusesCompletionAndNoShow() {
		ZonedDateTime futureStart = ZonedDateTime.now(this.clock).plusHours(2);
		Appointment app = createPersistedAppointment(AppointmentStatus.CONFIRMED, futureStart);
		int initialChanges = this.changeRepository.findByAppointmentIdOrderByTimestampAsc(app.getId()).size();
		int initialVisits = this.testPet.getVisits().size();

		assertThatThrownBy(() -> this.lifecycleService.markCompleted(app, "staff_1"))
			.isInstanceOf(IllegalAppointmentTransitionException.class);
		assertThatThrownBy(() -> this.lifecycleService.markNoShow(app, "staff_1", "didn't show"))
			.isInstanceOf(IllegalAppointmentTransitionException.class);

		Appointment current = this.appointmentRepository.findById(app.getId()).orElseThrow();
		assertThat(current.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
		List<AppointmentChange> changes = this.changeRepository.findByAppointmentIdOrderByTimestampAsc(app.getId());
		assertThat(changes).hasSize(initialChanges);
		assertThat(this.testPet.getVisits()).hasSize(initialVisits);
	}

	@Test
	void confirmedAfterStartRefusesCancellationAndReschedule() {
		ZonedDateTime pastStart = ZonedDateTime.now(this.clock).minusHours(1);
		Appointment app = createPersistedAppointment(AppointmentStatus.CONFIRMED, pastStart);
		int initialChanges = this.changeRepository.findByAppointmentIdOrderByTimestampAsc(app.getId()).size();

		assertThatThrownBy(() -> this.lifecycleService.ownerCancel(app, "owner_1"))
			.isInstanceOf(IllegalAppointmentTransitionException.class);
		assertThatThrownBy(() -> this.lifecycleService.staffCancel(app, "staff_1", "vet sick"))
			.isInstanceOf(IllegalAppointmentTransitionException.class);
		assertThatThrownBy(() -> this.lifecycleService.staffReschedule(app, "staff_1", "reschedule",
				pastStart.plusDays(1), 30, this.testVet))
			.isInstanceOf(IllegalAppointmentTransitionException.class);

		Appointment current = this.appointmentRepository.findById(app.getId()).orElseThrow();
		assertThat(current.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
		List<AppointmentChange> changes = this.changeRepository.findByAppointmentIdOrderByTimestampAsc(app.getId());
		assertThat(changes).hasSize(initialChanges);
	}

	@Test
	@org.junit.jupiter.api.DisplayName("AC-124: terminal appointment states refuse all actions without mutation")
	void terminalStatesRefuseAllActions() {
		List<AppointmentStatus> terminalStates = List.of(AppointmentStatus.CANCELLED_BY_OWNER,
				AppointmentStatus.CANCELLED_BY_STAFF, AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW);

		for (AppointmentStatus status : terminalStates) {
			ZonedDateTime start = ZonedDateTime.now(this.clock).plusDays(1);
			Appointment app = createPersistedAppointment(status, start);
			int initialChanges = this.changeRepository.findByAppointmentIdOrderByTimestampAsc(app.getId()).size();

			assertThatThrownBy(() -> this.lifecycleService.ownerCancel(app, "owner_1"))
				.isInstanceOf(IllegalAppointmentTransitionException.class);
			assertThatThrownBy(() -> this.lifecycleService.staffCancel(app, "staff_1", "cancel"))
				.isInstanceOf(IllegalAppointmentTransitionException.class);
			assertThatThrownBy(() -> this.lifecycleService.staffReschedule(app, "staff_1", "reschedule",
					start.plusDays(1), 30, this.testVet))
				.isInstanceOf(IllegalAppointmentTransitionException.class);
			assertThatThrownBy(() -> this.lifecycleService.markCompleted(app, "staff_1"))
				.isInstanceOf(IllegalAppointmentTransitionException.class);
			assertThatThrownBy(() -> this.lifecycleService.markNoShow(app, "staff_1", "no show"))
				.isInstanceOf(IllegalAppointmentTransitionException.class);

			Appointment current = this.appointmentRepository.findById(app.getId()).orElseThrow();
			assertThat(current.getStatus()).isEqualTo(status);
			List<AppointmentChange> changes = this.changeRepository.findByAppointmentIdOrderByTimestampAsc(app.getId());
			assertThat(changes).hasSize(initialChanges);
		}
	}

	@Test
	void validTransitionsSucceedAndRecordChanges() {
		ZonedDateTime futureStart = ZonedDateTime.now(this.clock).plusDays(1);
		Appointment app = this.lifecycleService.bookAppointment(this.testPet, this.testVet, futureStart, 30,
				"Routine checkup", null, "owner_1");
		assertThat(app.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);

		Appointment cancelled = this.lifecycleService.ownerCancel(app, "owner_1");
		assertThat(cancelled.getStatus()).isEqualTo(AppointmentStatus.CANCELLED_BY_OWNER);

		List<AppointmentChange> changes = this.changeRepository.findByAppointmentIdOrderByTimestampAsc(app.getId());
		assertThat(changes).hasSize(2);
		assertThat(changes.get(0).getAction()).isEqualTo("BOOK");
		assertThat(changes.get(1).getAction()).isEqualTo("CANCEL_BY_OWNER");
	}

	@Test
	void markCompletedCreatesVisit() {
		ZonedDateTime pastStart = ZonedDateTime.now(this.clock).minusMinutes(10);
		Appointment app = createPersistedAppointment(AppointmentStatus.CONFIRMED, pastStart);

		Appointment completed = this.lifecycleService.markCompleted(app, "staff_1");
		assertThat(completed.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);

		assertThat(this.testPet.getVisits()).anyMatch(v -> v.getDescription().equals("Annual wellness exam"));
	}

}
