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
import org.springframework.samples.petclinic.scheduling.appointment.IllegalAppointmentTransitionException;
import org.springframework.samples.petclinic.scheduling.appointment.OwnerCancelService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
class OwnerCancelTests {

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
	private AppointmentChangeRepository appointmentChangeRepository;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private AppointmentLifecycleService appointmentLifecycleService;

	@Autowired
	private OwnerCancelService ownerCancelService;

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
	@Tag("AC-95")
	void directBookedAppointmentVisibleAndCancellable_AC95() throws Exception {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPet(1);
		Vet vet = this.vetRepository.findById(1).orElseThrow();
		ZonedDateTime futureStart = ZonedDateTime.now(this.clock).plusDays(5);

		Appointment appointment = this.appointmentLifecycleService.bookAppointment(pet, vet, futureStart, 30,
				"Direct booking without prior request", null, "staff");

		MockHttpSession session = loginUser("george", "george123");

		// Renders in upcoming appointments
		MvcResult getResult = this.mockMvc.perform(get("/my/appointments").session(session))
			.andExpect(status().isOk())
			.andReturn();
		String content = getResult.getResponse().getContentAsString();
		assertThat(content).contains(pet.getName());
		assertThat(content).contains("/my/appointments/" + appointment.getId() + "/cancel");

		// Cancel via POST
		this.mockMvc.perform(post("/my/appointments/" + appointment.getId() + "/cancel").session(session).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", endsWith("/my/appointments")))
			.andExpect(flash().attribute("appointmentCancelled", true));

		Appointment cancelled = this.appointmentRepository.findById(appointment.getId()).orElseThrow();
		assertThat(cancelled.getStatus()).isEqualTo(AppointmentStatus.CANCELLED_BY_OWNER);

		// Now rendered under past appointments
		MvcResult getAfterResult = this.mockMvc.perform(get("/my/appointments").session(session))
			.andExpect(status().isOk())
			.andReturn();
		String contentAfter = getAfterResult.getResponse().getContentAsString();
		assertThat(contentAfter).contains("CANCELLED_BY_OWNER");
	}

	@Test
	@Tag("AC-116")
	void upcomingOwnedAppointmentCancelsAndRequestStaysClosed_AC116() throws Exception {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPet(1);
		Vet vet = this.vetRepository.findById(1).orElseThrow();
		ZonedDateTime futureStart = ZonedDateTime.now(this.clock).plusDays(2);

		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(owner);
		request.setPet(pet);
		request.setState(RequestState.ACCEPTED);
		request.setActivePetId(null);
		request.setReasonText("Follow up checkup");
		request.setAvailabilityText("Any weekday morning");
		request.setCreatedAt(ZonedDateTime.now(this.clock));
		request.setUpdatedAt(ZonedDateTime.now(this.clock));
		SchedulingRequest savedRequest = this.requestRepository.save(request);

		Appointment appointment = this.appointmentLifecycleService.bookAppointment(pet, vet, futureStart, 30,
				"Accepted suggestion booking", savedRequest, "staff");

		Appointment cancelled = this.ownerCancelService.cancel(owner.getId(), appointment.getId(), "george");

		assertThat(cancelled.getStatus()).isEqualTo(AppointmentStatus.CANCELLED_BY_OWNER);

		// Request remains closed in ACCEPTED state
		SchedulingRequest reloadedRequest = this.requestRepository.findById(savedRequest.getId()).orElseThrow();
		assertThat(reloadedRequest.getState()).isEqualTo(RequestState.ACCEPTED);
		assertThat(reloadedRequest.getActivePetId()).isNull();

		// Audit change row exists
		List<AppointmentChange> changes = this.appointmentChangeRepository
			.findByAppointmentIdOrderByTimestampAsc(appointment.getId());
		assertThat(changes).filteredOn(c -> "CANCEL_BY_OWNER".equals(c.getAction())).hasSize(1);
	}

	@Test
	@Tag("AC-117")
	void pastCompletedAndNoShowRefusedWithoutSideEffect_AC117() throws Exception {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPet(1);
		Vet vet = this.vetRepository.findById(1).orElseThrow();

		ZonedDateTime pastStart = ZonedDateTime.now(this.clock).minusDays(1);
		Appointment pastAppt = this.appointmentLifecycleService.bookAppointment(pet, vet, pastStart, 30,
				"Past appointment", null, "staff");

		ZonedDateTime futureStart = ZonedDateTime.now(this.clock).plusDays(1);
		Appointment completedAppt = this.appointmentLifecycleService.bookAppointment(pet, vet, futureStart, 30,
				"Completed appointment", null, "staff");
		completedAppt.setStatus(AppointmentStatus.COMPLETED);
		this.appointmentRepository.save(completedAppt);

		Appointment noShowAppt = this.appointmentLifecycleService.bookAppointment(pet, vet, futureStart, 30,
				"No-show appointment", null, "staff");
		noShowAppt.setStatus(AppointmentStatus.NO_SHOW);
		this.appointmentRepository.save(noShowAppt);

		Map<String, Integer> beforeCounts = captureTableCounts();

		// Service level: all throw IllegalAppointmentTransitionException
		assertThatThrownBy(() -> this.ownerCancelService.cancel(owner.getId(), pastAppt.getId(), "george"))
			.isInstanceOf(IllegalAppointmentTransitionException.class);
		assertThatThrownBy(() -> this.ownerCancelService.cancel(owner.getId(), completedAppt.getId(), "george"))
			.isInstanceOf(IllegalAppointmentTransitionException.class);
		assertThatThrownBy(() -> this.ownerCancelService.cancel(owner.getId(), noShowAppt.getId(), "george"))
			.isInstanceOf(IllegalAppointmentTransitionException.class);

		// HTTP level: all refused with flash attribute and redirect to detail
		MockHttpSession session = loginUser("george", "george123");
		this.mockMvc.perform(post("/my/appointments/" + pastAppt.getId() + "/cancel").session(session).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", endsWith("/my/appointments/" + pastAppt.getId())))
			.andExpect(flash().attribute("appointmentActionNotAllowed", true));

		this.mockMvc
			.perform(post("/my/appointments/" + completedAppt.getId() + "/cancel").session(session).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", endsWith("/my/appointments/" + completedAppt.getId())))
			.andExpect(flash().attribute("appointmentActionNotAllowed", true));

		this.mockMvc.perform(post("/my/appointments/" + noShowAppt.getId() + "/cancel").session(session).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", endsWith("/my/appointments/" + noShowAppt.getId())))
			.andExpect(flash().attribute("appointmentActionNotAllowed", true));

		Map<String, Integer> afterCounts = captureTableCounts();
		assertThat(afterCounts).as("Disallowed cancellation attempts must produce no mutations")
			.isEqualTo(beforeCounts);
	}

	@Test
	@Tag("AC-118")
	void otherOwnerAndMissingViewCancelAreIdentical_AC118() throws Exception {
		Owner george = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = george.getPet(1);
		Vet vet = this.vetRepository.findById(1).orElseThrow();
		ZonedDateTime futureStart = ZonedDateTime.now(this.clock).plusDays(4);

		Appointment appointment = this.appointmentLifecycleService.bookAppointment(pet, vet, futureStart, 30,
				"George confidential note", null, "staff");
		int georgeApptId = appointment.getId();
		int missingApptId = 999999;

		Map<String, Integer> beforeCounts = captureTableCounts();

		MockHttpSession bettySession = loginUser("betty", "betty123");
		MockHttpSession georgeSession = loginUser("george", "george123");

		// GET detail: other-owner vs missing id return identical 404
		MvcResult otherOwnerGet = this.mockMvc.perform(get("/my/appointments/" + georgeApptId).session(bettySession))
			.andExpect(status().isNotFound())
			.andReturn();
		MvcResult missingGet = this.mockMvc.perform(get("/my/appointments/" + missingApptId).session(georgeSession))
			.andExpect(status().isNotFound())
			.andReturn();

		assertThat(otherOwnerGet.getResponse().getStatus()).isEqualTo(missingGet.getResponse().getStatus());
		assertThat(otherOwnerGet.getResponse().getContentAsString()).doesNotContain(pet.getName(),
				"George confidential note", vet.getLastName());

		// POST cancel: other-owner vs missing id return identical 404
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
		assertThat(afterCounts).as("Zero mutations on other-owner and missing id requests").isEqualTo(beforeCounts);
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
