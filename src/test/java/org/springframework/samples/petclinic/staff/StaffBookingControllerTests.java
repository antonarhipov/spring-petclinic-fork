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
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.appointment.Appointment;
import org.springframework.samples.petclinic.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.calendar.ClinicSettings;
import org.springframework.samples.petclinic.calendar.ClinicSettingsRepository;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.security.AppUserRepository;
import org.springframework.samples.petclinic.security.MustChangePasswordFilter;
import org.springframework.samples.petclinic.security.OwnerSecurity;
import org.springframework.samples.petclinic.security.SecurityConfig;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(StaffBookingController.class)
@WithMockUser(roles = "STAFF")
@DisabledInNativeImage
@DisabledInAotMode
class StaffBookingControllerTests {

	private static final int TEST_OWNER_ID = 1;

	private static final int TEST_PET_ID = 2;

	private static final int TEST_VET_ID = 3;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private StaffBookingService staffBookingService;

	@MockitoBean
	private OwnerRepository ownerRepository;

	@MockitoBean
	private VetRepository vetRepository;

	@MockitoBean
	private ClinicSettingsRepository clinicSettingsRepository;

	private ClinicSettings clinicSettings;

	private Owner owner;

	private Pet pet;

	private Vet vet;

	@BeforeEach
	void setUp() {
		this.clinicSettings = new ClinicSettings();
		this.clinicSettings.setZoneId("Europe/Amsterdam");
		this.clinicSettings.setBookingHorizonDays(60);
		this.clinicSettings.setDefaultVisitMin(30);
		this.clinicSettings.setMinVisitMin(15);
		this.clinicSettings.setMaxVisitMin(120);
		this.clinicSettings.setGridGranularityMin(15);

		this.owner = new Owner();
		this.owner.setId(TEST_OWNER_ID);
		this.owner.setFirstName("George");
		this.owner.setLastName("Franklin");

		this.pet = new Pet();
		this.owner.addPet(this.pet);
		this.pet.setId(TEST_PET_ID);
		this.pet.setName("Leo");

		this.vet = new Vet();
		this.vet.setId(TEST_VET_ID);
		this.vet.setFirstName("James");
		this.vet.setLastName("Carter");

		given(this.clinicSettingsRepository.getClinicSettings()).willReturn(this.clinicSettings);
		given(this.ownerRepository.findById(TEST_OWNER_ID)).willReturn(Optional.of(this.owner));
		given(this.vetRepository.findAll()).willReturn(List.of(this.vet));
		given(this.vetRepository.findById(TEST_VET_ID)).willReturn(Optional.of(this.vet));
	}

	@Test
	@WithMockUser(roles = "STAFF")
	void initNewAppointmentFormRendersStaffForm() throws Exception {
		Instant slot = Instant.parse("2026-09-01T08:00:00Z");
		given(this.staffBookingService.getAvailableSlots(eq(TEST_VET_ID), any(LocalDate.class), eq(30)))
			.willReturn(List.of(slot));

		this.mockMvc
			.perform(get("/owners/{ownerId}/pets/{petId}/appointments/new", TEST_OWNER_ID, TEST_PET_ID)
				.param("vetId", String.valueOf(TEST_VET_ID))
				.param("durationMin", "30"))
			.andExpect(status().isOk())
			.andExpect(view().name("staff/bookAppointmentForm"))
			.andExpect(model().attributeExists("owner", "pet", "vets", "slots", "selectedDate"));
	}

	@Test
	@WithMockUser(roles = "STAFF")
	void processNewAppointmentFormSuccess() throws Exception {
		Instant start = Instant.parse("2026-09-01T08:00:00Z");
		Appointment appointment = new Appointment();
		appointment.setPet(this.pet);
		appointment.setVet(this.vet);
		appointment.setStartInstant(start);
		appointment.setDurationMin(30);
		appointment.setStatus(AppointmentStatus.SCHEDULED);

		given(this.staffBookingService.bookDirectAppointment(eq(TEST_OWNER_ID), eq(TEST_PET_ID), eq(TEST_VET_ID),
				eq(start), eq(30), eq("Routine Checkup")))
			.willReturn(appointment);

		this.mockMvc
			.perform(post("/owners/{ownerId}/pets/{petId}/appointments/new", TEST_OWNER_ID, TEST_PET_ID).with(csrf())
				.param("vetId", String.valueOf(TEST_VET_ID))
				.param("startInstant", start.toString())
				.param("durationMin", "30")
				.param("reason", "Routine Checkup"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/owners/" + TEST_OWNER_ID))
			.andExpect(flash().attributeExists("message"));
	}

	@Test
	@WithMockUser(roles = "STAFF")
	void processNewAppointmentFormFailsOnCollisionAndRedirects() throws Exception {
		Instant start = Instant.parse("2026-09-01T08:00:00Z");
		given(this.staffBookingService.bookDirectAppointment(anyInt(), anyInt(), anyInt(), any(Instant.class), anyInt(),
				any()))
			.willThrow(new IllegalStateException("The selected slot is no longer available"));

		this.mockMvc
			.perform(post("/owners/{ownerId}/pets/{petId}/appointments/new", TEST_OWNER_ID, TEST_PET_ID).with(csrf())
				.param("vetId", String.valueOf(TEST_VET_ID))
				.param("startInstant", start.toString())
				.param("durationMin", "30"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl(
					"/owners/" + TEST_OWNER_ID + "/pets/" + TEST_PET_ID + "/appointments/new?vetId=" + TEST_VET_ID))
			.andExpect(flash().attributeExists("error"));
	}

	@Test
	@WithMockUser(roles = "STAFF")
	void initDirectBookingFormRedirectsCorrectly() throws Exception {
		this.mockMvc
			.perform(get("/staff/appointments/new").param("ownerId", "1")
				.param("petId", "2")
				.param("vetId", "3")
				.param("durationMin", "30"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/owners/1/pets/2/appointments/new?vetId=3&durationMin=30"));

		this.mockMvc.perform(get("/staff/appointments/new"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/owners/find"));
	}

}
