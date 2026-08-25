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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.model.Appointment;
import org.springframework.samples.petclinic.scheduling.model.AppointmentStatus;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.samples.petclinic.security.SecurityConfig;
import org.springframework.samples.petclinic.security.UserAccountRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;

@WebMvcTest(StaffAppointmentController.class)
@WithMockUser(roles = "STAFF")
class StaffAppointmentControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private BookingService bookingService;

	@MockitoBean
	private OwnerRepository owners;

	@MockitoBean
	private VetRepository vets;

	@MockitoBean
	private UserAccountRepository userAccountRepository;

	private Appointment appointment;

	private Owner owner;

	private Pet pet;

	private Vet vet;

	@BeforeEach
	void setUp() {
		this.owner = new Owner();
		this.owner.setId(1);
		this.owner.setFirstName("George");
		this.owner.setLastName("Franklin");

		this.pet = new Pet();
		this.pet.setId(1);
		this.pet.setName("Leo");
		this.owner.getPets().add(this.pet);

		this.vet = new Vet();
		this.vet.setId(1);
		this.vet.setFirstName("James");
		this.vet.setLastName("Carter");

		this.appointment = new Appointment();
		this.appointment.setId(10);
		this.appointment.setOwner(this.owner);
		this.appointment.setPet(this.pet);
		this.appointment.setVet(this.vet);
		this.appointment.setStartTime(LocalDateTime.of(2026, 9, 10, 10, 0));
		this.appointment.setEndTime(LocalDateTime.of(2026, 9, 10, 10, 30));
		this.appointment.setStatus(AppointmentStatus.BOOKED);
		this.appointment.setReason("Annual Checkup");

		when(this.bookingService.getAppointment(10)).thenReturn(Optional.of(this.appointment));
		when(this.bookingService.getAllAppointments()).thenReturn(List.of(this.appointment));
		when(this.owners.findAll()).thenReturn(List.of(this.owner));
		when(this.vets.findAll()).thenReturn(List.of(this.vet));
	}

	@Test
	void shouldListAppointmentsForStaff() throws Exception {
		this.mockMvc.perform(get("/staff/appointments"))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/staffAppointmentsList"))
			.andExpect(model().attributeExists("appointments"));
	}

	@Test
	void shouldShowDirectBookingForm() throws Exception {
		this.mockMvc.perform(get("/staff/appointments/new"))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/staffDirectBookingForm"))
			.andExpect(model().attributeExists("owners", "vets"));
	}

	@Test
	void shouldProcessDirectBookingForm() throws Exception {
		when(this.bookingService.bookDirect(anyInt(), anyInt(), anyInt(), any(), any(), any()))
			.thenReturn(this.appointment);

		this.mockMvc
			.perform(post("/staff/appointments/new").with(csrf())
				.param("ownerId", "1")
				.param("petId", "1")
				.param("vetId", "1")
				.param("date", "2026-09-10")
				.param("startTime", "10:00")
				.param("durationMinutes", "30")
				.param("reason", "Direct checkup"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/appointments/10"));

		verify(this.bookingService).bookDirect(eq(1), eq(1), eq(1), any(), any(), eq("Direct checkup"));
	}

	@Test
	void shouldShowAppointmentDetails() throws Exception {
		this.mockMvc.perform(get("/staff/appointments/10"))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/staffAppointmentDetails"))
			.andExpect(model().attribute("appointment", this.appointment));
	}

	@Test
	void shouldShowRescheduleForm() throws Exception {
		this.mockMvc.perform(get("/staff/appointments/10/reschedule"))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/staffRescheduleForm"))
			.andExpect(model().attributeExists("appointment", "vets"));
	}

	@Test
	void shouldProcessRescheduleForm() throws Exception {
		when(this.bookingService.rescheduleAppointment(anyInt(), anyInt(), any(), any(), anyString()))
			.thenReturn(this.appointment);

		this.mockMvc
			.perform(post("/staff/appointments/10/reschedule").with(csrf())
				.param("vetId", "1")
				.param("date", "2026-09-12")
				.param("startTime", "14:00")
				.param("durationMinutes", "30")
				.param("changeReason", "Owner requested afternoon"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/appointments/10"));

		verify(this.bookingService).rescheduleAppointment(eq(10), eq(1), any(), any(), eq("Owner requested afternoon"));
	}

	@Test
	void shouldProcessStaffCancel() throws Exception {
		when(this.bookingService.cancelByStaff(anyInt(), anyString())).thenReturn(this.appointment);

		this.mockMvc
			.perform(post("/staff/appointments/10/cancel").with(csrf()).param("changeReason", "Emergency vet leave"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/appointments/10"));

		verify(this.bookingService).cancelByStaff(eq(10), eq("Emergency vet leave"));
	}

	@Test
	void shouldProcessCompleteAppointment() throws Exception {
		when(this.bookingService.completeAppointment(anyInt())).thenReturn(this.appointment);

		this.mockMvc.perform(post("/staff/appointments/10/complete").with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/appointments/10"));

		verify(this.bookingService).completeAppointment(eq(10));
	}

	@Test
	void shouldProcessNoShowAppointment() throws Exception {
		when(this.bookingService.markNoShow(anyInt())).thenReturn(this.appointment);

		this.mockMvc.perform(post("/staff/appointments/10/no-show").with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/appointments/10"));

		verify(this.bookingService).markNoShow(eq(10));
	}

}
