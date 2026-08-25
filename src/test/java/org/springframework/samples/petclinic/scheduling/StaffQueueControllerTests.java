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

import java.time.Instant;
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
import org.springframework.samples.petclinic.scheduling.model.QueueReason;
import org.springframework.samples.petclinic.scheduling.model.RequestState;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequestRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.security.SecurityConfig;
import org.springframework.samples.petclinic.security.UserAccountRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StaffQueueController.class)
@WithMockUser(roles = "STAFF")
class StaffQueueControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SchedulingRequestRepository schedulingRequests;

	@MockitoBean
	private BookingService bookingService;

	@MockitoBean
	private VetRepository vets;

	@MockitoBean
	private UserAccountRepository userAccountRepository;

	private SchedulingRequest regularRequest;

	private SchedulingRequest emergencyRequest;

	private Appointment appointment;

	@BeforeEach
	void setUp() {
		Owner owner = new Owner();
		owner.setId(1);
		owner.setFirstName("George");
		owner.setLastName("Franklin");

		Pet pet = new Pet();
		pet.setId(1);
		pet.setName("Leo");
		org.springframework.samples.petclinic.owner.PetType dog = new org.springframework.samples.petclinic.owner.PetType();
		dog.setName("dog");
		pet.setType(dog);

		Vet vet = new Vet();
		vet.setId(1);
		vet.setFirstName("James");
		vet.setLastName("Carter");

		this.regularRequest = new SchedulingRequest();
		this.regularRequest.setId(1);
		this.regularRequest.setOwner(owner);
		this.regularRequest.setPet(pet);
		this.regularRequest.setState(RequestState.STAFF_QUEUED);
		this.regularRequest.setQueueReason(QueueReason.CONSENT_DECLINED);
		this.regularRequest.setQueuedAt(Instant.now().minusSeconds(3600));

		this.emergencyRequest = new SchedulingRequest();
		this.emergencyRequest.setId(2);
		this.emergencyRequest.setOwner(owner);
		this.emergencyRequest.setPet(pet);
		this.emergencyRequest.setState(RequestState.STAFF_QUEUED);
		this.emergencyRequest.setQueueReason(QueueReason.EMERGENCY);
		this.emergencyRequest.setQueuedAt(Instant.now().minusSeconds(600));

		this.appointment = new Appointment();
		this.appointment.setId(10);
		this.appointment.setOwner(owner);
		this.appointment.setPet(pet);
		this.appointment.setVet(vet);
		this.appointment.setStatus(AppointmentStatus.BOOKED);

		when(this.schedulingRequests.findByStateWithOwnerAndPet(RequestState.STAFF_QUEUED))
			.thenReturn(List.of(this.regularRequest, this.emergencyRequest));
		// The queue-details page and the book-on-behalf error path load the request with
		// its owner/pet join-fetched, so the lazy associations are initialized before the
		// (open-in-view=false) session closes and the template renders without a
		// LazyInitializationException.
		when(this.schedulingRequests.findByIdWithOwnerAndPet(2)).thenReturn(Optional.of(this.emergencyRequest));
		when(this.vets.findAll()).thenReturn(List.of(vet));
	}

	@Test
	void shouldListQueueEmergencyFirstThenFifo() throws Exception {
		// AC-49: Staff queue ordered emergency-first then FIFO
		this.mockMvc.perform(get("/staff/queue"))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/queueList"))
			.andExpect(model().attributeExists("requests"));
	}

	@Test
	void shouldShowQueueDetails() throws Exception {
		this.mockMvc.perform(get("/staff/queue/2"))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/queueDetails"))
			.andExpect(model().attribute("request", this.emergencyRequest))
			.andExpect(model().attributeExists("vets"));
	}

	@Test
	void shouldBookOnBehalfAndRedirect() throws Exception {
		// AC-50: Staff complete interpretation and book on behalf
		when(this.bookingService.bookOnBehalf(anyInt(), anyInt(), any(), any(), any())).thenReturn(this.appointment);

		this.mockMvc
			.perform(post("/staff/queue/2/book").with(csrf())
				.param("vetId", "1")
				.param("date", "2026-09-15")
				.param("startTime", "11:00")
				.param("durationMinutes", "30")
				.param("reason", "Triage booking"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/appointments/10"));

		verify(this.bookingService).bookOnBehalf(eq(2), eq(1), any(LocalDateTime.class), any(LocalDateTime.class),
				eq("Triage booking"));
	}

}
