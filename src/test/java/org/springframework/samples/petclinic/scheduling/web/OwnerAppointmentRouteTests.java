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

package org.springframework.samples.petclinic.scheduling.web;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentLifecycleService;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@Transactional
class OwnerAppointmentRouteTests {

	private static final List<String> MUTABLE_TABLES = List.of("owners", "pets", "visits", "scheduling_request",
			"scheduling_request_event", "interpretation", "interpretation_window", "appointment", "appointment_change");

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private VetRepository vetRepository;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private AppointmentLifecycleService appointmentLifecycleService;

	@Autowired
	private Clock clock;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
	}

	@Test
	@Tag("AC-116")
	void detailAndCancelResolveForOwner() throws Exception {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPet(1);
		Vet vet = this.vetRepository.findById(1).orElseThrow();
		ZonedDateTime futureStart = ZonedDateTime.now(this.clock).plusDays(2);

		Appointment appointment = this.appointmentLifecycleService.bookAppointment(pet, vet, futureStart, 30,
				"Routine checkup", null, "staff");

		MockHttpSession ownerSession = loginUser("george", "george123");

		// Detail resolves with 200 OK
		this.mockMvc.perform(get("/my/appointments/" + appointment.getId()).session(ownerSession))
			.andExpect(status().isOk())
			.andExpect(view().name("my/appointmentDetail"));

		// Cancel without CSRF is rejected with 403 Forbidden
		this.mockMvc.perform(post("/my/appointments/" + appointment.getId() + "/cancel").session(ownerSession))
			.andExpect(status().isForbidden());

		// Cancel with CSRF succeeds with 302 redirect
		this.mockMvc
			.perform(post("/my/appointments/" + appointment.getId() + "/cancel").session(ownerSession).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", endsWith("/my/appointments")));

		Appointment cancelled = this.appointmentRepository.findById(appointment.getId()).orElseThrow();
		assertThat(cancelled.getStatus()).isEqualTo(AppointmentStatus.CANCELLED_BY_OWNER);
	}

	@Test
	@Tag("AC-118")
	void anonymousWrongRoleAndOtherOwnerDeniedWithoutDisclosureOrMutation_AC118() throws Exception {
		Owner george = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = george.getPet(1);
		Vet vet = this.vetRepository.findById(1).orElseThrow();
		ZonedDateTime futureStart = ZonedDateTime.now(this.clock).plusDays(3);

		Appointment appointment = this.appointmentLifecycleService.bookAppointment(pet, vet, futureStart, 30,
				"George confidential reason", null, "staff");
		int ownedApptId = appointment.getId();
		int missingApptId = 999999;

		Map<String, Integer> beforeCounts = captureTableCounts();

		// Anonymous requests must redirect to /login
		this.mockMvc.perform(get("/my/appointments/" + ownedApptId))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", endsWith("/login")));
		this.mockMvc.perform(post("/my/appointments/" + ownedApptId + "/cancel").with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", endsWith("/login")));

		// Staff (wrong role) must be forbidden (403)
		MockHttpSession staffSession = loginUser("staff", "staff123");
		this.mockMvc.perform(get("/my/appointments/" + ownedApptId).session(staffSession))
			.andExpect(status().isForbidden());
		this.mockMvc.perform(post("/my/appointments/" + ownedApptId + "/cancel").session(staffSession).with(csrf()))
			.andExpect(status().isForbidden());

		// Other owner (betty) must receive 404 identical to missing id
		MockHttpSession otherOwnerSession = loginUser("betty", "betty123");
		MockHttpSession georgeSession = loginUser("george", "george123");

		MvcResult otherOwnerGet = this.mockMvc
			.perform(get("/my/appointments/" + ownedApptId).session(otherOwnerSession))
			.andExpect(status().isNotFound())
			.andReturn();
		MvcResult missingGet = this.mockMvc.perform(get("/my/appointments/" + missingApptId).session(georgeSession))
			.andExpect(status().isNotFound())
			.andReturn();

		assertThat(otherOwnerGet.getResponse().getStatus()).isEqualTo(missingGet.getResponse().getStatus());
		assertThat(otherOwnerGet.getResponse().getContentAsString()).doesNotContain(pet.getName(),
				"George confidential reason", vet.getLastName());

		MvcResult otherOwnerPost = this.mockMvc
			.perform(post("/my/appointments/" + ownedApptId + "/cancel").session(otherOwnerSession).with(csrf()))
			.andExpect(status().isNotFound())
			.andReturn();
		MvcResult missingPost = this.mockMvc
			.perform(post("/my/appointments/" + missingApptId + "/cancel").session(georgeSession).with(csrf()))
			.andExpect(status().isNotFound())
			.andReturn();

		assertThat(otherOwnerPost.getResponse().getStatus()).isEqualTo(missingPost.getResponse().getStatus());

		Map<String, Integer> afterCounts = captureTableCounts();
		assertThat(afterCounts).as("Denials must produce no mutations").isEqualTo(beforeCounts);
	}

	private MockHttpSession loginUser(String username, String password) throws Exception {
		MvcResult login = this.mockMvc.perform(formLogin().user(username).password(password))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		return (MockHttpSession) login.getRequest().getSession(false);
	}

	private Map<String, Integer> captureTableCounts() {
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (String table : MUTABLE_TABLES) {
			Integer count = this.jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
			counts.put(table, count != null ? count : 0);
		}
		return counts;
	}

}
