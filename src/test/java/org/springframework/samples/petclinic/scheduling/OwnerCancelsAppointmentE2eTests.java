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
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentChange;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentChangeRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentLifecycleService;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
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
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class OwnerCancelsAppointmentE2eTests {

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
	private AppointmentChangeRepository changeRepository;

	@Autowired
	private SchedulingRequestRepository requestRepository;

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
	@Tag("AC-138")
	@Tag("AC-116")
	void ownerCancelsUpcomingSteps1And2() throws Exception {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPet(1);
		Vet vet = this.vetRepository.findById(1).orElseThrow();
		ZonedDateTime futureStart = ZonedDateTime.now(this.clock).plusDays(3);

		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(owner);
		request.setPet(pet);
		request.setState(RequestState.ACCEPTED);
		request.setActivePetId(null);
		request.setReasonText("Routine veterinary checkup");
		request.setAvailabilityText("Any morning");
		request.setCreatedAt(ZonedDateTime.now(this.clock));
		request.setUpdatedAt(ZonedDateTime.now(this.clock));
		SchedulingRequest savedRequest = this.requestRepository.save(request);

		Appointment appointment = this.appointmentLifecycleService.bookAppointment(pet, vet, futureStart, 30,
				"Routine veterinary checkup", savedRequest, "staff");
		int appointmentId = appointment.getId();

		// UC-6 Step 1: Owner opens My appointments and views appointment
		MockHttpSession session = loginUser("george", "george123");

		MvcResult listResult = this.mockMvc.perform(get("/my/appointments").session(session))
			.andExpect(status().isOk())
			.andReturn();
		String listHtml = listResult.getResponse().getContentAsString();
		assertThat(listHtml).contains(pet.getName());
		assertThat(listHtml).contains("/my/appointments/" + appointmentId);

		// Owner opens detail page
		MvcResult detailResult = this.mockMvc.perform(get("/my/appointments/" + appointmentId).session(session))
			.andExpect(status().isOk())
			.andReturn();
		String detailHtml = detailResult.getResponse().getContentAsString();
		assertThat(detailHtml).contains(pet.getName());
		assertThat(detailHtml).contains(vet.getLastName());
		assertThat(detailHtml).contains("CONFIRMED");
		assertThat(detailHtml).contains("/my/appointments/" + appointmentId + "/cancel");

		// UC-6 Step 2: Owner cancels the appointment
		this.mockMvc.perform(post("/my/appointments/" + appointmentId + "/cancel").session(session).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", endsWith("/my/appointments")))
			.andExpect(flash().attribute("appointmentCancelled", true));

		// Verify database state: status is CANCELLED_BY_OWNER
		Appointment cancelled = this.appointmentRepository.findById(appointmentId).orElseThrow();
		assertThat(cancelled.getStatus()).isEqualTo(AppointmentStatus.CANCELLED_BY_OWNER);

		// Associated request remains closed in ACCEPTED state
		SchedulingRequest reloadedRequest = this.requestRepository.findById(savedRequest.getId()).orElseThrow();
		assertThat(reloadedRequest.getState()).isEqualTo(RequestState.ACCEPTED);
		assertThat(reloadedRequest.getActivePetId()).isNull();

		// Audit change record persists
		List<AppointmentChange> changes = this.changeRepository.findByAppointmentIdOrderByTimestampAsc(appointmentId);
		assertThat(changes).filteredOn(c -> "CANCEL_BY_OWNER".equals(c.getAction())).hasSize(1);
		assertThat(changes.getLast().getActor()).isEqualTo("george");

		// Follow redirect to My appointments: rendered under past items
		MvcResult afterCancelResult = this.mockMvc.perform(get("/my/appointments").session(session))
			.andExpect(status().isOk())
			.andReturn();
		String afterCancelHtml = afterCancelResult.getResponse().getContentAsString();
		assertThat(afterCancelHtml).contains("CANCELLED_BY_OWNER");
	}

	@Test
	@Tag("AC-117")
	void pastAndNoShowRefusedExtension() throws Exception {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPet(1);
		Vet vet = this.vetRepository.findById(1).orElseThrow();

		ZonedDateTime pastStart = ZonedDateTime.now(this.clock).minusDays(2);
		Appointment pastAppt = this.appointmentLifecycleService.bookAppointment(pet, vet, pastStart, 30, "Past visit",
				null, "staff");

		ZonedDateTime futureStart = ZonedDateTime.now(this.clock).plusDays(2);
		Appointment completedAppt = this.appointmentLifecycleService.bookAppointment(pet, vet, futureStart, 30,
				"Completed appointment", null, "staff");
		completedAppt.setStatus(AppointmentStatus.COMPLETED);
		this.appointmentRepository.save(completedAppt);

		Appointment noShowAppt = this.appointmentLifecycleService.bookAppointment(pet, vet, futureStart, 30,
				"No-show appointment", null, "staff");
		noShowAppt.setStatus(AppointmentStatus.NO_SHOW);
		this.appointmentRepository.save(noShowAppt);

		Map<String, Integer> beforeCounts = captureTableCounts();
		MockHttpSession session = loginUser("george", "george123");

		// Cancel past appointment -> refused
		this.mockMvc.perform(post("/my/appointments/" + pastAppt.getId() + "/cancel").session(session).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", endsWith("/my/appointments/" + pastAppt.getId())))
			.andExpect(flash().attribute("appointmentActionNotAllowed", true));

		// Cancel completed appointment -> refused
		this.mockMvc
			.perform(post("/my/appointments/" + completedAppt.getId() + "/cancel").session(session).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", endsWith("/my/appointments/" + completedAppt.getId())))
			.andExpect(flash().attribute("appointmentActionNotAllowed", true));

		// Cancel no-show appointment -> refused
		this.mockMvc.perform(post("/my/appointments/" + noShowAppt.getId() + "/cancel").session(session).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", endsWith("/my/appointments/" + noShowAppt.getId())))
			.andExpect(flash().attribute("appointmentActionNotAllowed", true));

		Map<String, Integer> afterCounts = captureTableCounts();
		assertThat(afterCounts).as("Disallowed cancellation attempts must produce no database mutation")
			.isEqualTo(beforeCounts);
	}

	@Test
	@Tag("AC-118")
	void otherOwnerCancelIsIndistinguishableExtension() throws Exception {
		Owner george = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = george.getPet(1);
		Vet vet = this.vetRepository.findById(1).orElseThrow();
		ZonedDateTime futureStart = ZonedDateTime.now(this.clock).plusDays(5);

		Appointment appointment = this.appointmentLifecycleService.bookAppointment(pet, vet, futureStart, 30,
				"Confidential owner medical notes", null, "staff");
		int georgeApptId = appointment.getId();
		int missingApptId = 999999;

		Map<String, Integer> beforeCounts = captureTableCounts();

		MockHttpSession bettySession = loginUser("betty", "betty123");
		MockHttpSession georgeSession = loginUser("george", "george123");

		// Detail GET
		MvcResult otherOwnerGet = this.mockMvc.perform(get("/my/appointments/" + georgeApptId).session(bettySession))
			.andExpect(status().isNotFound())
			.andReturn();
		MvcResult missingGet = this.mockMvc.perform(get("/my/appointments/" + missingApptId).session(georgeSession))
			.andExpect(status().isNotFound())
			.andReturn();

		assertThat(otherOwnerGet.getResponse().getStatus()).isEqualTo(missingGet.getResponse().getStatus());
		assertThat(otherOwnerGet.getResponse().getContentAsString()).doesNotContain(pet.getName(),
				"Confidential owner medical notes", vet.getLastName());

		// Cancel POST
		MvcResult otherOwnerPost = this.mockMvc
			.perform(post("/my/appointments/" + georgeApptId + "/cancel").session(bettySession).with(csrf()))
			.andExpect(status().isNotFound())
			.andReturn();
		MvcResult missingPost = this.mockMvc
			.perform(post("/my/appointments/" + missingApptId + "/cancel").session(georgeSession).with(csrf()))
			.andExpect(status().isNotFound())
			.andReturn();

		assertThat(otherOwnerPost.getResponse().getStatus()).isEqualTo(missingPost.getResponse().getStatus());

		Map<String, Integer> afterCounts = captureTableCounts();
		assertThat(afterCounts).as("Other-owner and missing id requests must produce zero mutations")
			.isEqualTo(beforeCounts);
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
