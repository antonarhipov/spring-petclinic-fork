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
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.interpretation.Interpretation;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationRepository;
import org.springframework.samples.petclinic.scheduling.interpretation.Provenance;
import org.springframework.samples.petclinic.scheduling.request.RequestLifecycleService;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEvent;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEventRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class StaffResolveRequestE2eTests {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private RequestLifecycleService requestLifecycleService;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private SchedulingRequestEventRepository eventRepository;

	@Autowired
	private InterpretationRepository interpretationRepository;

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
	@Tag("AC-138")
	void staffResolveQueuedRequestSteps1Through4() throws Exception {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().get(0);
		ZonedDateTime now = ZonedDateTime.now(this.clock);

		// Step 1: Owner creates request and declines consent -> WITH_STAFF
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(owner);
		request.setPet(pet);
		request.setState(RequestState.AWAITING_CONSENT);
		request.setReasonText("Persistent limp");
		request.setAvailabilityText("Monday mornings");
		request.setActivePetId(pet.getId());
		request.setFailedAttempts(0);
		request.setCreatedAt(now.minusMinutes(30));
		request.setUpdatedAt(now.minusMinutes(30));
		request = this.requestRepository.saveAndFlush(request);

		SchedulingRequestEvent createEv = new SchedulingRequestEvent();
		createEv.setRequest(request);
		createEv.setToState(RequestState.AWAITING_CONSENT);
		createEv.setActor("george");
		createEv.setAction("CREATE_REQUEST");
		createEv.setTimestamp(now.minusMinutes(30));
		this.eventRepository.saveAndFlush(createEv);

		request = this.requestLifecycleService.declineConsent(request, "george");
		assertThat(request.getState()).isEqualTo(RequestState.WITH_STAFF);

		// Staff logs in
		MockHttpSession staffSession = login("staff", "staff123");

		// Staff checks queue
		this.mockMvc.perform(get("/staff/queue").session(staffSession))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Persistent limp")))
			.andExpect(content().string(containsString("Declined consent")));

		// Staff views request detail
		this.mockMvc.perform(get("/staff/requests/" + request.getId()).session(staffSession))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Persistent limp")))
			.andExpect(content().string(containsString("WITH_STAFF")));

		// Step 2: Staff edits/creates interpretation
		this.mockMvc.perform(get("/staff/requests/" + request.getId() + "/interpretation").session(staffSession))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Staff interpretation")));

		this.mockMvc
			.perform(post("/staff/requests/" + request.getId() + "/interpretation").session(staffSession)
				.with(csrf())
				.param("reasonSummary", "Orthopedic evaluation")
				.param("careType", "GENERAL")
				.param("estimatedMinutes", "30")
				.param("preferredWindows", "MONDAY:09:00-12:00")
				.param("cannotInterpret", "false"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/requests/" + request.getId()));

		// Verify interpretation persisted
		Interpretation interp = this.interpretationRepository.findTopByRequestIdOrderByVersionDesc(request.getId())
			.orElseThrow();
		assertThat(interp.getProvenance()).isEqualTo(Provenance.STAFF);
		assertThat(interp.getReasonSummary()).isEqualTo("Orthopedic evaluation");

		// Step 3: Staff runs solver suggestion
		this.mockMvc.perform(post("/staff/requests/" + request.getId() + "/suggest").session(staffSession).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/requests/" + request.getId()));

		// Reload request and verify hold
		SchedulingRequest offered = this.requestRepository.findById(request.getId()).orElseThrow();
		assertThat(offered.getState()).isEqualTo(RequestState.SUGGESTION_OFFERED);
		assertThat(offered.hasHold()).isTrue();

		// Staff views detail with hold rendered
		this.mockMvc.perform(get("/staff/requests/" + request.getId()).session(staffSession))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("SUGGESTION_OFFERED")));

		// Step 4: Owner logs in and accepts suggestion
		MockHttpSession ownerSession = login("george", "george123");

		this.mockMvc.perform(get("/my/requests/" + request.getId()).session(ownerSession))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("SUGGESTION_OFFERED")));

		this.mockMvc.perform(post("/my/requests/" + request.getId() + "/accept").session(ownerSession).with(csrf()))
			.andExpect(status().is3xxRedirection());

		// Verify request accepted and appointment confirmed
		SchedulingRequest accepted = this.requestRepository.findById(request.getId()).orElseThrow();
		assertThat(accepted.getState()).isEqualTo(RequestState.ACCEPTED);
		assertThat(accepted.hasHold()).isFalse();

		final Integer targetRequestId = request.getId();
		List<Appointment> appts = this.appointmentRepository.findByPetId(pet.getId());
		assertThat(appts).anyMatch(a -> a.getStatus() == AppointmentStatus.CONFIRMED && a.getRequest() != null
				&& a.getRequest().getId().equals(targetRequestId));

		// Owner views appointment list
		this.mockMvc.perform(get("/my/appointments").session(ownerSession))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("CONFIRMED")))
			.andExpect(content().string(containsString(pet.getName())));
	}

	@Test
	@Tag("AC-93")
	void directBookAttachExtension() throws Exception {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().get(0);
		Vet vet = this.vetRepository.findById(1).orElseThrow();
		ZonedDateTime now = ZonedDateTime.now(this.clock);
		ZonedDateTime apptStart = now.plusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0);

		// Owner creates request in WITH_STAFF
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(owner);
		request.setPet(pet);
		request.setState(RequestState.AWAITING_CONSENT);
		request.setReasonText("Dental issue");
		request.setAvailabilityText("anytime");
		request.setActivePetId(pet.getId());
		request.setFailedAttempts(0);
		request.setCreatedAt(now.minusMinutes(20));
		request.setUpdatedAt(now.minusMinutes(20));
		request = this.requestRepository.saveAndFlush(request);

		request = this.requestLifecycleService.declineConsent(request, "george");
		assertThat(request.getState()).isEqualTo(RequestState.WITH_STAFF);

		MockHttpSession staffSession = login("staff", "staff123");

		// Staff opens direct booking form with requestId
		this.mockMvc
			.perform(get("/staff/appointments/new").param("requestId", String.valueOf(request.getId()))
				.session(staffSession))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Dental issue")))
			.andExpect(content().string(containsString("Attach to open request")));

		// Staff submits direct booking with attach
		this.mockMvc
			.perform(post("/staff/appointments").session(staffSession)
				.with(csrf())
				.param("petId", String.valueOf(pet.getId()))
				.param("vetId", String.valueOf(vet.getId()))
				.param("appointmentDate", apptStart.format(DateTimeFormatter.ISO_LOCAL_DATE))
				.param("startTime", "10:00")
				.param("durationMinutes", "30")
				.param("reason", "Direct dental treatment")
				.param("requestId", String.valueOf(request.getId()))
				.param("decision", "attach"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/calendar"));

		// Verify request closed and appointment created
		SchedulingRequest reloaded = this.requestRepository.findById(request.getId()).orElseThrow();
		assertThat(reloaded.getState()).isEqualTo(RequestState.ACCEPTED);
		assertThat(reloaded.hasHold()).isFalse();

		final Integer attachedReqId = request.getId();
		List<Appointment> appts = this.appointmentRepository.findByPetId(pet.getId());
		assertThat(appts).anyMatch(a -> a.getVet().getId().equals(vet.getId()) && a.getStartTime().isEqual(apptStart)
				&& a.getStatus() == AppointmentStatus.CONFIRMED && a.getRequest() != null
				&& a.getRequest().getId().equals(attachedReqId));

		// Owner views appointment list
		MockHttpSession ownerSession = login("george", "george123");
		this.mockMvc.perform(get("/my/appointments").session(ownerSession))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("CONFIRMED")))
			.andExpect(content().string(containsString(pet.getName())));
	}

	@Test
	@Tag("AC-94")
	void directBookLeaveOpenExtension() throws Exception {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().get(0);
		Vet vet = this.vetRepository.findById(1).orElseThrow();
		ZonedDateTime now = ZonedDateTime.now(this.clock);
		ZonedDateTime apptStart = now.plusDays(3).withHour(14).withMinute(0).withSecond(0).withNano(0);

		// Owner creates request in WITH_STAFF
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(owner);
		request.setPet(pet);
		request.setState(RequestState.AWAITING_CONSENT);
		request.setReasonText("Skin allergy review");
		request.setAvailabilityText("flexible");
		request.setActivePetId(pet.getId());
		request.setFailedAttempts(0);
		request.setCreatedAt(now.minusMinutes(20));
		request.setUpdatedAt(now.minusMinutes(20));
		request = this.requestRepository.saveAndFlush(request);

		request = this.requestLifecycleService.declineConsent(request, "george");
		assertThat(request.getState()).isEqualTo(RequestState.WITH_STAFF);

		MockHttpSession staffSession = login("staff", "staff123");

		// Staff submits direct booking with leave_open
		this.mockMvc
			.perform(post("/staff/appointments").session(staffSession)
				.with(csrf())
				.param("petId", String.valueOf(pet.getId()))
				.param("vetId", String.valueOf(vet.getId()))
				.param("appointmentDate", apptStart.format(DateTimeFormatter.ISO_LOCAL_DATE))
				.param("startTime", "14:00")
				.param("durationMinutes", "30")
				.param("reason", "Urgent vaccination")
				.param("requestId", String.valueOf(request.getId()))
				.param("decision", "leave_open"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/calendar"));

		// Verify request remains WITH_STAFF and independent appointment created
		SchedulingRequest reloaded = this.requestRepository.findById(request.getId()).orElseThrow();
		assertThat(reloaded.getState()).isEqualTo(RequestState.WITH_STAFF);

		List<Appointment> appts = this.appointmentRepository.findByPetId(pet.getId());
		assertThat(appts).anyMatch(a -> a.getVet().getId().equals(vet.getId()) && a.getStartTime().isEqual(apptStart)
				&& a.getStatus() == AppointmentStatus.CONFIRMED && a.getRequest() == null);

		// Owner views appointments and open request
		MockHttpSession ownerSession = login("george", "george123");
		this.mockMvc.perform(get("/my/appointments").session(ownerSession))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("CONFIRMED")))
			.andExpect(content().string(containsString(pet.getName())));

		this.mockMvc.perform(get("/my/requests/" + request.getId()).session(ownerSession))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("WITH_STAFF")));
	}

	private MockHttpSession login(String username, String password) throws Exception {
		MvcResult result = this.mockMvc.perform(formLogin().user(username).password(password))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

}
