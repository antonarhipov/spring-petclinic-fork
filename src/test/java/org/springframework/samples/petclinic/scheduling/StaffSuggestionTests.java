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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.interpretation.CareType;
import org.springframework.samples.petclinic.scheduling.interpretation.Interpretation;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationRepository;
import org.springframework.samples.petclinic.scheduling.interpretation.Provenance;
import org.springframework.samples.petclinic.scheduling.interpretation.StaffInterpretationService;
import org.springframework.samples.petclinic.scheduling.request.RequestLifecycleService;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEvent;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEventRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.StaffSuggestionService;
import org.springframework.samples.petclinic.scheduling.request.SuggestionRejection;
import org.springframework.samples.petclinic.scheduling.web.StaffInterpretationForm;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class StaffSuggestionTests {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private StaffSuggestionService staffSuggestionService;

	@Autowired
	private StaffInterpretationService staffInterpretationService;

	@Autowired
	private RequestLifecycleService lifecycleService;

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
	@Tag("AC-90")
	void suggestButtonCreatesOneStaffHold() throws Exception {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().get(0);
		ZonedDateTime now = ZonedDateTime.now(this.clock);

		// Request in WITH_STAFF with complete staff interpretation
		SchedulingRequest request = createRequestWithTimestamp(owner, pet, "Routine checkup", "anytime",
				now.minusMinutes(20));
		request = this.lifecycleService.declineConsent(request, "george");

		StaffInterpretationForm form = new StaffInterpretationForm();
		form.setReasonSummary("Routine checkup");
		form.setEstimatedMinutes(30);
		form.setCareType("GENERAL");
		form.setPreferredWindows("MONDAY:09:00-12:00");
		this.staffInterpretationService.saveInterpretation(request.getId(), form, "staff");

		MockHttpSession staffSession = login("staff", "staff123");

		// POST /staff/requests/{id}/suggest
		this.mockMvc.perform(post("/staff/requests/" + request.getId() + "/suggest").session(staffSession).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/requests/" + request.getId()));

		// Reload and verify
		SchedulingRequest reloaded = this.requestRepository.findById(request.getId()).orElseThrow();
		assertThat(reloaded.getState()).isEqualTo(RequestState.SUGGESTION_OFFERED);
		assertThat(reloaded.hasHold()).isTrue();
		assertThat(reloaded.getHeldVet()).isNotNull();
		assertThat(reloaded.getHeldStart()).isNotNull();
		assertThat(reloaded.getHeldDuration()).isEqualTo(30);

		// Verify event
		List<SchedulingRequestEvent> events = this.eventRepository.findByRequestIdOrderByTimestampAsc(request.getId());
		SchedulingRequestEvent lastEvent = events.get(events.size() - 1);
		assertThat(lastEvent.getFromState()).isEqualTo(RequestState.WITH_STAFF);
		assertThat(lastEvent.getToState()).isEqualTo(RequestState.SUGGESTION_OFFERED);
		assertThat(lastEvent.getAction()).isEqualTo("staff place suggestion");
		assertThat(lastEvent.getActor()).isEqualTo("staff");
	}

	@Test
	@Tag("AC-91")
	void ownerAcceptRejectUsesSameRules_AC91() throws Exception {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().get(0);
		Vet vet = this.vetRepository.findById(1).orElseThrow();
		ZonedDateTime now = ZonedDateTime.now(this.clock);

		MockHttpSession ownerSession = login("george", "george123");

		// --- Scenario 1: Owner accepts staff-placed suggestion ---
		SchedulingRequest req1 = createRequestWithTimestamp(owner, pet, "Accept test", "anytime", now.minusMinutes(30));
		req1 = this.lifecycleService.declineConsent(req1, "george");

		// Staff places suggestion on next Monday (valid opening hours & weekly block)
		ZonedDateTime start1 = now.plusDays(3).withHour(11).withMinute(0);
		this.staffSuggestionService.placeHold(req1, vet, start1, 30, "staff");

		// Owner accepts via HTTP
		this.mockMvc.perform(post("/my/requests/" + req1.getId() + "/accept").session(ownerSession).with(csrf()))
			.andExpect(status().is3xxRedirection());

		// Verify request is ACCEPTED, hold cleared, and appointment created
		SchedulingRequest reloaded1 = this.requestRepository.findById(req1.getId()).orElseThrow();
		assertThat(reloaded1.getState()).isEqualTo(RequestState.ACCEPTED);
		assertThat(reloaded1.hasHold()).isFalse();

		List<Appointment> appts = this.appointmentRepository.findByPetId(pet.getId());
		assertThat(appts).anyMatch(a -> a.getVet().getId().equals(vet.getId()) && a.getStartTime().isEqual(start1)
				&& a.getDuration() == 30);

		// --- Scenario 2: Owner rejects staff-placed suggestion ---
		SchedulingRequest req2 = createRequestWithTimestamp(owner, pet, "Reject test", "anytime", now.minusMinutes(20));
		req2 = this.lifecycleService.declineConsent(req2, "george");

		// Create complete interpretation for solver re-evaluation
		Interpretation staffInterp = new Interpretation();
		staffInterp.setRequest(req2);
		staffInterp.setVersion(1);
		staffInterp.setProvenance(Provenance.STAFF);
		staffInterp.setReasonSummary("Wellness");
		staffInterp.setEstimatedMinutes(30);
		staffInterp.setCareType(CareType.GENERAL);
		staffInterp.setCannotInterpret(false);
		staffInterp.setCreatedAt(now.minusMinutes(10));
		this.interpretationRepository.saveAndFlush(staffInterp);

		ZonedDateTime start2 = now.plusDays(3).withHour(14).withMinute(0);
		this.staffSuggestionService.placeHold(req2, vet, start2, 30, "staff");

		// Owner rejects with NOT_THIS_TIME
		this.mockMvc
			.perform(post("/my/requests/" + req2.getId() + "/another").session(ownerSession)
				.with(csrf())
				.param("scope", "NOT_THIS_TIME"))
			.andExpect(status().is3xxRedirection());

		// Verify rejection event recorded with scope
		List<SchedulingRequestEvent> events = this.eventRepository.findByRequestIdOrderByTimestampAsc(req2.getId());
		assertThat(events).anyMatch(
				e -> SuggestionRejection.EVENT_ACTION.equals(e.getAction()) && "NOT_THIS_TIME".equals(e.getReason()));
	}

	private SchedulingRequest createRequestWithTimestamp(Owner owner, Pet pet, String reason, String availability,
			ZonedDateTime createdAt) {
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(owner);
		request.setPet(pet);
		request.setState(RequestState.AWAITING_CONSENT);
		request.setReasonText(reason);
		request.setAvailabilityText(availability);
		request.setActivePetId(null);
		request.setFailedAttempts(0);
		request.setCreatedAt(createdAt);
		request.setUpdatedAt(createdAt);
		SchedulingRequest saved = this.requestRepository.saveAndFlush(request);

		SchedulingRequestEvent event = new SchedulingRequestEvent();
		event.setRequest(saved);
		event.setFromState(null);
		event.setToState(RequestState.AWAITING_CONSENT);
		event.setActor("george");
		event.setAction("CREATE_REQUEST");
		event.setTimestamp(createdAt);
		this.eventRepository.saveAndFlush(event);

		return saved;
	}

	private MockHttpSession login(String username, String password) throws Exception {
		MvcResult result = this.mockMvc.perform(formLogin().user(username).password(password))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

}
