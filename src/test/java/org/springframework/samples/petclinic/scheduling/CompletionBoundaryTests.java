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
import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentChangeRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentLifecycleService;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.appointment.IllegalAppointmentTransitionException;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TestClockConfig.class)
@Transactional
class CompletionBoundaryTests {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private Clock clock;

	@Autowired
	private AppointmentLifecycleService lifecycleService;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private AppointmentChangeRepository changeRepository;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private VetRepository vetRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private MockMvc mockMvc;

	private Owner owner;

	private Pet pet;

	private Vet vet;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
		this.owner = this.ownerRepository.findById(1).orElseThrow();
		this.pet = this.owner.getPet(1);
		this.vet = this.vetRepository.findById(1).orElseThrow();
	}

	@Test
	@Tag("AC-111")
	void beforeStartRefusedWithoutSideEffect_AC111() {
		Appointment completion = saveConfirmed(now().plusMinutes(1), "future completion", null);
		Appointment noShow = saveConfirmed(now().plusMinutes(1), "future no-show", null);
		long changesBefore = this.changeRepository.count();
		long visitsBefore = visitCount();

		assertThatThrownBy(() -> this.lifecycleService.markCompleted(completion, "staff"))
			.isExactlyInstanceOf(IllegalAppointmentTransitionException.class);
		assertThatThrownBy(() -> this.lifecycleService.markNoShow(noShow, "staff", "did not arrive"))
			.isExactlyInstanceOf(IllegalAppointmentTransitionException.class);

		this.appointmentRepository.flush();
		assertThat(this.appointmentRepository.findById(completion.getId()).orElseThrow().getStatus())
			.isEqualTo(AppointmentStatus.CONFIRMED);
		assertThat(this.appointmentRepository.findById(noShow.getId()).orElseThrow().getStatus())
			.isEqualTo(AppointmentStatus.CONFIRMED);
		assertThat(this.changeRepository.count()).isEqualTo(changesBefore);
		assertThat(visitCount()).isEqualTo(visitsBefore);
	}

	@Test
	@Tag("AC-112")
	void exactlyAtStartAllowed_AC112() {
		Appointment completion = saveConfirmed(now(), "boundary completion", null);
		Appointment noShow = saveConfirmed(now(), "boundary no-show", null);

		this.lifecycleService.markCompleted(completion, "staff");
		this.lifecycleService.markNoShow(noShow, "staff", "did not arrive");

		assertThat(completion.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
		assertThat(noShow.getStatus()).isEqualTo(AppointmentStatus.NO_SHOW);
	}

	@Test
	@Tag("AC-113")
	void afterStartAllowed_AC113() {
		Appointment completion = saveConfirmed(now().minusNanos(1), "past completion", null);
		Appointment noShow = saveConfirmed(now().minusDays(1), "past no-show", null);

		this.lifecycleService.markCompleted(completion, "staff");
		this.lifecycleService.markNoShow(noShow, "staff", "did not arrive");

		assertThat(completion.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
		assertThat(noShow.getStatus()).isEqualTo(AppointmentStatus.NO_SHOW);
	}

	@Test
	@Tag("AC-114")
	void completionCreatesLinkedEditableVisit_AC114() throws Exception {
		SchedulingRequest request = request("owner described limping");
		Appointment requestBacked = saveConfirmed(now().minusHours(2), "staff attached booking note", request);
		Appointment directBooked = saveConfirmed(now().minusHours(1), "staff recorded nail trim", null);
		MockHttpSession staff = loginStaff();

		completeOverHttp(staff, requestBacked);
		completeOverHttp(staff, directBooked);

		VisitRow requestVisit = visitFor(requestBacked);
		VisitRow directVisit = visitFor(directBooked);
		assertThat(requestVisit).isEqualTo(new VisitRow(requestVisit.id(), requestBacked.getId(),
				requestBacked.getStartTime().toLocalDate(), "owner described limping"));
		assertThat(directVisit).isEqualTo(new VisitRow(directVisit.id(), directBooked.getId(),
				directBooked.getStartTime().toLocalDate(), "staff recorded nail trim"));
		assertThat(linkedVisitCount(requestBacked)).isOne();
		assertThat(linkedVisitCount(directBooked)).isOne();

		assertVisitEditValues(staff, requestVisit, "owner described limping");
		assertVisitEditValues(staff, directVisit, "staff recorded nail trim");

		this.mockMvc
			.perform(post("/staff/visits/{visitId}/edit", requestVisit.id()).session(staff)
				.with(csrf())
				.param("description", "owner described limping; medication prescribed"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/calendar"))
			.andExpect(flash().attribute("message", "visitUpdated"));
		assertThat(visitFor(requestBacked).description()).isEqualTo("owner described limping; medication prescribed");
	}

	private void completeOverHttp(MockHttpSession staff, Appointment appointment) throws Exception {
		this.mockMvc
			.perform(post("/staff/appointments/{appointmentId}/complete", appointment.getId()).session(staff)
				.with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/calendar"))
			.andExpect(flash().attribute("message", "appointmentCompleted"));
	}

	private void assertVisitEditValues(MockHttpSession staff, VisitRow visit, String expectedDescription)
			throws Exception {
		String html = this.mockMvc.perform(get("/staff/visits/{visitId}/edit", visit.id()).session(staff))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(html).contains("name=\"date\" value=\"" + visit.date() + "\"");
		assertThat(html).contains("<textarea name=\"description\" rows=\"4\" class=\"form-control\">"
				+ expectedDescription + "</textarea>");
	}

	private SchedulingRequest request(String ownerReason) {
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(this.owner);
		request.setPet(this.pet);
		request.setState(RequestState.ACCEPTED);
		request.setReasonText(ownerReason);
		request.setAvailabilityText("weekday morning");
		request.setCreatedAt(now());
		request.setUpdatedAt(now());
		return this.requestRepository.saveAndFlush(request);
	}

	private Appointment saveConfirmed(ZonedDateTime start, String reason, SchedulingRequest request) {
		Appointment appointment = new Appointment();
		appointment.setPet(this.pet);
		appointment.setVet(this.vet);
		appointment.setStartTime(start);
		appointment.setDuration(30);
		appointment.setStatus(AppointmentStatus.CONFIRMED);
		appointment.setReason(reason);
		appointment.setRequest(request);
		return this.appointmentRepository.saveAndFlush(appointment);
	}

	private VisitRow visitFor(Appointment appointment) {
		return this.jdbcTemplate.queryForObject(
				"SELECT id, appointment_id, visit_date, description FROM visits WHERE appointment_id = ?",
				(rs, rowNum) -> new VisitRow(rs.getInt("id"), rs.getInt("appointment_id"),
						rs.getDate("visit_date").toLocalDate(), rs.getString("description")),
				appointment.getId());
	}

	private long linkedVisitCount(Appointment appointment) {
		Long count = this.jdbcTemplate.queryForObject("SELECT count(*) FROM visits WHERE appointment_id = ?",
				Long.class, appointment.getId());
		return count != null ? count : 0;
	}

	private long visitCount() {
		Long count = this.jdbcTemplate.queryForObject("SELECT count(*) FROM visits", Long.class);
		return count != null ? count : 0;
	}

	private ZonedDateTime now() {
		return ZonedDateTime.now(this.clock);
	}

	private MockHttpSession loginStaff() throws Exception {
		MvcResult result = this.mockMvc.perform(formLogin().user("staff").password("staff123"))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private record VisitRow(Integer id, Integer appointmentId, LocalDate date, String description) {
	}

}
