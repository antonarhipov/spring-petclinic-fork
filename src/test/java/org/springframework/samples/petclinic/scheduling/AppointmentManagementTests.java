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
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentChange;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentChangeRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentLifecycleService;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentManagementService;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Propagation;
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
class AppointmentManagementTests {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private Clock clock;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private VetRepository vetRepository;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private AppointmentChangeRepository changeRepository;

	@Autowired
	private AppointmentLifecycleService lifecycleService;

	@Autowired
	private AppointmentManagementService managementService;

	private MockMvc mockMvc;

	private Pet pet;

	private Vet firstVet;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		this.pet = owner.getPet(1);
		this.firstVet = this.vetRepository.findById(1).orElseThrow();
	}

	@Test
	@Tag("AC-109")
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	@DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
	void rescheduleMutatesInPlaceAndRecordsOldTime_AC109() throws Exception {
		MockHttpSession staff = login("staff", "staff123");
		ZonedDateTime original = at(2026, 9, 8, 10, 0);
		ZonedDateTime replacement = at(2026, 9, 9, 11, 15);
		Appointment appointment = saveConfirmed(original, "checkup");
		long countBefore = this.appointmentRepository.count();

		String reschedulePage = this.mockMvc
			.perform(get("/staff/appointments/{id}/reschedule", appointment.getId()).session(staff))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(reschedulePage).contains(this.pet.getName(), "value=\"2026-09-08\"", "value=\"10:00\"",
				"value=\"30\"");

		this.mockMvc
			.perform(post("/staff/appointments/{id}/reschedule", appointment.getId()).session(staff)
				.with(csrf())
				.param("vetId", "2")
				.param("appointmentDate", replacement.toLocalDate().toString())
				.param("startTime", "11:15")
				.param("durationMinutes", "45")
				.param("reason", "clinic schedule adjustment"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/calendar"))
			.andExpect(flash().attribute("message", "appointmentRescheduled"));

		Appointment reloaded = this.appointmentRepository.findById(appointment.getId()).orElseThrow();
		assertThat(this.appointmentRepository.count()).isEqualTo(countBefore);
		assertThat(reloaded.getId()).isEqualTo(appointment.getId());
		assertThat(reloaded.getStartTime()).isEqualTo(replacement);
		assertThat(reloaded.getDuration()).isEqualTo(45);
		assertThat(reloaded.getVet().getId()).isEqualTo(2);
		AppointmentChange change = onlyChange(appointment);
		assertThat(change.getOriginalStartTime()).isEqualTo(original);
		assertThat(change.getReason()).isEqualTo("clinic schedule adjustment");
	}

	@Test
	@Tag("AC-110")
	void cancelBeforeStartRecordsReason_AC110() throws Exception {
		MockHttpSession staff = login("staff", "staff123");
		Appointment appointment = saveConfirmed(at(2026, 9, 10, 10, 0), "vaccination");

		this.mockMvc
			.perform(post("/staff/appointments/{id}/cancel", appointment.getId()).session(staff)
				.with(csrf())
				.param("reason", "veterinarian unavailable"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/calendar"))
			.andExpect(flash().attribute("message", "appointmentCancelled"));

		Appointment reloaded = this.appointmentRepository.findById(appointment.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(AppointmentStatus.CANCELLED_BY_STAFF);
		AppointmentChange change = onlyChange(appointment);
		assertThat(change.getAction()).isEqualTo("CANCEL_BY_STAFF");
		assertThat(change.getReason()).isEqualTo("veterinarian unavailable");
	}

	@Test
	@Tag("AC-115")
	void everyStaffActionRequiresAndWritesReason_AC115() throws Exception {
		Appointment blank = saveConfirmed(at(2026, 9, 11, 10, 0), "blank guard");
		long appointmentCount = this.appointmentRepository.count();
		long changeCount = this.changeRepository.count();
		assertThatThrownBy(() -> this.lifecycleService.bookAppointment(this.pet, this.firstVet, at(2026, 9, 12, 10, 0),
				30, " ", null, "staff"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> this.managementService.staffReschedule(blank, "staff", " ", at(2026, 9, 11, 11, 0), 30,
				this.firstVet))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> this.managementService.staffCancel(blank, "staff", null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(this.appointmentRepository.count()).isEqualTo(appointmentCount);
		assertThat(this.changeRepository.count()).isEqualTo(changeCount);
		assertThat(this.appointmentRepository.findById(blank.getId()).orElseThrow().getStatus())
			.isEqualTo(AppointmentStatus.CONFIRMED);

		Appointment managed = this.lifecycleService.bookAppointment(this.pet, this.firstVet, at(2026, 9, 14, 10, 0), 30,
				"staff booking reason", null, "staff");
		this.managementService.staffReschedule(managed, "staff", "staff reschedule reason", at(2026, 9, 14, 11, 0), 45,
				this.firstVet);

		String calendar = this.mockMvc
			.perform(get("/staff/calendar").param("date", "2026-09-14").session(login("staff", "staff123")))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(calendar).contains("staff reschedule reason");

		this.managementService.staffCancel(managed, "staff", "staff cancellation reason");
		List<AppointmentChange> changes = this.changeRepository.findByAppointmentIdOrderByTimestampAsc(managed.getId());
		assertThat(changes).extracting(AppointmentChange::getAction)
			.containsExactly("BOOK", "RESCHEDULE", "CANCEL_BY_STAFF");
		assertThat(changes).extracting(AppointmentChange::getActor).containsOnly("staff");
		assertThat(changes).extracting(AppointmentChange::getReason)
			.containsExactly("staff booking reason", "staff reschedule reason", "staff cancellation reason");
		assertThat(changes).extracting(AppointmentChange::getTimestamp).containsOnly(ZonedDateTime.now(this.clock));
	}

	@Test
	@Tag("AC-119")
	void ownerSeesClinicChangeReasonAndOriginalTime_AC119() throws Exception {
		ZonedDateTime rescheduleOriginal = at(2026, 9, 15, 10, 0);
		Appointment rescheduled = saveConfirmed(rescheduleOriginal, "rescheduled appointment");
		this.managementService.staffReschedule(rescheduled, "staff", "room maintenance", at(2026, 9, 15, 11, 0), 30,
				this.firstVet);
		ZonedDateTime cancelOriginal = at(2026, 9, 16, 12, 0);
		Appointment cancelled = saveConfirmed(cancelOriginal, "cancelled appointment");
		this.managementService.staffCancel(cancelled, "staff", "clinic closure");

		String html = this.mockMvc.perform(get("/my/appointments").session(login("george", "george123")))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(html).contains("Changed by the clinic", "room maintenance", rescheduleOriginal.toString(),
				"clinic closure", cancelOriginal.toString());
	}

	@Test
	@Tag("AC-120")
	void cancelledRowsRemainUnderPast_AC120() throws Exception {
		Appointment appointment = saveConfirmed(at(2026, 9, 17, 10, 0), "future appointment");
		this.managementService.staffCancel(appointment, "staff", "cancelled but retained");

		assertThat(this.appointmentRepository.findById(appointment.getId())).isPresent();
		String html = this.mockMvc.perform(get("/my/appointments").session(login("george", "george123")))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(html).containsSubsequence("id=\"past-appointments\"", "CANCELLED_BY_STAFF",
				"cancelled but retained");
	}

	private Appointment saveConfirmed(ZonedDateTime start, String reason) {
		Appointment appointment = new Appointment();
		appointment.setPet(this.pet);
		appointment.setVet(this.firstVet);
		appointment.setStartTime(start);
		appointment.setDuration(30);
		appointment.setStatus(AppointmentStatus.CONFIRMED);
		appointment.setReason(reason);
		return this.appointmentRepository.saveAndFlush(appointment);
	}

	private AppointmentChange onlyChange(Appointment appointment) {
		List<AppointmentChange> changes = this.changeRepository
			.findByAppointmentIdOrderByTimestampAsc(appointment.getId());
		assertThat(changes).hasSize(1);
		return changes.getFirst();
	}

	private ZonedDateTime at(int year, int month, int day, int hour, int minute) {
		return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, this.clock.getZone());
	}

	private MockHttpSession login(String username, String password) throws Exception {
		MvcResult result = this.mockMvc.perform(formLogin().user(username).password(password))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

}
