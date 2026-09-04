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

package org.springframework.samples.petclinic.scheduling;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.request.RequestLifecycleService;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class CalendarRenderingTests {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private Clock clock;

	@Autowired
	private VetRepository vetRepository;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private RequestLifecycleService lifecycleService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
	}

	@Test
	@Tag("AC-96")
	void dayGridHasVetColumnsRowsFiveCellStatesAndNavigation_AC96() throws Exception {
		MockHttpSession staffSession = loginStaff();
		ZoneId zoneId = this.clock.getZone();
		LocalDate monday = LocalDate.of(2026, 9, 7);

		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().stream().findFirst().orElseThrow();
		Vet james = this.vetRepository.findById(1).orElseThrow();

		// Seed a confirmed appointment on Monday 09:00 (30 min)
		Appointment appointment = new Appointment();
		appointment.setPet(pet);
		appointment.setVet(james);
		appointment.setStartTime(monday.atTime(9, 0).atZone(zoneId));
		appointment.setDuration(30);
		appointment.setStatus(AppointmentStatus.CONFIRMED);
		appointment.setReason("Annual Checkup");
		this.appointmentRepository.save(appointment);

		// Seed an active hold on Monday 09:30 (30 min)
		SchedulingRequest request = this.lifecycleService.createRequest(owner, pet, "Routine check", "Monday morning",
				"george");
		request.setHeldVet(james);
		request.setHeldStart(monday.atTime(9, 30).atZone(zoneId));
		request.setHeldDuration(30);
		request.setState(RequestState.SUGGESTION_OFFERED);
		this.requestRepository.save(request);

		MvcResult result = this.mockMvc.perform(get("/staff/calendar?date=2026-09-07").session(staffSession))
			.andExpect(status().isOk())
			.andReturn();

		String html = result.getResponse().getContentAsString();

		// Check vet columns
		assertThat(html).contains("James Carter", "Helen Leary", "Linda Douglas", "Rafael Ortega", "Henry Stevens",
				"Sharon Jenkins");

		// Check 15-minute grid rows
		assertThat(html).contains("08:00", "08:15", "09:00", "09:15", "09:30", "09:45", "10:00", "17:00", "18:45");

		// Check all 5 cell states classes exist
		assertThat(html).contains("class=\"closed\"", "class=\"off-shift\"", "class=\"free\"", "class=\"booked\"",
				"class=\"held\"");

		// Check navigation links (prev, next, today)
		assertThat(html).contains("href=\"/staff/calendar?date=2026-09-06\"",
				"href=\"/staff/calendar?date=2026-09-08\"");

		// Check date picker bounded by horizon
		assertThat(html).contains("type=" + "\"date\"", "name=\"date\"", "min=\"" + LocalDate.now(this.clock) + "\"");
	}

	@Test
	@Tag("AC-98")
	void eachVetDayShowsAllFiveCapacityLayers_AC98() throws Exception {
		MockHttpSession staffSession = loginStaff();
		ZoneId zoneId = this.clock.getZone();
		LocalDate monday = LocalDate.of(2026, 9, 7);

		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().stream().findFirst().orElseThrow();
		Vet james = this.vetRepository.findById(1).orElseThrow();

		// Booked appointment for James Carter: 30 minutes
		Appointment appointment = new Appointment();
		appointment.setPet(pet);
		appointment.setVet(james);
		appointment.setStartTime(monday.atTime(9, 0).atZone(zoneId));
		appointment.setDuration(30);
		appointment.setStatus(AppointmentStatus.CONFIRMED);
		appointment.setReason("Dental cleaning");
		this.appointmentRepository.save(appointment);

		// Hold for James Carter: 45 minutes
		SchedulingRequest request = this.lifecycleService.createRequest(owner, pet, "Ear check", "Monday morning",
				"george");
		request.setHeldVet(james);
		request.setHeldStart(monday.atTime(10, 0).atZone(zoneId));
		request.setHeldDuration(45);
		request.setState(RequestState.SUGGESTION_OFFERED);
		this.requestRepository.save(request);

		MvcResult result = this.mockMvc.perform(get("/staff/calendar?date=2026-09-07").session(staffSession))
			.andExpect(status().isOk())
			.andReturn();

		String html = result.getResponse().getContentAsString();

		// Layer 1: Opening hours summary
		assertThat(html).contains("09:00 - 17:00");

		// Layer 2: Effective working blocks for James Carter (Mon 09:00-17:00)
		assertThat(html).contains("class=\"layer-working\"");

		// Layer 3: Booked count/minutes (1 (30m))
		assertThat(html).contains("1 (30m)");

		// Layer 4: Held count/minutes (1 (45m))
		assertThat(html).contains("1 (45m)");

		// Layer 5: Free capacity (405m = 480m total - 30m booked - 45m held)
		assertThat(html).contains("405m");
	}

	private MockHttpSession loginStaff() throws Exception {
		MvcResult login = this.mockMvc.perform(formLogin().user("staff").password("staff123"))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		return (MockHttpSession) login.getRequest().getSession(false);
	}

}
