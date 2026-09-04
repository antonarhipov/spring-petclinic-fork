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
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.interpretation.CareType;
import org.springframework.samples.petclinic.scheduling.interpretation.Interpretation;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationRepository;
import org.springframework.samples.petclinic.scheduling.interpretation.Provenance;
import org.springframework.samples.petclinic.scheduling.interpretation.StaffInterpretationService;
import org.springframework.samples.petclinic.scheduling.request.RequestLifecycleService;
import org.springframework.samples.petclinic.scheduling.request.RejectionScope;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEvent;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEventRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.StaffSuggestionService;
import org.springframework.samples.petclinic.scheduling.request.SuggestionRejection;
import org.springframework.samples.petclinic.scheduling.request.SuggestionService;
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
	private SuggestionService suggestionService;

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

		SchedulingRequest req1 = createRequestWithTimestamp(owner, pet, "Accept test", "anytime", now.minusMinutes(30));
		req1 = this.lifecycleService.declineConsent(req1, "george");
		Integer acceptedRequestId = req1.getId();
		ZonedDateTime start1 = now.plusDays(3).withHour(11).withMinute(0);
		this.staffSuggestionService.placeHold(req1, vet, start1, 30, "staff");

		ZonedDateTime acceptStarted = ZonedDateTime.now(this.clock);
		this.mockMvc.perform(post("/my/requests/" + req1.getId() + "/accept").session(ownerSession).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/my/appointments"));
		ZonedDateTime acceptFinished = ZonedDateTime.now(this.clock);

		SchedulingRequest reloaded1 = this.requestRepository.findById(req1.getId()).orElseThrow();
		assertThat(reloaded1.getState()).isEqualTo(RequestState.ACCEPTED);
		assertThat(reloaded1.getHeldVet()).isNull();
		assertThat(reloaded1.getHeldStart()).isNull();
		assertThat(reloaded1.getHeldDuration()).isNull();

		assertThat(this.appointmentRepository.findAll())
			.filteredOn(appointment -> appointment.getRequest() != null
					&& appointment.getRequest().getId().equals(acceptedRequestId))
			.singleElement()
			.satisfies(appointment -> {
				assertThat(appointment.getPet().getId()).isEqualTo(pet.getId());
				assertThat(appointment.getVet().getId()).isEqualTo(vet.getId());
				assertThat(appointment.getStartTime()).isEqualTo(start1);
				assertThat(appointment.getDuration()).isEqualTo(30);
				assertThat(appointment.getReason()).isEqualTo("Accept test");
				assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
			});
		assertThat(eventsWithAction(req1.getId(), "accept")).singleElement().satisfies(event -> {
			assertThat(event.getFromState()).isEqualTo(RequestState.SUGGESTION_OFFERED);
			assertThat(event.getToState()).isEqualTo(RequestState.ACCEPTED);
			assertThat(event.getActor()).isEqualTo("george");
			assertThat(event.getReason()).isNull();
			assertThat(event.getPayload()).isNull();
			assertThat(event.getTimestamp()).isBetween(acceptStarted, acceptFinished);
		});

		for (RejectionScope scope : RejectionScope.values()) {
			SchedulingRequest request = createRequestWithTimestamp(owner, pet, "Reject " + scope, "anytime",
					now.minusMinutes(20));
			request = this.lifecycleService.declineConsent(request, "george");
			createStaffInterpretation(request, now);

			ZonedDateTime originalStart = now.plusDays(3).withHour(14).withMinute(0);
			this.staffSuggestionService.placeHold(request, vet, originalStart, 30, "staff");
			SchedulingRequest offered = this.requestRepository.findById(request.getId()).orElseThrow();
			HoldTuple originalHold = new HoldTuple(offered.getHeldVet().getId(), offered.getHeldStart(),
					offered.getHeldDuration());
			long appointmentsBefore = this.appointmentRepository.count();
			ZonedDateTime rejectionStarted = ZonedDateTime.now(this.clock);

			this.mockMvc
				.perform(post("/my/requests/" + request.getId() + "/another").session(ownerSession)
					.with(csrf())
					.param("scope", scope.name()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/my/requests/" + request.getId()));
			ZonedDateTime rejectionFinished = ZonedDateTime.now(this.clock);

			SchedulingRequest after = this.requestRepository.findById(request.getId()).orElseThrow();
			SuggestionRejection expected = new SuggestionRejection(1, originalHold.vetId(), originalHold.start(),
					scope);
			assertThat(eventsWithAction(request.getId(), SuggestionRejection.EVENT_ACTION)).singleElement()
				.satisfies(event -> {
					assertThat(event.getFromState()).isEqualTo(RequestState.SUGGESTION_OFFERED);
					assertThat(event.getToState()).isEqualTo(RequestState.SUGGESTION_OFFERED);
					assertThat(event.getActor()).isEqualTo("george");
					assertThat(event.getReason()).isEqualTo(scope.name());
					assertThat(event.getPayload()).isEqualTo(expected.payload());
					assertThat(event.getTimestamp()).isBetween(rejectionStarted, rejectionFinished);
					assertThat(SuggestionRejection.fromEvent(event)).contains(expected);
				});
			assertThat(this.suggestionService.currentRejections(after)).containsExactly(expected);
			assertThat(this.appointmentRepository.count()).isEqualTo(appointmentsBefore);

			if (after.getState() == RequestState.SUGGESTION_OFFERED) {
				assertThat(after.getHeldVet()).isNotNull();
				assertThat(after.getHeldStart()).isNotNull();
				assertThat(after.getHeldDuration()).isNotNull();
				HoldTuple replacement = new HoldTuple(after.getHeldVet().getId(), after.getHeldStart(),
						after.getHeldDuration());
				assertThat(replacement).isNotEqualTo(originalHold);
				assertThat(expected.excludes(replacement.vetId(), replacement.start(), 1)).isFalse();
			}
			else {
				assertThat(after.getState()).isEqualTo(RequestState.WITH_STAFF);
				assertThat(after.getHeldVet()).isNull();
				assertThat(after.getHeldStart()).isNull();
				assertThat(after.getHeldDuration()).isNull();
			}
		}
	}

	private void createStaffInterpretation(SchedulingRequest request, ZonedDateTime now) {
		Interpretation staffInterpretation = new Interpretation();
		staffInterpretation.setRequest(request);
		staffInterpretation.setVersion(1);
		staffInterpretation.setProvenance(Provenance.STAFF);
		staffInterpretation.setReasonSummary("Wellness");
		staffInterpretation.setEstimatedMinutes(30);
		staffInterpretation.setCareType(CareType.GENERAL);
		staffInterpretation.setCannotInterpret(false);
		staffInterpretation.setCreatedAt(now.minusMinutes(10));
		this.interpretationRepository.saveAndFlush(staffInterpretation);
	}

	private List<SchedulingRequestEvent> eventsWithAction(Integer requestId, String action) {
		return this.eventRepository.findByRequestIdOrderByTimestampAsc(requestId)
			.stream()
			.filter(event -> action.equals(event.getAction()))
			.toList();
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

	private record HoldTuple(Integer vetId, ZonedDateTime start, int duration) {
	}

}
