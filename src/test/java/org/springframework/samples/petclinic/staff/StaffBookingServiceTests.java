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
package org.springframework.samples.petclinic.staff;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.samples.petclinic.appointment.Appointment;
import org.springframework.samples.petclinic.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.appointment.AppointmentRequestWorkflowService;
import org.springframework.samples.petclinic.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.calendar.ClinicSettings;
import org.springframework.samples.petclinic.calendar.ClinicSettingsRepository;
import org.springframework.samples.petclinic.calendar.EffectiveAvailabilityResolver;
import org.springframework.samples.petclinic.calendar.GridGenerator;
import org.springframework.samples.petclinic.calendar.InstantInterval;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StaffBookingServiceTests {

	@Mock
	private ClinicSettingsRepository clinicSettingsRepository;

	@Mock
	private EffectiveAvailabilityResolver availabilityResolver;

	@Mock
	private AppointmentRepository appointmentRepository;

	@Mock
	private AppointmentRequestWorkflowService workflowService;

	@Mock
	private OwnerRepository ownerRepository;

	@Mock
	private VetRepository vetRepository;

	private GridGenerator gridGenerator;

	private StaffBookingService staffBookingService;

	private ClinicSettings clinicSettings;

	private final ZoneId zoneId = ZoneId.of("Europe/Amsterdam");

	@BeforeEach
	void setUp() {
		this.clinicSettings = new ClinicSettings();
		this.clinicSettings.setGridGranularityMin(15);
		this.clinicSettings.setDefaultVisitMin(30);
		this.clinicSettings.setMinVisitMin(15);
		this.clinicSettings.setMaxVisitMin(120);
		this.clinicSettings.setBookingHorizonDays(60);
		this.clinicSettings.setZoneId(this.zoneId.getId());

		this.gridGenerator = new GridGenerator(this.availabilityResolver, this.clinicSettingsRepository);
		this.staffBookingService = new StaffBookingService(this.clinicSettingsRepository, this.gridGenerator,
				this.appointmentRepository, this.ownerRepository, this.vetRepository, this.workflowService);

		given(this.clinicSettingsRepository.getClinicSettings()).willReturn(this.clinicSettings);
	}

	@Test
	void getAvailableSlotsFiltersOutOverlappingAppointments() {
		int vetId = 1;
		LocalDate targetDate = LocalDate.now(this.zoneId).plusDays(2);

		// Vet has availability from 09:00 to 12:00 (UTC 07:00 to 10:00 for CEST, or 08:00
		// to 11:00 for CET)
		Instant startInterval = Instant.parse("2026-09-01T07:00:00Z");
		Instant endInterval = Instant.parse("2026-09-01T10:00:00Z");
		given(this.availabilityResolver.resolve(eq(vetId), eq(targetDate), any(ClinicSettings.class)))
			.willReturn(List.of(new InstantInterval(startInterval, endInterval)));

		// Existing appointment from 07:30 to 08:00
		Appointment existing = new Appointment();
		existing.setId(10);
		existing.setStartInstant(Instant.parse("2026-09-01T07:30:00Z"));
		existing.setDurationMin(30);
		existing.setStatus(AppointmentStatus.SCHEDULED);

		// Cancelled appointment from 08:30 to 09:00 (should not block slots)
		Appointment cancelled = new Appointment();
		cancelled.setId(11);
		cancelled.setStartInstant(Instant.parse("2026-09-01T08:30:00Z"));
		cancelled.setDurationMin(30);
		cancelled.setStatus(AppointmentStatus.CANCELLED);

		given(this.appointmentRepository.findByVetIdAndStatusNot(vetId, AppointmentStatus.CANCELLED))
			.willReturn(List.of(existing));

		List<Instant> available = this.staffBookingService.getAvailableSlots(vetId, targetDate, 30);

		// Slots without existing appointment: 07:00, 07:15, 07:30, 07:45, 08:00, 08:15,
		// 08:30, 08:45, 09:00, 09:15, 09:30
		// Collisions for duration=30:
		// 07:00 [07:00-07:30] -> NO collision
		// 07:15 [07:15-07:45] -> COLLISION with [07:30-08:00]
		// 07:30 [07:30-08:00] -> COLLISION with [07:30-08:00]
		// 07:45 [07:45-08:15] -> COLLISION with [07:30-08:00]
		// 08:00 [08:00-08:30] -> NO collision
		assertThat(available).contains(Instant.parse("2026-09-01T07:00:00Z"), Instant.parse("2026-09-01T08:00:00Z"),
				Instant.parse("2026-09-01T08:30:00Z"), Instant.parse("2026-09-01T09:30:00Z"));
		assertThat(available).doesNotContain(Instant.parse("2026-09-01T07:15:00Z"),
				Instant.parse("2026-09-01T07:30:00Z"), Instant.parse("2026-09-01T07:45:00Z"));
	}

	@Test
	void getAvailableSlotsReturnsEmptyForDatePastHorizon() {
		int vetId = 1;
		LocalDate farFuture = LocalDate.now(this.zoneId).plusDays(61);

		List<Instant> slots = this.staffBookingService.getAvailableSlots(vetId, farFuture, 30);
		assertThat(slots).isEmpty();
	}

	@Test
	void bookDirectAppointmentSuccessfullyCreatesScheduledAppointment() {
		int ownerId = 1;
		int petId = 2;
		int vetId = 3;

		LocalDate targetDate = LocalDate.now(this.zoneId).plusDays(3);
		Instant slotStart = targetDate.atTime(LocalTime.of(9, 0)).atZone(this.zoneId).toInstant();
		Instant slotEnd = slotStart.plusSeconds(30 * 60);

		Owner owner = new Owner();
		owner.setId(ownerId);
		Pet pet = new Pet();
		owner.addPet(pet);
		pet.setId(petId);
		pet.setName("Leo");

		Vet vet = new Vet();
		vet.setId(vetId);

		given(this.ownerRepository.findById(ownerId)).willReturn(Optional.of(owner));
		given(this.vetRepository.findById(vetId)).willReturn(Optional.of(vet));
		given(this.availabilityResolver.resolve(eq(vetId), eq(targetDate), any(ClinicSettings.class)))
			.willReturn(List.of(new InstantInterval(slotStart, slotEnd)));
		given(this.appointmentRepository.findByVetIdAndStatusNot(vetId, AppointmentStatus.CANCELLED))
			.willReturn(List.of());

		given(this.appointmentRepository.saveAndFlush(any(Appointment.class))).willAnswer(inv -> inv.getArgument(0));

		Appointment booked = this.staffBookingService.bookDirectAppointment(ownerId, petId, vetId, slotStart, 30,
				"Direct staff booking");

		assertThat(booked).isNotNull();
		assertThat(booked.getRequest()).isNull();
		assertThat(booked.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
		assertThat(booked.getPet()).isEqualTo(pet);
		assertThat(booked.getVet()).isEqualTo(vet);
		assertThat(booked.getStartInstant()).isEqualTo(slotStart);
		assertThat(booked.getDurationMin()).isEqualTo(30);
		assertThat(booked.getReason()).isEqualTo("Direct staff booking");
	}

	@Test
	void bookDirectAppointmentFailsWhenSlotUnavailable() {
		int ownerId = 1;
		int petId = 2;
		int vetId = 3;

		LocalDate targetDate = LocalDate.now(this.zoneId).plusDays(3);
		Instant slotStart = targetDate.atTime(LocalTime.of(9, 0)).atZone(this.zoneId).toInstant();

		Owner owner = new Owner();
		owner.setId(ownerId);
		Pet pet = new Pet();
		owner.addPet(pet);
		pet.setId(petId);

		Vet vet = new Vet();
		vet.setId(vetId);

		given(this.ownerRepository.findById(ownerId)).willReturn(Optional.of(owner));
		given(this.vetRepository.findById(vetId)).willReturn(Optional.of(vet));
		// Vet has NO availability intervals
		given(this.availabilityResolver.resolve(eq(vetId), eq(targetDate), any(ClinicSettings.class)))
			.willReturn(List.of());

		assertThatThrownBy(() -> this.staffBookingService.bookDirectAppointment(ownerId, petId, vetId, slotStart, 30,
				"Direct booking"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("no longer available");
	}

}
