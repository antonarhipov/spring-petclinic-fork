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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
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
import org.springframework.samples.petclinic.scheduling.interpretation.CareType;
import org.springframework.samples.petclinic.scheduling.interpretation.Interpretation;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationRepository;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationWindow;
import org.springframework.samples.petclinic.scheduling.interpretation.Provenance;
import org.springframework.samples.petclinic.scheduling.interpretation.StaffInterpretationService;
import org.springframework.samples.petclinic.scheduling.interpretation.WindowKind;
import org.springframework.samples.petclinic.scheduling.request.RequestLifecycleService;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEvent;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEventRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.web.StaffInterpretationForm;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
public class StaffInterpretationTests {

	@Autowired
	private WebApplicationContext context;

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
	@Tag("AC-55")
	void staffEditCreatesNewVersionWithoutOverwrite_AC55() {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().get(0);
		Vet vet = this.vetRepository.findById(1).orElseThrow();
		ZonedDateTime now = ZonedDateTime.now(this.clock);

		SchedulingRequest request = createRequestWithTimestamp(owner, pet, "Original request", "anytime",
				now.minusMinutes(30));
		request = this.lifecycleService.declineConsent(request, "george");

		// 1. Initial AI version
		Interpretation aiInterp = new Interpretation();
		aiInterp.setRequest(request);
		aiInterp.setVersion(1);
		aiInterp.setProvenance(Provenance.AI);
		aiInterp.setReasonSummary("AI guess");
		aiInterp.setEstimatedMinutes(30);
		aiInterp.setCareType(CareType.GENERAL);
		aiInterp.setCannotInterpret(false);
		aiInterp.setRawResponse("{\"ai_response\": true}");
		aiInterp.setModelTag("ministral-3:14b");
		aiInterp.setPromptVersion("v1.0");
		aiInterp.setCreatedAt(now.minusMinutes(20));
		this.interpretationRepository.saveAndFlush(aiInterp);

		// 2. Staff edits interpretation
		StaffInterpretationForm form = new StaffInterpretationForm();
		form.setReasonSummary("Staff corrected diagnosis");
		form.setEstimatedMinutes(60);
		form.setCareType("SPECIALTY");
		form.setSpecialty("surgery");
		form.setPreferredVetId(vet.getId());
		form.setCannotInterpret(false);

		Interpretation staffInterp = this.staffInterpretationService.saveInterpretation(request.getId(), form, "staff");

		// Assertions: 2 rows exist, AI row is completely untouched, STAFF row has
		// incremented version and new fields
		List<Interpretation> versions = this.interpretationRepository
			.findByRequestIdOrderByVersionDesc(request.getId());
		assertThat(versions).hasSize(2);

		Interpretation v2 = versions.get(0);
		Interpretation v1 = versions.get(1);

		// Field-by-field check for V1 (AI)
		assertThat(v1.getVersion()).isEqualTo(1);
		assertThat(v1.getProvenance()).isEqualTo(Provenance.AI);
		assertThat(v1.getReasonSummary()).isEqualTo("AI guess");
		assertThat(v1.getEstimatedMinutes()).isEqualTo(30);
		assertThat(v1.getCareType()).isEqualTo(CareType.GENERAL);
		assertThat(v1.getRawResponse()).isEqualTo("{\"ai_response\": true}");
		assertThat(v1.getModelTag()).isEqualTo("ministral-3:14b");
		assertThat(v1.getPromptVersion()).isEqualTo("v1.0");

		// Field-by-field check for V2 (STAFF)
		assertThat(v2.getVersion()).isEqualTo(2);
		assertThat(v2.getProvenance()).isEqualTo(Provenance.STAFF);
		assertThat(v2.getReasonSummary()).isEqualTo("Staff corrected diagnosis");
		assertThat(v2.getEstimatedMinutes()).isEqualTo(60);
		assertThat(v2.getCareType()).isEqualTo(CareType.SPECIALTY);
		assertThat(v2.getSpecialty()).isEqualTo("surgery");
		assertThat(v2.getPreferredVet().getId()).isEqualTo(vet.getId());
		assertThat(v2.getRawResponse()).isNull();
		assertThat(v2.getModelTag()).isNull();
		assertThat(v2.getPromptVersion()).isNull();
	}

