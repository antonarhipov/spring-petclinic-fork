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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.PetRepository;
import org.springframework.samples.petclinic.scheduling.controller.StaffAppointmentController;
import org.springframework.samples.petclinic.scheduling.dto.AppointmentDto;
import org.springframework.samples.petclinic.scheduling.model.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.model.BookingSource;
import org.springframework.samples.petclinic.scheduling.model.CareType;
import org.springframework.samples.petclinic.scheduling.model.ClinicSettings;
import org.springframework.samples.petclinic.scheduling.service.AppointmentService;
import org.springframework.samples.petclinic.scheduling.service.ClinicSettingsService;
import org.springframework.samples.petclinic.scheduling.service.SchedulingTimeService;
import org.springframework.samples.petclinic.security.Account;
import org.springframework.samples.petclinic.security.AccountRepository;
import org.springframework.samples.petclinic.security.AccountRole;
import org.springframework.samples.petclinic.security.CustomAuthenticationFailureHandler;
import org.springframework.samples.petclinic.security.CustomAuthenticationSuccessHandler;
import org.springframework.samples.petclinic.security.UserPrincipal;
import org.springframework.samples.petclinic.security.WebMvcSecurityTestConfig;
import org.springframework.samples.petclinic.system.ResourceNotFoundException;
import org.springframework.samples.petclinic.vet.SpecialtyRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StaffAppointmentController.class)
@Import(WebMvcSecurityTestConfig.class)
class StaffAppointmentControllerTests {

	private static final int APPOINTMENT_ID = 200;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AppointmentService appointmentService;

	@MockitoBean
	private OwnerRepository ownerRepository;

	@MockitoBean
	private PetRepository petRepository;

	@MockitoBean
	private VetRepository vetRepository;

	@MockitoBean
	private SpecialtyRepository specialtyRepository;

	@MockitoBean
	private ClinicSettingsService clinicSettingsService;

	@MockitoBean
	private SchedulingTimeService timeService;

	@MockitoBean
	private AccountRepository accountRepository;

	@MockitoBean
	private CustomAuthenticationSuccessHandler successHandler;

	@MockitoBean
	private CustomAuthenticationFailureHandler failureHandler;

	private UserPrincipal staffPrincipal;

	private UserPrincipal ownerPrincipal;

	private AppointmentDto appointmentDto;

	private Owner owner;

	private Vet vet;

	@BeforeEach
	void setUp() {
		Account staffAccount = new Account();
		staffAccount.setId(30);
		staffAccount.setUsername("staff1");
		staffAccount.setRole(AccountRole.STAFF);
		staffPrincipal = UserPrincipal.fromAccount(staffAccount);

		Account ownerAccount = new Account();
		ownerAccount.setId(40);
		ownerAccount.setUsername("owner1");
		ownerAccount.setRole(AccountRole.OWNER);
		ownerPrincipal = UserPrincipal.fromAccount(ownerAccount);

		owner = new Owner();
		owner.setId(1);
		owner.setFirstName("George");
		owner.setLastName("Franklin");

		Pet pet = new Pet();
		pet.setId(10);
		pet.setName("Leo");
		owner.addPet(pet);

		vet = new Vet();
		vet.setId(5);
		vet.setFirstName("James");
		vet.setLastName("Carter");
		vet.setActive(true);

		Instant start = Instant.parse("2026-08-28T10:00:00Z");
		Instant end = Instant.parse("2026-08-28T10:30:00Z");
		appointmentDto = new AppointmentDto(APPOINTMENT_ID, 1, "George Franklin", 10, "Leo", 5, "James Carter", start,
				end, 30, CareType.GENERAL, null, null, AppointmentStatus.BOOKED, BookingSource.STAFF_DIRECT, null,
				"Phone direct booking", null, null, null, 0L, null);

		given(timeService.getClinicZoneId()).willReturn(ZoneId.of("UTC"));
		given(clinicSettingsService.getSettings()).willReturn(new ClinicSettings());
	}

	@Test
	void testUnauthenticatedAccessRedirectsToLogin() throws Exception {
		mockMvc.perform(get("/scheduling/staff/appointments/" + APPOINTMENT_ID)).andExpect(status().is3xxRedirection());
	}

	@Test
	void testOwnerRoleForbiddenForStaffRoutes() throws Exception {
		mockMvc.perform(get("/scheduling/staff/appointments/" + APPOINTMENT_ID).with(user(ownerPrincipal)))
			.andExpect(status().isForbidden());
	}

	@Test
	void testShowAppointmentDetailsStaffSuccess() throws Exception {
		given(appointmentService.getAppointmentDto(APPOINTMENT_ID)).willReturn(appointmentDto);

		mockMvc.perform(get("/scheduling/staff/appointments/" + APPOINTMENT_ID).with(user(staffPrincipal)))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/staffAppointmentDetail"))
			.andExpect(model().attributeExists("appointment"));
	}

	@Test
	void testShowAppointmentDetailsNotFound() throws Exception {
		given(appointmentService.getAppointmentDto(APPOINTMENT_ID))
			.willThrow(new ResourceNotFoundException("Appointment not found"));

		mockMvc.perform(get("/scheduling/staff/appointments/" + APPOINTMENT_ID).with(user(staffPrincipal)))
			.andExpect(status().isNotFound());
	}

