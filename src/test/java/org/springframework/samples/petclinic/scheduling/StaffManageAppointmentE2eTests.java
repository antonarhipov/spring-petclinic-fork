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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentChange;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentChangeRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.clinic.AvailabilityConflictService;
import org.springframework.samples.petclinic.scheduling.clinic.VetException;
import org.springframework.samples.petclinic.scheduling.clinic.VetExceptionRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEvent;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEventRepository;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(StaffManageAppointmentE2eTests.MutableClockConfiguration.class)
@Transactional
class StaffManageAppointmentE2eTests {

	private static final ZoneId ZONE = ZoneId.of("Europe/Amsterdam");

	private static final ZonedDateTime START_NOW = ZonedDateTime.of(2026, 9, 7, 9, 0, 0, 0, ZONE);

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private MutableClock clock;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

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
	private SchedulingRequestEventRepository eventRepository;

	@Autowired
	private VetExceptionRepository exceptionRepository;

	private MockMvc mockMvc;

	private Owner owner;

	private Pet pet;

	private Vet vet;

	private MockHttpSession staff;

	@BeforeEach
	void setUp() throws Exception {
		this.clock.set(START_NOW);
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
		this.owner = this.ownerRepository.findById(1).orElseThrow();
		this.pet = this.owner.getPet(1);
		this.vet = this.vetRepository.findById(1).orElseThrow();
		this.staff = loginStaff();
	}

