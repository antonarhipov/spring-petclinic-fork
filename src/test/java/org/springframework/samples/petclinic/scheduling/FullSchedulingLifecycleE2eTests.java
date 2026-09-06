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
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.interpretation.Interpretation;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationRepository;
import org.springframework.samples.petclinic.scheduling.interpretation.Provenance;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "scheduling.interpretation.executor=synchronous")
@Import(StaffManageAppointmentE2eTests.MutableClockConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FullSchedulingLifecycleE2eTests {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private VetRepository vetRepository;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private InterpretationRepository interpretationRepository;

	@Autowired
	private StaffManageAppointmentE2eTests.MutableClock clock;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
	}

	@Test
	@Tag("AC-138")
	void fullInterleavedOwnerStaffLifecycle_AC138() throws Exception {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPet(1);

		MockHttpSession ownerSession = loginUser("george", "george123");
		MockHttpSession staffSession = loginUser("staff", "staff123");

		// 1. Owner starts request
		MvcResult createReq = this.mockMvc
			.perform(post("/my/requests").session(ownerSession)
				.with(csrf())
				.param("petId", String.valueOf(pet.getId()))
				.param("reasonText", "Annual vaccinations and routine checkup")
				.param("availabilityText", "Monday morning preferred"))
			.andExpect(status().is3xxRedirection())
			.andReturn();

		String reqRedirect = createReq.getResponse().getRedirectedUrl();
		int reqId = Integer.parseInt(reqRedirect.substring("/my/requests/".length()));

		SchedulingRequest req = reload(reqId);
		assertThat(req.getState()).isEqualTo(RequestState.AWAITING_CONSENT);
		assertThat(req.getActivePetId()).isEqualTo(pet.getId());

		// 2. Owner grants consent -> INTERPRETED
		this.mockMvc.perform(post("/my/requests/" + reqId + "/consent").session(ownerSession).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/my/requests/" + reqId));

		req = reload(reqId);
		assertThat(req.getState()).isEqualTo(RequestState.INTERPRETED);

		// 3. Owner confirms interpretation -> SUGGESTION_OFFERED with hold
		this.mockMvc.perform(post("/my/requests/" + reqId + "/confirm").session(ownerSession).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/my/requests/" + reqId));

		req = reload(reqId);
		assertThat(req.getState()).isEqualTo(RequestState.SUGGESTION_OFFERED);
		assertThat(req.hasHold()).isTrue();

		// 4. Owner asks again / routes to staff -> WITH_STAFF, hold cleared
		this.mockMvc
			.perform(post("/my/requests/" + reqId + "/route-to-staff").session(ownerSession)
				.with(csrf())
				.param("reason", "Preferred times not suitable"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/my/requests/" + reqId));

		req = reload(reqId);
		assertThat(req.getState()).isEqualTo(RequestState.WITH_STAFF);
		assertThat(req.hasHold()).isFalse();

		// 5. Staff interprets and runs suggestion -> SUGGESTION_OFFERED with staff hold
		this.mockMvc
			.perform(post("/staff/requests/" + reqId + "/interpretation").session(staffSession)
				.with(csrf())
				.param("reasonSummary", "Comprehensive checkup")
				.param("careType", "GENERAL")
				.param("estimatedMinutes", "30")
				.param("preferredWindows", "MONDAY:09:00-12:00")
				.param("cannotInterpret", "false"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/requests/" + reqId));

		Interpretation staffInterp = this.interpretationRepository.findTopByRequestIdOrderByVersionDesc(reqId)
			.orElseThrow();
		assertThat(staffInterp.getProvenance()).isEqualTo(Provenance.STAFF);

		this.mockMvc.perform(post("/staff/requests/" + reqId + "/suggest").session(staffSession).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/requests/" + reqId));

		req = reload(reqId);
		assertThat(req.getState()).isEqualTo(RequestState.SUGGESTION_OFFERED);
		assertThat(req.hasHold()).isTrue();

		// 6. Owner accepts suggestion -> ACCEPTED, appointment confirmed
		this.mockMvc.perform(post("/my/requests/" + reqId + "/accept").session(ownerSession).with(csrf()))
			.andExpect(status().is3xxRedirection());

		req = reload(reqId);
		assertThat(req.getState()).isEqualTo(RequestState.ACCEPTED);
		assertThat(req.getActivePetId()).isNull();

		List<Appointment> petAppts = this.appointmentRepository.findByPetId(pet.getId());
		Appointment appt = petAppts.stream()
			.filter(a -> a.getRequest() != null && a.getRequest().getId().equals(reqId))
			.findFirst()
			.orElseThrow();
		assertThat(appt.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);

		// 7. Advance clock past appointment start and staff completes visit
		this.clock.set(appt.getStartTime().plusMinutes(15));

		this.mockMvc
			.perform(post("/staff/appointments/" + appt.getId() + "/complete").session(staffSession)
				.with(csrf())
				.param("reason", "Completed annual checkup"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/calendar"));

		Appointment completedAppt = this.appointmentRepository.findById(appt.getId()).orElseThrow();
		assertThat(completedAppt.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);

		// 8. Owner views My appointments: appointment appears under past appointments
		MvcResult finalOwnerView = this.mockMvc.perform(get("/my/appointments").session(ownerSession))
			.andExpect(status().isOk())
			.andReturn();
		String finalHtml = finalOwnerView.getResponse().getContentAsString();
		assertThat(finalHtml).contains("COMPLETED");
		assertThat(finalHtml).contains(pet.getName());
	}

	@Test
	@Tag("AC-138")
	void declinedConsentStaffBookingNoShowLeg_AC138() throws Exception {
		Owner owner = this.ownerRepository.findById(3).orElseThrow();
		Pet pet = owner.getPet("Rosy");
		Vet vet = this.vetRepository.findById(1).orElseThrow();

		MockHttpSession ownerSession = loginUser("eduardo", "eduardo123");
		MockHttpSession staffSession = loginUser("staff", "staff123");

		// 1. Owner starts request
		MvcResult createReq = this.mockMvc
			.perform(post("/my/requests").session(ownerSession)
				.with(csrf())
				.param("petId", String.valueOf(pet.getId()))
				.param("reasonText", "Skin allergy checkup")
				.param("availabilityText", "Wednesday afternoon"))
			.andExpect(status().is3xxRedirection())
			.andReturn();

		String reqRedirect = createReq.getResponse().getRedirectedUrl();
		int reqId = Integer.parseInt(reqRedirect.substring("/my/requests/".length()));

		// 2. Owner declines consent -> WITH_STAFF, no AI call
		this.mockMvc.perform(post("/my/requests/" + reqId + "/decline").session(ownerSession).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/my/requests/" + reqId));

		SchedulingRequest req = reload(reqId);
		assertThat(req.getState()).isEqualTo(RequestState.WITH_STAFF);
		assertThat(req.getActivePetId()).isEqualTo(pet.getId());
		assertThat(this.interpretationRepository.findByRequestIdOrderByVersionDesc(reqId)).isEmpty();

		// 3. Staff directly books and attaches request -> ACCEPTED
		this.mockMvc
			.perform(post("/staff/appointments").session(staffSession)
				.with(csrf())
				.param("petId", String.valueOf(pet.getId()))
				.param("vetId", String.valueOf(vet.getId()))
				.param("appointmentDate", "2026-09-09")
				.param("startTime", "14:00")
				.param("durationMinutes", "30")
				.param("reason", "Direct staff booking for skin allergy")
				.param("requestId", String.valueOf(reqId))
				.param("decision", "attach"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/calendar"));

		req = reload(reqId);
		assertThat(req.getState()).isEqualTo(RequestState.ACCEPTED);
		assertThat(req.getActivePetId()).isNull();

		List<Appointment> petAppts = this.appointmentRepository.findByPetId(pet.getId());
		Appointment appt = petAppts.stream()
			.filter(a -> a.getRequest() != null && a.getRequest().getId().equals(reqId))
			.findFirst()
			.orElseThrow();
		assertThat(appt.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);

		// 4. Advance clock past appointment start and staff marks NO_SHOW
		this.clock.set(appt.getStartTime().plusMinutes(15));

		this.mockMvc
			.perform(post("/staff/appointments/" + appt.getId() + "/no-show").session(staffSession)
				.with(csrf())
				.param("reason", "Patient did not appear for scheduled visit"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/calendar"));

		Appointment noShowAppt = this.appointmentRepository.findById(appt.getId()).orElseThrow();
		assertThat(noShowAppt.getStatus()).isEqualTo(AppointmentStatus.NO_SHOW);

		// 5. Owner views My appointments: appointment appears under past appointments
		MvcResult finalOwnerView = this.mockMvc.perform(get("/my/appointments").session(ownerSession))
			.andExpect(status().isOk())
			.andReturn();
		String finalHtml = finalOwnerView.getResponse().getContentAsString();
		assertThat(finalHtml).contains("NO_SHOW");
		assertThat(finalHtml).contains(pet.getName());
	}

	private SchedulingRequest reload(Integer requestId) {
		return this.requestRepository.findById(requestId).orElseThrow();
	}

	private MockHttpSession loginUser(String username, String password) throws Exception {
		MvcResult login = this.mockMvc.perform(formLogin().user(username).password(password))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		return (MockHttpSession) login.getRequest().getSession(false);
	}

}
