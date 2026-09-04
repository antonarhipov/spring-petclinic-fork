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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

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
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentLifecycleService;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.appointment.StaffBookingService;
import org.springframework.samples.petclinic.scheduling.request.RequestLifecycleService;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEvent;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEventRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.web.StaffBookingForm;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class StaffDirectBookingTests {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private StaffBookingService staffBookingService;

	@Autowired
	private AppointmentLifecycleService appointmentLifecycleService;

	@Autowired
	private RequestLifecycleService requestLifecycleService;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private SchedulingRequestEventRepository eventRepository;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private VetRepository vetRepository;

	@Autowired
	private Clock clock;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
	}

	@Test
	@Tag("AC-92")
	void bookingNeedsNoInterpretation_AC92() throws Exception {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().get(0);
		Vet vet = this.vetRepository.findById(1).orElseThrow();
		ZonedDateTime now = ZonedDateTime.now(this.clock);
		ZonedDateTime apptStart = now.plusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0);

		MockHttpSession staffSession = login("staff", "staff123");

		// POST /staff/appointments
		this.mockMvc
			.perform(post("/staff/appointments").session(staffSession)
				.with(csrf())
				.param("petId", String.valueOf(pet.getId()))
				.param("vetId", String.valueOf(vet.getId()))
				.param("appointmentDate", apptStart.format(DateTimeFormatter.ISO_LOCAL_DATE))
				.param("startTime", "10:00")
				.param("durationMinutes", "45")
				.param("reason", "Direct walk-in checkup"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/calendar"));

		// Verify appointment created
		List<Appointment> appointments = this.appointmentRepository.findByPetId(pet.getId());
		assertThat(appointments)
			.anyMatch(a -> a.getVet().getId().equals(vet.getId()) && a.getStartTime().isEqual(apptStart)
					&& a.getDuration() == 45 && "Direct walk-in checkup".equals(a.getReason())
					&& a.getStatus() == AppointmentStatus.CONFIRMED && a.getRequest() == null);
	}

	@Test
	@Tag("AC-93")
	void attachAcceptsReleasesAndLogsFromEveryNonTerminalState_AC93() {
		List<Owner> allOwners = this.ownerRepository.findAll();
		Vet vet = this.vetRepository.findById(1).orElseThrow();
		ZonedDateTime now = ZonedDateTime.now(this.clock);

		List<RequestState> nonTerminalStates = List.of(RequestState.AWAITING_CONSENT, RequestState.INTERPRETING,
				RequestState.INTERPRETATION_FAILED, RequestState.INTERPRETED, RequestState.SUGGESTION_OFFERED,
				RequestState.WITH_STAFF);

		for (int i = 0; i < nonTerminalStates.size(); i++) {
			RequestState targetState = nonTerminalStates.get(i);
			Owner owner = allOwners.get(i);
			Pet pet = owner.getPets().get(0);
			SchedulingRequest request = setupRequestInState(owner, pet, targetState, vet, now, i + 1);

			ZonedDateTime apptStart = now.plusDays(3 + i).withHour(9).withMinute(0).withSecond(0).withNano(0);

			StaffBookingForm form = new StaffBookingForm();
			form.setPetId(pet.getId());
			form.setVetId(vet.getId());
			form.setAppointmentDate(apptStart.format(DateTimeFormatter.ISO_LOCAL_DATE));
			form.setStartTime("09:00");
			form.setDurationMinutes(30);
			form.setReason("Attach booking for " + targetState);
			form.setRequestId(request.getId());
			form.setDecision("attach");
			int eventCountBefore = this.eventRepository.findByRequestIdOrderByTimestampAsc(request.getId()).size();

			Appointment appt = this.staffBookingService.directBook(form, "staff");

			// Assertions:
			// 1. Request transitions to ACCEPTED
			SchedulingRequest reloaded = this.requestRepository.findById(request.getId()).orElseThrow();
			assertThat(reloaded.getState()).as("Request in %s must transition to ACCEPTED on attach", targetState)
				.isEqualTo(RequestState.ACCEPTED);

			// 2. Hold is cleared and active pet is null
			assertThat(reloaded.hasHold()).isFalse();
			assertThat(reloaded.getHeldVet()).isNull();
			assertThat(reloaded.getHeldStart()).isNull();
			assertThat(reloaded.getHeldDuration()).isNull();
			assertThat(reloaded.getActivePetId()).isNull();

			// 3. Appointment is created and linked to request
			assertThat(appt.getRequest()).isNotNull();
			assertThat(appt.getRequest().getId()).isEqualTo(request.getId());
			assertThat(appt.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);

			// 4. Exactly one attach event logged
			List<SchedulingRequestEvent> events = this.eventRepository
				.findByRequestIdOrderByTimestampAsc(request.getId());
			assertThat(events).hasSize(eventCountBefore + 1);
			SchedulingRequestEvent lastEvent = events.get(events.size() - 1);
			assertThat(lastEvent.getFromState()).isEqualTo(targetState);
			assertThat(lastEvent.getToState()).isEqualTo(RequestState.ACCEPTED);
			assertThat(lastEvent.getAction()).isEqualTo("staff book attach");
			assertThat(lastEvent.getActor()).isEqualTo("staff");
		}
	}

	@Test
	@Tag("AC-94")
	void leaveOpenPreservesRequestInEveryNonTerminalState_AC94() {
		List<Owner> allOwners = this.ownerRepository.findAll();
		Vet vet = this.vetRepository.findById(1).orElseThrow();
		ZonedDateTime now = ZonedDateTime.now(this.clock);

		List<RequestState> nonTerminalStates = List.of(RequestState.AWAITING_CONSENT, RequestState.INTERPRETING,
				RequestState.INTERPRETATION_FAILED, RequestState.INTERPRETED, RequestState.SUGGESTION_OFFERED,
				RequestState.WITH_STAFF);

		for (int i = 0; i < nonTerminalStates.size(); i++) {
			RequestState targetState = nonTerminalStates.get(i);
			Owner owner = allOwners.get(i);
			Pet pet = owner.getPets().get(0);
			SchedulingRequest request = setupRequestInState(owner, pet, targetState, vet, now, i + 10);

			RequestSnapshot requestBefore = snapshot(request);
			int eventCountBefore = this.eventRepository.findByRequestIdOrderByTimestampAsc(request.getId()).size();

			ZonedDateTime apptStart = now.plusDays(4 + i).withHour(11).withMinute(0).withSecond(0).withNano(0);

			StaffBookingForm form = new StaffBookingForm();
			form.setPetId(pet.getId());
			form.setVetId(vet.getId());
			form.setAppointmentDate(apptStart.format(DateTimeFormatter.ISO_LOCAL_DATE));
			form.setStartTime("11:00");
			form.setDurationMinutes(30);
			form.setReason("Leave open booking for " + targetState);
			form.setRequestId(request.getId());
			form.setDecision("leave_open");

			Appointment appt = this.staffBookingService.directBook(form, "staff");

			SchedulingRequest reloaded = this.requestRepository.findById(request.getId()).orElseThrow();
			assertThat(snapshot(reloaded)).as("leave-open must not change any request field from %s", targetState)
				.isEqualTo(requestBefore);

			// Appointment is created with null request link (independent appointment).
			assertThat(appt.getRequest()).isNull();
			assertThat(appt.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);

			assertThat(this.eventRepository.findByRequestIdOrderByTimestampAsc(request.getId()))
				.as("leave-open must not append a request event")
				.hasSize(eventCountBefore);
		}
	}

	@Test
	@Tag("AC-95")
	void directBookingVisibleToOwner() throws Exception {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().get(0);
		Vet vet = this.vetRepository.findById(1).orElseThrow();
		ZonedDateTime now = ZonedDateTime.now(this.clock);
		ZonedDateTime apptStart = now.plusDays(3).withHour(15).withMinute(0).withSecond(0).withNano(0);

		// Staff direct books appointment
		StaffBookingForm form = new StaffBookingForm();
		form.setPetId(pet.getId());
		form.setVetId(vet.getId());
		form.setAppointmentDate(apptStart.format(DateTimeFormatter.ISO_LOCAL_DATE));
		form.setStartTime("15:00");
		form.setDurationMinutes(30);
		form.setReason("Vaccination");

		Appointment appt = this.staffBookingService.directBook(form, "staff");

		// Owner views appointment list
		MockHttpSession ownerSession = login("george", "george123");
		this.mockMvc.perform(get("/my/appointments").session(ownerSession))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("CONFIRMED")))
			.andExpect(content().string(containsString(pet.getName())));

		// Owner cancels appointment
		this.appointmentLifecycleService.ownerCancel(appt, "george");

		// Reload and verify status
		Appointment reloaded = this.appointmentRepository.findById(appt.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(AppointmentStatus.CANCELLED_BY_OWNER);

		// Verify cancelled appointment appears under items on owner appointments page
		this.mockMvc.perform(get("/my/appointments").session(ownerSession))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("CANCELLED_BY_OWNER")))
			.andExpect(content().string(containsString(pet.getName())));
	}

	private SchedulingRequest setupRequestInState(Owner owner, Pet pet, RequestState state, Vet vet, ZonedDateTime now,
			int index) {
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(owner);
		request.setPet(pet);
		request.setState(RequestState.AWAITING_CONSENT);
		request.setReasonText("Matrix request " + index);
		request.setAvailabilityText("anytime");
		request.setActivePetId(pet.getId());
		request.setFailedAttempts(0);
		request.setCreatedAt(now.minusMinutes(100 + index));
		request.setUpdatedAt(now.minusMinutes(100 + index));
		SchedulingRequest saved = this.requestRepository.saveAndFlush(request);

		SchedulingRequestEvent event = new SchedulingRequestEvent();
		event.setRequest(saved);
		event.setFromState(null);
		event.setToState(RequestState.AWAITING_CONSENT);
		event.setActor("george");
		event.setAction("CREATE_REQUEST");
		event.setTimestamp(now.minusMinutes(100 + index));
		this.eventRepository.saveAndFlush(event);

		if (state == RequestState.AWAITING_CONSENT) {
			return saved;
		}

		if (state == RequestState.INTERPRETING) {
			return this.requestLifecycleService.consent(saved, "george");
		}

		if (state == RequestState.INTERPRETATION_FAILED) {
			saved = this.requestLifecycleService.consent(saved, "george");
			return this.requestLifecycleService.interpretationFailed(saved, "system", "cannotInterpret");
		}

		if (state == RequestState.INTERPRETED) {
			saved = this.requestLifecycleService.consent(saved, "george");
			return this.requestLifecycleService.interpretationUsable(saved, "system");
		}

		if (state == RequestState.SUGGESTION_OFFERED) {
			saved = this.requestLifecycleService.consent(saved, "george");
			saved = this.requestLifecycleService.interpretationUsable(saved, "system");
			ZonedDateTime heldStart = now.plusDays(3 + (index % 5))
				.withHour(10)
				.withMinute(0)
				.withSecond(0)
				.withNano(0);
			return this.requestLifecycleService.confirmFeasible(saved, "george", vet, heldStart, 30);
		}

		if (state == RequestState.WITH_STAFF) {
			return this.requestLifecycleService.declineConsent(saved, "george");
		}

		return saved;
	}

	private MockHttpSession login(String username, String password) throws Exception {
		MvcResult result = this.mockMvc.perform(formLogin().user(username).password(password))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private static RequestSnapshot snapshot(SchedulingRequest request) {
		return new RequestSnapshot(request.getId(), request.getPet().getId(), request.getOwner().getId(),
				request.getState(), request.getReasonText(), request.getAvailabilityText(), request.getActivePetId(),
				request.getFailedAttempts(), request.getHeldVet() != null ? request.getHeldVet().getId() : null,
				request.getHeldStart() != null ? request.getHeldStart().toInstant() : null, request.getHeldDuration(),
				request.getCreatedAt().toInstant(), request.getUpdatedAt().toInstant());
	}

	private record RequestSnapshot(Integer id, Integer petId, Integer ownerId, RequestState state, String reason,
			String availability, Integer activePetId, int failedAttempts, Integer heldVetId, Instant heldStart,
			Integer heldDuration, Instant createdAt, Instant updatedAt) {
	}

}