	@Test
	@Tag("AC-138")
	void staffManageAppointmentSteps1Through4() throws Exception {
		LocalDate appointmentDate = LocalDate.of(2026, 9, 8);
		Appointment managed = saveConfirmed(appointmentDate, LocalTime.of(10, 0), "owner requested wellness exam");
		Appointment cancelled = saveConfirmed(appointmentDate, LocalTime.of(11, 0), "owner requested vaccination");
		Appointment noShow = saveConfirmed(appointmentDate, LocalTime.of(12, 0), "owner reported skin irritation");

		String calendar = this.mockMvc
			.perform(get("/staff/calendar").param("date", appointmentDate.toString()).session(this.staff))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(calendar).contains("owner requested wellness exam", "owner requested vaccination",
				"owner reported skin irritation");
		assertThat(statusOf(managed)).isEqualTo(AppointmentStatus.CONFIRMED);

		ZonedDateTime originalStart = managed.getStartTime();
		this.mockMvc
			.perform(post("/staff/appointments/{appointmentId}/reschedule", managed.getId()).session(this.staff)
				.with(csrf())
				.param("vetId", this.vet.getId().toString())
				.param("appointmentDate", appointmentDate.toString())
				.param("startTime", "10:30")
				.param("durationMinutes", "45")
				.param("reason", "clinic moved the appointment"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/calendar"))
			.andExpect(flash().attribute("message", "appointmentRescheduled"));
		Appointment rescheduled = this.appointmentRepository.findById(managed.getId()).orElseThrow();
		assertThat(rescheduled.getStartTime()).isEqualTo(appointmentDate.atTime(10, 30).atZone(ZONE));
		assertThat(rescheduled.getDuration()).isEqualTo(45);
		assertThat(lastChange(managed))
			.extracting(AppointmentChange::getAction, AppointmentChange::getReason,
					AppointmentChange::getOriginalStartTime)
			.containsExactly("RESCHEDULE", "clinic moved the appointment", originalStart);

		this.mockMvc
			.perform(post("/staff/appointments/{appointmentId}/cancel", cancelled.getId()).session(this.staff)
				.with(csrf())
				.param("reason", "veterinarian unavailable"))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute("message", "appointmentCancelled"));
		assertThat(statusOf(cancelled)).isEqualTo(AppointmentStatus.CANCELLED_BY_STAFF);
		assertThat(lastChange(cancelled)).extracting(AppointmentChange::getAction, AppointmentChange::getReason)
			.containsExactly("CANCEL_BY_STAFF", "veterinarian unavailable");

		this.clock.set(appointmentDate.atTime(13, 0).atZone(ZONE));
		this.mockMvc
			.perform(post("/staff/appointments/{appointmentId}/complete", managed.getId()).session(this.staff)
				.with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute("message", "appointmentCompleted"));
		assertThat(statusOf(managed)).isEqualTo(AppointmentStatus.COMPLETED);
		assertThat(lastChange(managed).getAction()).isEqualTo("MARK_COMPLETED");

		this.mockMvc
			.perform(post("/staff/appointments/{appointmentId}/no-show", noShow.getId()).session(this.staff)
				.with(csrf())
				.param("reason", "owner did not arrive"))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute("message", "appointmentNoShow"));
		assertThat(statusOf(noShow)).isEqualTo(AppointmentStatus.NO_SHOW);
		assertThat(lastChange(noShow)).extracting(AppointmentChange::getAction, AppointmentChange::getReason)
			.containsExactly("MARK_NO_SHOW", "owner did not arrive");

		VisitRow visit = visitFor(managed);
		assertThat(visit)
			.isEqualTo(new VisitRow(visit.id(), managed.getId(), appointmentDate, "owner requested wellness exam"));
		String editPage = this.mockMvc.perform(get("/staff/visits/{visitId}/edit", visit.id()).session(this.staff))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(editPage).contains("<textarea name=\"description\" rows=\"4\" class=\"form-control\">"
				+ "owner requested wellness exam</textarea>");
		this.mockMvc
			.perform(post("/staff/visits/{visitId}/edit", visit.id()).session(this.staff)
				.with(csrf())
				.param("description", "wellness exam completed; follow-up in one year"))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute("message", "visitUpdated"));
		assertThat(visitFor(managed).description()).isEqualTo("wellness exam completed; follow-up in one year");
	}

	@Test
	void confirmedAvailabilityConflictExtension() throws Exception {
		LocalDate conflictDate = LocalDate.of(2026, 9, 9);
		Appointment appointment = saveConfirmed(conflictDate, LocalTime.of(10, 0), "confirmed conflict");
		List<ExceptionRow> exceptionsBefore = exceptions();
		long changesBefore = this.changeRepository.count();
		long eventsBefore = this.eventRepository.count();

		MvcResult result = postUnavailableException(conflictDate).andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/vets/1/availability"))
			.andExpect(flash().attribute("error", "confirmedConflictWarning"))
			.andReturn();

		Object conflicts = result.getFlashMap().get("conflicts");
		assertThat(conflicts).isInstanceOf(List.class);
		assertThat(((List<?>) conflicts).stream()
			.map(AvailabilityConflictService.AppointmentConflict.class::cast)
			.map(AvailabilityConflictService.AppointmentConflict::id)
			.toList()).containsExactly(appointment.getId());
		assertThat(statusOf(appointment)).isEqualTo(AppointmentStatus.CONFIRMED);
		assertThat(exceptions()).isEqualTo(exceptionsBefore);
		assertThat(this.changeRepository.count()).isEqualTo(changesBefore);
		assertThat(this.eventRepository.count()).isEqualTo(eventsBefore);
	}

	@Test
	void holdOnlyConflictExtension() throws Exception {
		LocalDate conflictDate = LocalDate.of(2026, 9, 9);
		SchedulingRequest held = saveHold(conflictDate, LocalTime.of(11, 0));

		postUnavailableException(conflictDate).andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/vets/1/availability"))
			.andExpect(flash().attribute("notice", "holdConflictNotice"))
			.andExpect(flash().attribute("message", "availabilitySaved"));

		this.entityManager.flush();
		this.entityManager.clear();
		SchedulingRequest reloaded = this.requestRepository.findById(held.getId()).orElseThrow();
		assertThat(reloaded.getState()).isEqualTo(RequestState.WITH_STAFF);
		assertThat(reloaded.getHeldVet()).isNull();
		assertThat(reloaded.getHeldStart()).isNull();
		assertThat(reloaded.getHeldDuration()).isNull();
		List<SchedulingRequestEvent> events = this.eventRepository.findByRequestIdOrderByTimestampAsc(held.getId());
		assertThat(events).singleElement().satisfies(event -> {
			assertThat(event.getFromState()).isEqualTo(RequestState.SUGGESTION_OFFERED);
			assertThat(event.getToState()).isEqualTo(RequestState.WITH_STAFF);
			assertThat(event.getActor()).isEqualTo("staff");
			assertThat(event.getAction()).isEqualTo("HOLD_INVALIDATED");
			assertThat(event.getReason()).isEqualTo("availability edit");
			assertThat(event.getTimestamp()).isEqualTo(this.clock.now());
		});
		assertThat(this.exceptionRepository.findByVetId(this.vet.getId())).anySatisfy(exception -> {
			assertThat(exception.getExceptionDate()).isEqualTo(conflictDate);
			assertThat(exception.isUnavailable()).isTrue();
		});
	}

	private org.springframework.test.web.servlet.ResultActions postUnavailableException(LocalDate conflictDate)
			throws Exception {
		return this.mockMvc.perform(post("/staff/vets/{vetId}/availability", this.vet.getId()).session(this.staff)
			.with(csrf())
			.param("newExceptionDate", conflictDate.toString())
			.param("newExceptionUnavailable", "true"));
	}

	private Appointment saveConfirmed(LocalDate date, LocalTime time, String reason) {
		Appointment appointment = new Appointment();
		appointment.setPet(this.pet);
		appointment.setVet(this.vet);
		appointment.setStartTime(date.atTime(time).atZone(ZONE));
		appointment.setDuration(30);
		appointment.setStatus(AppointmentStatus.CONFIRMED);
		appointment.setReason(reason);
		return this.appointmentRepository.saveAndFlush(appointment);
	}

	private SchedulingRequest saveHold(LocalDate date, LocalTime time) {
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(this.owner);
		request.setPet(this.pet);
		request.setState(RequestState.SUGGESTION_OFFERED);
		request.setReasonText("hold conflict");
		request.setAvailabilityText("Wednesday morning");
		request.setCreatedAt(this.clock.now().minusMinutes(5));
		request.setUpdatedAt(this.clock.now().minusMinutes(5));
		request.setHold(this.vet, date.atTime(time).atZone(ZONE), 30);
		return this.requestRepository.saveAndFlush(request);
	}

	private AppointmentStatus statusOf(Appointment appointment) {
		return this.appointmentRepository.findById(appointment.getId()).orElseThrow().getStatus();
	}

	private AppointmentChange lastChange(Appointment appointment) {
		return this.changeRepository.findByAppointmentIdOrderByTimestampAsc(appointment.getId()).getLast();
	}

	private VisitRow visitFor(Appointment appointment) {
		return this.jdbcTemplate.queryForObject(
				"SELECT id, appointment_id, visit_date, description FROM visits WHERE appointment_id = ?",
				(rs, rowNum) -> new VisitRow(rs.getInt("id"), rs.getInt("appointment_id"),
						rs.getDate("visit_date").toLocalDate(), rs.getString("description")),
				appointment.getId());
	}

	private List<ExceptionRow> exceptions() {
		return this.exceptionRepository.findByVetId(this.vet.getId())
			.stream()
			.map(this::exceptionRow)
			.sorted()
			.toList();
	}

	private ExceptionRow exceptionRow(VetException exception) {
		return new ExceptionRow(exception.getExceptionDate(), exception.isUnavailable(), exception.getStartTime(),
				exception.getEndTime());
	}

	private MockHttpSession loginStaff() throws Exception {
		MvcResult result = this.mockMvc.perform(formLogin().user("staff").password("staff123"))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private record VisitRow(Integer id, Integer appointmentId, LocalDate date, String description) {
	}

	private record ExceptionRow(LocalDate date, boolean unavailable, LocalTime start,
			LocalTime end) implements Comparable<ExceptionRow> {

		@Override
		public int compareTo(ExceptionRow other) {
			int dateComparison = this.date.compareTo(other.date);
			if (dateComparison != 0) {
				return dateComparison;
			}
			return Boolean.compare(this.unavailable, other.unavailable);
		}
	}

	@TestConfiguration
	static class MutableClockConfiguration {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(START_NOW.toInstant(), ZONE);
		}

	}

	static final class MutableClock extends Clock {

		private final AtomicReference<Instant> instant;

		private final ZoneId zone;

		private MutableClock(Instant instant, ZoneId zone) {
			this.instant = new AtomicReference<>(instant);
			this.zone = zone;
		}

		void set(ZonedDateTime dateTime) {
			this.instant.set(dateTime.toInstant());
		}

		ZonedDateTime now() {
			return ZonedDateTime.now(this);
		}

		@Override
		public ZoneId getZone() {
			return this.zone;
		}

		@Override
		public Clock withZone(ZoneId targetZone) {
			return new MutableClock(instant(), targetZone);
		}

		@Override
		public Instant instant() {
			return this.instant.get();
		}

	}

}
