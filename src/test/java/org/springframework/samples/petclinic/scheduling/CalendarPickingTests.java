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
import java.time.LocalTime;
import java.time.ZoneId;
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
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.interpretation.Interpretation;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationRepository;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationWindow;
import org.springframework.samples.petclinic.scheduling.interpretation.Provenance;
import org.springframework.samples.petclinic.scheduling.interpretation.WindowKind;
import org.springframework.samples.petclinic.scheduling.request.RequestLifecycleService;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEvent;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEventRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.StaffSuggestionService;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class CalendarPickingTests {

	private static final List<String> MUTABLE_TABLES = List.of("scheduling_request", "scheduling_request_event",
			"interpretation", "interpretation_window", "appointment", "appointment_change");

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
	private SchedulingRequestEventRepository eventRepository;

	@Autowired
	private InterpretationRepository interpretationRepository;

	@Autowired
	private RequestLifecycleService lifecycleService;

	@Autowired
	private StaffSuggestionService staffSuggestionService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
	}

	@Test
	@Tag("AC-90")
	void pickingModeRendersFreeCellsAsPostForms_AC90() throws Exception {
		MockHttpSession staffSession = loginStaff();
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().stream().findFirst().orElseThrow();

		SchedulingRequest request = this.lifecycleService.createRequest(owner, pet, "General checkup", "Monday",
				"george");
		request = this.lifecycleService.declineConsent(request, "george");
		assertThat(request.getState()).isEqualTo(RequestState.WITH_STAFF);

		MvcResult result = this.mockMvc
			.perform(get("/staff/calendar/pick/" + request.getId() + "?date=2026-09-07").session(staffSession))
			.andExpect(status().isOk())
			.andReturn();

		String html = result.getResponse().getContentAsString();

		// Picking forms are rendered for free cells
		assertThat(html).contains("action=\"/staff/calendar/pick/" + request.getId() + "\"");
		assertThat(html).contains("type=\"hidden\" name=\"vetId\"");
		assertThat(html).contains("type=\"hidden\" name=\"appointmentDate\"");
		assertThat(html).contains("type=\"hidden\" name=\"startTime\"");
	}

	@Test
	@Tag("AC-90")
	void pickedFreeCellAndSuggestButtonCreateIdenticalHold_AC90() throws Exception {
		MockHttpSession staffSession = loginStaff();
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().stream().findFirst().orElseThrow();
		ZoneId zoneId = this.clock.getZone();
		LocalDate monday = LocalDate.of(2026, 9, 7);

		// Fixture 1: Pick via POST /staff/calendar/pick/{id}
		SchedulingRequest request1 = this.lifecycleService.createRequest(owner, pet, "Limping left leg",
				"Monday morning", "george");
		request1 = this.lifecycleService.declineConsent(request1, "george");

		this.mockMvc
			.perform(post("/staff/calendar/pick/" + request1.getId()).session(staffSession)
				.with(csrf())
				.param("vetId", "1")
				.param("appointmentDate", "2026-09-07")
				.param("startTime", "09:00")
				.param("durationMinutes", "30"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/requests/" + request1.getId()));

		SchedulingRequest reloaded1 = this.requestRepository.findById(request1.getId()).orElseThrow();
		assertThat(reloaded1.getState()).isEqualTo(RequestState.SUGGESTION_OFFERED);
		assertThat(reloaded1.getHeldVet().getId()).isEqualTo(1);
		assertThat(reloaded1.getHeldStart()).isEqualTo(monday.atTime(9, 0).atZone(zoneId));
		assertThat(reloaded1.getHeldDuration()).isEqualTo(30);

		Integer expectedVetId = reloaded1.getHeldVet().getId();
		ZonedDateTime expectedStart = reloaded1.getHeldStart();
		Integer expectedDuration = reloaded1.getHeldDuration();
		RequestState expectedState = reloaded1.getState();

		List<SchedulingRequestEvent> events1 = this.eventRepository
			.findByRequestIdOrderByTimestampAsc(request1.getId());
		SchedulingRequestEvent lastEvent1 = events1.get(events1.size() - 1);
		assertThat(lastEvent1.getAction()).isEqualTo("staff place suggestion");
		assertThat(lastEvent1.getActor()).isEqualTo("staff");

		// Clear hold on request 1 so the 09:00 slot becomes available again
		request1.clearHold();
		this.requestRepository.save(request1);

		// Fixture 2: Suggest via POST /staff/requests/{id}/suggest with interpretation
		// matching the same slot
		Owner owner2 = this.ownerRepository.findById(2).orElseThrow();
		Pet pet2 = owner2.getPets().stream().findFirst().orElseThrow();
		SchedulingRequest request2 = this.lifecycleService.createRequest(owner2, pet2, "Limping left leg",
				"Monday morning", "betty");
		request2 = this.lifecycleService.declineConsent(request2, "betty");

		Interpretation interp = new Interpretation();
		interp.setRequest(request2);
		interp.setVersion(1);
		interp.setProvenance(Provenance.STAFF);
		interp.setReasonSummary("Limping left leg");
		interp.setEstimatedMinutes(30);
		interp.setPreferredVet(this.vetRepository.findById(1).orElseThrow());
		interp.setCreatedAt(ZonedDateTime.now(this.clock));
		InterpretationWindow window = new InterpretationWindow();
		window.setInterpretation(interp);
		window.setKind(WindowKind.PREFERRED);
		window.setDateVal(monday);
		window.setStartTime(LocalTime.of(9, 0));
		window.setEndTime(LocalTime.of(12, 0));
		interp.getWindows().add(window);
		this.interpretationRepository.save(interp);

		this.mockMvc
			.perform(post("/staff/requests/" + request2.getId() + "/suggest").session(staffSession).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/requests/" + request2.getId()));

		SchedulingRequest reloaded2 = this.requestRepository.findById(request2.getId()).orElseThrow();
		assertThat(reloaded2.getState()).isEqualTo(expectedState);
		assertThat(reloaded2.getHeldVet().getId()).isEqualTo(expectedVetId);
		assertThat(reloaded2.getHeldStart()).isEqualTo(expectedStart);
		assertThat(reloaded2.getHeldDuration()).isEqualTo(expectedDuration);
	}

	@Test
	@Tag("AC-90")
	void pickedNonFreeCellIsRefusedWithoutMutation_AC90() throws Exception {
		MockHttpSession staffSession = loginStaff();
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().stream().findFirst().orElseThrow();
		Vet james = this.vetRepository.findById(1).orElseThrow();
		ZoneId zoneId = this.clock.getZone();
		LocalDate monday = LocalDate.of(2026, 9, 7);

		// Seed a confirmed appointment on Monday 09:00 (30 min)
		Appointment appointment = new Appointment();
		appointment.setPet(pet);
		appointment.setVet(james);
		appointment.setStartTime(monday.atTime(9, 0).atZone(zoneId));
		appointment.setDuration(30);
		appointment.setStatus(AppointmentStatus.CONFIRMED);
		appointment.setReason("Existing appointment");
		this.appointmentRepository.save(appointment);

		SchedulingRequest request = this.lifecycleService.createRequest(owner, pet, "Routine check", "Monday",
				"george");
		request = this.lifecycleService.declineConsent(request, "george");

		Map<String, Integer> beforeCounts = captureTableCounts();

		// Attempt to pick a booked slot (09:00)
		this.mockMvc
			.perform(post("/staff/calendar/pick/" + request.getId()).session(staffSession)
				.with(csrf())
				.param("vetId", "1")
				.param("appointmentDate", "2026-09-07")
				.param("startTime", "09:00")
				.param("durationMinutes", "30"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/calendar/pick/" + request.getId() + "?date=2026-09-07"));

		Map<String, Integer> afterCounts = captureTableCounts();
		assertThat(afterCounts).as("Refused pick produces zero database mutation").isEqualTo(beforeCounts);

		SchedulingRequest unchanged = this.requestRepository.findById(request.getId()).orElseThrow();
		assertThat(unchanged.getState()).isEqualTo(RequestState.WITH_STAFF);
		assertThat(unchanged.hasHold()).isFalse();
	}

	@Test
	@Tag("AC-97")
	void normalModeFreeCellPrefillsBookingForm_AC97() throws Exception {
		MockHttpSession staffSession = loginStaff();

		// GET /staff/calendar normal mode renders links to
		// /staff/appointments/new?vetId=...&start=...
		MvcResult result = this.mockMvc.perform(get("/staff/calendar?date=2026-09-07").session(staffSession))
			.andExpect(status().isOk())
			.andReturn();

		String html = result.getResponse().getContentAsString();
		assertThat(html).contains("href=\"/staff/appointments/new?vetId=1&amp;start=2026-09-07T09:00\"");
	}

	private MockHttpSession loginStaff() throws Exception {
		MvcResult login = this.mockMvc.perform(formLogin().user("staff").password("staff123"))
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