	@Test
	void testShowDirectBookingFormSuccess() throws Exception {
		given(ownerRepository.findAll()).willReturn(List.of(owner));
		given(vetRepository.findByActiveTrueOrderById()).willReturn(List.of(vet));
		given(specialtyRepository.findByActiveTrueOrderByName()).willReturn(Collections.emptyList());
		given(ownerRepository.findById(1)).willReturn(Optional.of(owner));

		mockMvc.perform(get("/scheduling/staff/appointments/new?ownerId=1&petId=10").with(user(staffPrincipal)))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/staffDirectBook"))
			.andExpect(model().attributeExists("form", "owners", "vets", "specialties", "pets"));
	}

	@Test
	void testSubmitDirectBookingSuccess() throws Exception {
		Instant slotStart = Instant.parse("2026-08-28T14:00:00Z");
		given(appointmentService.directBookByStaff(eq(1), eq(10), eq(5), eq(slotStart), eq(30), eq(CareType.GENERAL),
				eq(null), eq("Phone call booking"), eq("staff1")))
			.willReturn(appointmentDto);

		mockMvc
			.perform(post("/scheduling/staff/appointments").with(user(staffPrincipal))
				.with(csrf())
				.param("ownerId", "1")
				.param("petId", "10")
				.param("vetId", "5")
				.param("startDateTime", "2026-08-28T14:00:00Z")
				.param("durationMinutes", "30")
				.param("careType", "GENERAL")
				.param("offlineReason", "Phone call booking"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/staff/appointments/" + APPOINTMENT_ID))
			.andExpect(flash().attributeExists("successMessage"));
	}

	@Test
	void testSubmitDirectBookingFailureRedirectsWithErrorMessage() throws Exception {
		Instant slotStart = Instant.parse("2026-08-28T14:00:00Z");
		given(appointmentService.directBookByStaff(any(), any(), any(), any(), any(), any(), any(), any(), any()))
			.willThrow(new IllegalStateException("Selected slot is not feasible"));

		mockMvc
			.perform(post("/scheduling/staff/appointments").with(user(staffPrincipal))
				.with(csrf())
				.param("ownerId", "1")
				.param("petId", "10")
				.param("vetId", "5")
				.param("startDateTime", "2026-08-28T14:00:00Z")
				.param("durationMinutes", "30")
				.param("careType", "GENERAL")
				.param("offlineReason", "Phone call booking"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/staff/appointments/new?ownerId=1&petId=10"))
			.andExpect(flash().attribute("errorMessage", "Selected slot is not feasible"));
	}

	@Test
	void testShowRescheduleFormSuccess() throws Exception {
		given(appointmentService.getAppointmentDto(APPOINTMENT_ID)).willReturn(appointmentDto);
		given(vetRepository.findByActiveTrueOrderById()).willReturn(List.of(vet));

		mockMvc
			.perform(get("/scheduling/staff/appointments/" + APPOINTMENT_ID + "/reschedule").with(user(staffPrincipal)))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/staffReschedule"))
			.andExpect(model().attributeExists("appointment", "form", "vets"));
	}

	@Test
	void testSubmitRescheduleSuccess() throws Exception {
		Instant newStart = Instant.parse("2026-08-29T10:00:00Z");
		given(appointmentService.rescheduleAppointment(eq(APPOINTMENT_ID), eq(5), eq(newStart), eq(45),
				eq("Client request"), eq("staff1")))
			.willReturn(appointmentDto);

		mockMvc
			.perform(post("/scheduling/staff/appointments/" + APPOINTMENT_ID + "/reschedule").with(user(staffPrincipal))
				.with(csrf())
				.param("vetId", "5")
				.param("startDateTime", "2026-08-29T10:00:00Z")
				.param("durationMinutes", "45")
				.param("rescheduleReason", "Client request"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/staff/appointments/" + APPOINTMENT_ID))
			.andExpect(flash().attributeExists("successMessage"));
	}

	@Test
	void testSubmitCancelByStaffSuccess() throws Exception {
		given(appointmentService.cancelAppointmentByStaff(eq(APPOINTMENT_ID), eq("Vet emergency leave"), eq("staff1")))
			.willReturn(appointmentDto);

		mockMvc
			.perform(post("/scheduling/staff/appointments/" + APPOINTMENT_ID + "/cancel").with(user(staffPrincipal))
				.with(csrf())
				.param("cancellationReason", "Vet emergency leave"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/staff/appointments/" + APPOINTMENT_ID))
			.andExpect(flash().attributeExists("successMessage"));
	}

	@Test
	void testSubmitCompleteAppointmentSuccess() throws Exception {
		given(appointmentService.completeAppointment(eq(APPOINTMENT_ID), eq("Dental surgery successful"), eq("staff1")))
			.willReturn(appointmentDto);

		mockMvc
			.perform(post("/scheduling/staff/appointments/" + APPOINTMENT_ID + "/complete").with(user(staffPrincipal))
				.with(csrf())
				.param("completionNotes", "Dental surgery successful"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/staff/appointments/" + APPOINTMENT_ID))
			.andExpect(flash().attributeExists("successMessage"));
	}

	@Test
	void testSubmitMarkNoShowSuccess() throws Exception {
		given(appointmentService.markNoShow(eq(APPOINTMENT_ID), eq("staff1"))).willReturn(appointmentDto);

		mockMvc
			.perform(post("/scheduling/staff/appointments/" + APPOINTMENT_ID + "/no-show").with(user(staffPrincipal))
				.with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/staff/appointments/" + APPOINTMENT_ID))
			.andExpect(flash().attributeExists("successMessage"));
	}

	@Test
	void testPostWithoutCsrfForbidden() throws Exception {
		mockMvc
			.perform(post("/scheduling/staff/appointments/" + APPOINTMENT_ID + "/no-show").with(user(staffPrincipal)))
			.andExpect(status().isForbidden());
	}

}
