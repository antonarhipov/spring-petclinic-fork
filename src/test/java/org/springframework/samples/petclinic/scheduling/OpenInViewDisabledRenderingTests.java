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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises server-rendered pages without a test transaction or an open persistence
 * context. Any lazy association not loaded by the application service/repository boundary
 * therefore fails here in the same way it would in production with OSIV disabled.
 */
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@AutoConfigureMockMvc
class OpenInViewDisabledRenderingTests {

	@Autowired
	private MockMvc mockMvc;

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

	private Integer appointmentId;

	private Integer requestId;

	private Integer holdId;

	private LocalDateTime startTime;

	@BeforeEach
	void setUp() {
		Owner owner = this.owners.findById(1).orElseThrow();
		Pet pet = owner.getPets().get(0);
		Vet vet = this.vets.findById(1).orElseThrow();

		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(owner);
		request.setPet(pet);
		request.setRawText("OSIV rendering check");
		request.setState(RequestState.CONFIRMED);
		request = this.schedulingRequests.saveAndFlush(request);
		this.requestId = request.getId();

		this.startTime = LocalDateTime.now().plusDays(20).withSecond(0).withNano(0);
		Appointment appointment = new Appointment();
		appointment.setOwner(owner);
		appointment.setPet(pet);
		appointment.setVet(vet);
		appointment.setSchedulingRequest(request);
		appointment.setStartTime(this.startTime);
		appointment.setEndTime(this.startTime.plusMinutes(30));
		appointment.setStatus(AppointmentStatus.BOOKED);
		appointment.setReason("OSIV rendering check");
		this.appointmentId = this.appointments.saveAndFlush(appointment).getId();
	}

	@AfterEach
	void tearDown() {
		if (this.appointmentId != null && this.appointments.existsById(this.appointmentId)) {
			this.appointments.deleteById(this.appointmentId);
		}
		if (this.holdId != null && this.holds.existsById(this.holdId)) {
			this.holds.deleteById(this.holdId);
		}
		if (this.requestId != null && this.schedulingRequests.existsById(this.requestId)) {
			this.schedulingRequests.deleteById(this.requestId);
		}
	}

	@Test
	void staffAppointmentPagesRender() throws Exception {
		this.mockMvc.perform(get("/staff/appointments").with(user("admin").roles("STAFF")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("OSIV rendering check")));

		this.mockMvc.perform(get("/staff/appointments").param("status", "BOOKED").with(user("admin").roles("STAFF")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("OSIV rendering check")));

		this.mockMvc
			.perform(get("/staff/appointments/{appointmentId}", this.appointmentId).with(user("admin").roles("STAFF")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("OSIV rendering check")));

		this.mockMvc
			.perform(get("/staff/appointments/{appointmentId}/reschedule", this.appointmentId)
				.with(user("admin").roles("STAFF")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("George Franklin")));
	}

	@Test
	void ownerAppointmentAndConfirmedRequestPagesRender() throws Exception {
		this.mockMvc
			.perform(get("/my-appointments/{appointmentId}", this.appointmentId).with(user("george").roles("OWNER")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("OSIV rendering check")));

		this.mockMvc
			.perform(get("/scheduling/requests/{requestId}", this.requestId).with(user("george").roles("OWNER")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Appointment Confirmed")))
			.andExpect(content().string(containsString("James Carter")));
	}

	@Test
	void heldSlotPageRendersVetDetails() throws Exception {
		SchedulingRequest request = this.schedulingRequests.findById(this.requestId).orElseThrow();
		request.setState(RequestState.SLOT_HELD);
		this.schedulingRequests.saveAndFlush(request);

		Hold hold = new Hold();
		hold.setSchedulingRequest(request);
		hold.setVet(this.vets.findById(1).orElseThrow());
		hold.setStartTime(this.startTime.plusDays(1));
		hold.setEndTime(this.startTime.plusDays(1).plusMinutes(30));
		hold.setExpiresAt(Instant.now().plusSeconds(3600));
		hold.setStatus(HoldStatus.ACTIVE);
		this.holdId = this.holds.saveAndFlush(hold).getId();

		this.mockMvc
			.perform(get("/scheduling/requests/{requestId}", this.requestId).with(user("george").roles("OWNER")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Suggested Appointment Slot")))
			.andExpect(content().string(containsString("James Carter")));
	}

	@Test
	void ownerRequestActionChecksOwnershipWithoutOsiv() throws Exception {
		this.mockMvc
			.perform(post("/scheduling/requests/{requestId}/cancel", this.requestId).with(user("george").roles("OWNER"))
				.with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/scheduling/requests/" + this.requestId));
	}

}
