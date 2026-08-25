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

import static org.mockito.ArgumentMatchers.anyInt;
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
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.model.Appointment;
import org.springframework.samples.petclinic.scheduling.model.AppointmentStatus;
import org.springframework.samples.petclinic.security.UserAccount;
import org.springframework.samples.petclinic.security.UserAccountRepository;
import org.springframework.samples.petclinic.security.UserRole;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.security.SecurityConfig;

@WebMvcTest(OwnerAppointmentController.class)
class OwnerAppointmentControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private BookingService bookingService;

	@MockitoBean
	private UserAccountRepository userAccountRepository;

	private Owner owner1;

	private Owner owner2;

	private Appointment appointment1;

	private Appointment appointment2;

	@BeforeEach
	void setUp() {
		this.owner1 = new Owner();
		this.owner1.setId(1);
		this.owner1.setFirstName("George");
		this.owner1.setLastName("Franklin");

		this.owner2 = new Owner();
		this.owner2.setId(2);
		this.owner2.setFirstName("Betty");
		this.owner2.setLastName("Davis");

		Pet pet1 = new Pet();
		pet1.setId(1);
		pet1.setName("Leo");
		this.owner1.getPets().add(pet1);

		Vet vet = new Vet();
		vet.setId(1);
		vet.setFirstName("James");
		vet.setLastName("Carter");

		UserAccount userAccount1 = new UserAccount("george", "pass", UserRole.OWNER, false, this.owner1);
		UserAccount userAccount2 = new UserAccount("betty", "pass", UserRole.OWNER, false, this.owner2);

		when(this.userAccountRepository.findByUsername("george")).thenReturn(Optional.of(userAccount1));
		when(this.userAccountRepository.findByUsername("betty")).thenReturn(Optional.of(userAccount2));

		this.appointment1 = new Appointment();
		this.appointment1.setId(10);
		this.appointment1.setOwner(this.owner1);
		this.appointment1.setPet(pet1);
		this.appointment1.setVet(vet);
		this.appointment1.setStartTime(LocalDateTime.now().plusDays(2));
		this.appointment1.setEndTime(LocalDateTime.now().plusDays(2).plusMinutes(30));
		this.appointment1.setStatus(AppointmentStatus.BOOKED);

		this.appointment2 = new Appointment();
		this.appointment2.setId(20);
		this.appointment2.setOwner(this.owner2);
		this.appointment2.setPet(pet1);
		this.appointment2.setVet(vet);
		this.appointment2.setStartTime(LocalDateTime.now().plusDays(3));
		this.appointment2.setEndTime(LocalDateTime.now().plusDays(3).plusMinutes(30));
		this.appointment2.setStatus(AppointmentStatus.BOOKED);

		when(this.bookingService.getUpcomingAppointmentsForOwner(1)).thenReturn(List.of(this.appointment1));
		when(this.bookingService.getAppointment(10)).thenReturn(Optional.of(this.appointment1));
		when(this.bookingService.getAppointment(20)).thenReturn(Optional.of(this.appointment2));
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void shouldListUpcomingAppointmentsForOwner() throws Exception {
		// AC-56: Owner views own upcoming appointments
		this.mockMvc.perform(get("/my-appointments"))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/myAppointments"))
			.andExpect(model().attributeExists("appointments"));
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void shouldShowMyAppointmentDetails() throws Exception {
		this.mockMvc.perform(get("/my-appointments/10"))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/myAppointmentDetails"))
			.andExpect(model().attribute("appointment", this.appointment1));
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void shouldDenyViewingAnotherOwnersAppointment() throws Exception {
		// AC-58: Owner cannot access others' appointments
		this.mockMvc.perform(get("/my-appointments/20")).andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void shouldCancelOwnAppointmentBeforeStart() throws Exception {
		// AC-56: Owner cancels own upcoming appointment before start
		when(this.bookingService.cancelByOwner(anyInt(), anyInt())).thenReturn(this.appointment1);

		this.mockMvc.perform(post("/my-appointments/10/cancel").with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/my-appointments"));

		verify(this.bookingService).cancelByOwner(eq(10), eq(1));
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void shouldDenyCancelingAnotherOwnersAppointment() throws Exception {
		// AC-58: Owner cannot access or cancel others' appointments
		when(this.bookingService.cancelByOwner(eq(20), eq(1)))
			.thenThrow(new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.FORBIDDEN, "Access denied"));

		this.mockMvc.perform(post("/my-appointments/20/cancel").with(csrf())).andExpect(status().isForbidden());
	}

}