	@Test
	@Tag("AC-56")
	void provenanceVisibleOnlyToStaff_AC56() throws Exception {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().get(0);
		ZonedDateTime now = ZonedDateTime.now(this.clock);

		SchedulingRequest request = createRequestWithTimestamp(owner, pet, "Checkup", "Monday", now.minusMinutes(30));
		request = this.lifecycleService.consent(request, "george");
		request = this.lifecycleService.interpretationUsable(request, "system");

		Interpretation aiInterp = new Interpretation();
		aiInterp.setRequest(request);
		aiInterp.setVersion(1);
		aiInterp.setProvenance(Provenance.AI);
		aiInterp.setReasonSummary("Annual exam");
		aiInterp.setEstimatedMinutes(30);
		aiInterp.setCareType(CareType.GENERAL);
		aiInterp.setCannotInterpret(false);
		aiInterp.setRawResponse("RAW_JSON_SECRET_PAYLOAD_XYZ_123");
		aiInterp.setModelTag("ministral-3:14b");
		aiInterp.setPromptVersion("prompt-v2.5");
		aiInterp.setCreatedAt(now.minusMinutes(10));
		this.interpretationRepository.saveAndFlush(aiInterp);

		// 1. Staff views request detail -> contains raw/model/prompt
		MockHttpSession staffSession = login("staff", "staff123");
		this.mockMvc.perform(get("/staff/requests/" + request.getId()).session(staffSession))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("RAW_JSON_SECRET_PAYLOAD_XYZ_123")))
			.andExpect(content().string(containsString("ministral-3:14b")))
			.andExpect(content().string(containsString("prompt-v2.5")));

		// 2. Owner views request detail -> does NOT contain raw/model/prompt
		MockHttpSession ownerSession = login("george", "george123");
		this.mockMvc.perform(get("/my/requests/" + request.getId()).session(ownerSession))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString("RAW_JSON_SECRET_PAYLOAD_XYZ_123"))))
			.andExpect(content().string(not(containsString("ministral-3:14b"))))
			.andExpect(content().string(not(containsString("prompt-v2.5"))));
	}

	@Test
	@Tag("AC-89")
	void staffCreatesAndEditsStructuredInterpretation_AC89() throws Exception {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().get(0);
		Vet vet = this.vetRepository.findById(1).orElseThrow();
		ZonedDateTime now = ZonedDateTime.now(this.clock);

		SchedulingRequest request = createRequestWithTimestamp(owner, pet, "Knee injury", "flexible",
				now.minusMinutes(20));
		request = this.lifecycleService.declineConsent(request, "george");

		MockHttpSession staffSession = login("staff", "staff123");

		// Save 1: POST /staff/requests/{id}/interpretation
		this.mockMvc
			.perform(post("/staff/requests/" + request.getId() + "/interpretation").session(staffSession)
				.with(csrf())
				.param("reasonSummary", "Orthopedic consult")
				.param("careType", "SPECIALTY")
				.param("specialty", "surgery")
				.param("estimatedMinutes", "45")
				.param("preferredVetId", String.valueOf(vet.getId()))
				.param("cannotInterpret", "false")
				.param("preferredWindows", "MONDAY:09:00-12:00")
				.param("allowedWindows", "2026-09-10:14:00-17:00")
				.param("excludedWindows", "2026-09-11:10:00-11:00"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/requests/" + request.getId()));

		// Read back by value
		List<Interpretation> firstSaveList = this.interpretationRepository
			.findByRequestIdOrderByVersionDesc(request.getId());
		assertThat(firstSaveList).hasSize(1);
		Interpretation v1 = firstSaveList.get(0);
		assertThat(v1.getVersion()).isEqualTo(1);
		assertThat(v1.getProvenance()).isEqualTo(Provenance.STAFF);
		assertThat(v1.getReasonSummary()).isEqualTo("Orthopedic consult");
		assertThat(v1.getCareType()).isEqualTo(CareType.SPECIALTY);
		assertThat(v1.getSpecialty()).isEqualTo("surgery");
		assertThat(v1.getEstimatedMinutes()).isEqualTo(45);
		assertThat(v1.getPreferredVet().getId()).isEqualTo(vet.getId());
		assertThat(v1.isCannotInterpret()).isFalse();

		List<InterpretationWindow> windows1 = v1.getWindows();
		assertThat(windows1).hasSize(3);
		assertThat(windows1).anyMatch(w -> w.getKind() == WindowKind.PREFERRED && w.getDayOfWeek() == DayOfWeek.MONDAY
				&& w.getStartTime().equals(LocalTime.of(9, 0)) && w.getEndTime().equals(LocalTime.of(12, 0)));
		assertThat(windows1)
			.anyMatch(w -> w.getKind() == WindowKind.ALLOWED && LocalDate.of(2026, 9, 10).equals(w.getDateVal())
					&& w.getStartTime().equals(LocalTime.of(14, 0)) && w.getEndTime().equals(LocalTime.of(17, 0)));
		assertThat(windows1)
			.anyMatch(w -> w.getKind() == WindowKind.EXCLUDED && LocalDate.of(2026, 9, 11).equals(w.getDateVal())
					&& w.getStartTime().equals(LocalTime.of(10, 0)) && w.getEndTime().equals(LocalTime.of(11, 0)));

		// Save 2: Second save creates version 2 and leaves earlier row unchanged
		this.mockMvc
			.perform(post("/staff/requests/" + request.getId() + "/interpretation").session(staffSession)
				.with(csrf())
				.param("reasonSummary", "Orthopedic surgery (extended)")
				.param("careType", "SPECIALTY")
				.param("specialty", "surgery")
				.param("estimatedMinutes", "60")
				.param("preferredVetId", String.valueOf(vet.getId()))
				.param("cannotInterpret", "false")
				.param("preferredWindows", "TUESDAY:10:00-12:00"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/requests/" + request.getId()));

		List<Interpretation> secondSaveList = this.interpretationRepository
			.findByRequestIdOrderByVersionDesc(request.getId());
		assertThat(secondSaveList).hasSize(2);

		Interpretation v2 = secondSaveList.get(0);
		Interpretation v1After = secondSaveList.get(1);

		assertThat(v2.getVersion()).isEqualTo(2);
		assertThat(v2.getReasonSummary()).isEqualTo("Orthopedic surgery (extended)");
		assertThat(v2.getEstimatedMinutes()).isEqualTo(60);

		// Earlier row unchanged
		assertThat(v1After.getVersion()).isEqualTo(1);
		assertThat(v1After.getReasonSummary()).isEqualTo("Orthopedic consult");
		assertThat(v1After.getEstimatedMinutes()).isEqualTo(45);
	}

	@Test
	@Tag("AC-89")
	void declinedConsentRequiresStaffInterpretation_AC89() {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().get(0);
		ZonedDateTime now = ZonedDateTime.now(this.clock);

		// Request with declined consent has NO interpretation
		SchedulingRequest request = this.lifecycleService.declineConsent(
				createRequestWithTimestamp(owner, pet, "Checkup", "anytime", now.minusMinutes(10)), "george");

		assertThat(this.interpretationRepository.findByRequestIdOrderByVersionDesc(request.getId())).isEmpty();

		// Suggest is refused
		assertThatThrownBy(() -> this.staffInterpretationService.suggest(request.getId(), "staff"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("complete staff interpretation");

		// Add a complete staff interpretation
		StaffInterpretationForm form = new StaffInterpretationForm();
		form.setReasonSummary("Staff wellness review");
		form.setEstimatedMinutes(30);
		form.setCareType("GENERAL");
		form.setPreferredWindows("MONDAY:09:00-12:00");
		this.staffInterpretationService.saveInterpretation(request.getId(), form, "staff");

		// Now suggest succeeds
		SchedulingRequest suggested = this.staffInterpretationService.suggest(request.getId(), "staff");
		assertThat(suggested.getState()).isEqualTo(RequestState.SUGGESTION_OFFERED);
		assertThat(suggested.hasHold()).isTrue();
	}

	@Test
	@Tag("AC-121")
	void timelineShowsEveryTransitionAndAction_AC121() throws Exception {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().get(0);
		Vet vet = this.vetRepository.findById(1).orElseThrow();
		ZonedDateTime now = ZonedDateTime.now(this.clock);

		// 1. Create request
		SchedulingRequest request = createRequestWithTimestamp(owner, pet, "Annual checkup", "Mondays",
				now.minusMinutes(50));

		// 2. Consent
		request = this.lifecycleService.consent(request, "george");

		// 3. Interpretation usable
		request = this.lifecycleService.interpretationUsable(request, "system");

		// 4. Confirm feasible
		ZonedDateTime heldStart = now.plusDays(1).withHour(9).withMinute(0);
		request = this.lifecycleService.confirmFeasible(request, "george", vet, heldStart, 30);

		// 5. Staff release hold
		request = this.lifecycleService.staffReleaseHold(request, "staff", "schedule conflict with surgery");

		// 6. Staff edit interpretation
		request = this.lifecycleService.staffEditInterpretation(request, "staff");

		// Verify timeline service
		List<SchedulingRequestEvent> timeline = this.staffInterpretationService.getTimeline(request.getId());
		assertThat(timeline).hasSize(6);

		assertThat(timeline.get(0).getFromState()).isNull();
		assertThat(timeline.get(0).getToState()).isEqualTo(RequestState.AWAITING_CONSENT);
		assertThat(timeline.get(0).getAction()).isEqualTo("CREATE_REQUEST");

		assertThat(timeline.get(1).getFromState()).isEqualTo(RequestState.AWAITING_CONSENT);
		assertThat(timeline.get(1).getToState()).isEqualTo(RequestState.INTERPRETING);
		assertThat(timeline.get(1).getAction()).isEqualTo("CONSENT_GRANTED");

		assertThat(timeline.get(2).getFromState()).isEqualTo(RequestState.INTERPRETING);
		assertThat(timeline.get(2).getToState()).isEqualTo(RequestState.INTERPRETED);
		assertThat(timeline.get(2).getAction()).isEqualTo("INTERPRETATION_APPLIED");

		assertThat(timeline.get(3).getFromState()).isEqualTo(RequestState.INTERPRETED);
		assertThat(timeline.get(3).getToState()).isEqualTo(RequestState.SUGGESTION_OFFERED);
		assertThat(timeline.get(3).getAction()).isEqualTo("confirm feasible");

		assertThat(timeline.get(4).getFromState()).isEqualTo(RequestState.SUGGESTION_OFFERED);
		assertThat(timeline.get(4).getToState()).isEqualTo(RequestState.WITH_STAFF);
		assertThat(timeline.get(4).getAction()).isEqualTo("staff release hold");
		assertThat(timeline.get(4).getReason()).isEqualTo("schedule conflict with surgery");
		assertThat(timeline.get(4).getActor()).isEqualTo("staff");

		assertThat(timeline.get(5).getFromState()).isEqualTo(RequestState.WITH_STAFF);
		assertThat(timeline.get(5).getToState()).isEqualTo(RequestState.WITH_STAFF);
		assertThat(timeline.get(5).getAction()).isEqualTo("staff edit interpretation");
		assertThat(timeline.get(5).getActor()).isEqualTo("staff");

		for (SchedulingRequestEvent event : timeline) {
			assertThat(event.getTimestamp()).isNotNull();
		}

		// Verify HTTP rendering
		MockHttpSession staffSession = login("staff", "staff123");
		this.mockMvc.perform(get("/staff/requests/" + request.getId()).session(staffSession))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("CREATE_REQUEST")))
			.andExpect(content().string(containsString("CONSENT_GRANTED")))
			.andExpect(content().string(containsString("INTERPRETATION_APPLIED")))
			.andExpect(content().string(containsString("confirm feasible")))
			.andExpect(content().string(containsString("staff release hold")))
			.andExpect(content().string(containsString("schedule conflict with surgery")))
			.andExpect(content().string(containsString("staff edit interpretation")));
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
