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
import java.time.Duration;
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
import org.springframework.samples.petclinic.scheduling.request.RequestLifecycleService;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEvent;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEventRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.StaffQueueService;
import org.springframework.samples.petclinic.scheduling.request.StaffQueueService.StaffQueueItem;
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
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class StaffQueueTests {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private StaffQueueService staffQueueService;

	@Autowired
	private RequestLifecycleService lifecycleService;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private SchedulingRequestEventRepository eventRepository;

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
	@Tag("AC-84")
	void needsStaffOldestFirstWithTrigger_AC84() throws Exception {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		List<Pet> pets = owner.getPets();
		Pet pet1 = pets.get(0);

		ZonedDateTime now = ZonedDateTime.now(this.clock);

		// 1. Request 1: Declined consent -> WITH_STAFF (created oldest: now - 4 hours)
		SchedulingRequest req1 = createRequestWithTimestamp(owner, pet1, "Checkup 1", "morning", now.minusHours(4));
		req1 = this.lifecycleService.declineConsent(req1, "george");

		// 2. Request 2: Route to staff -> WITH_STAFF (created: now - 3 hours)
		SchedulingRequest req2 = createRequestWithTimestamp(owner, pet1, "Checkup 2", "afternoon", now.minusHours(3));
		req2 = this.lifecycleService.consent(req2, "george");
		req2 = this.lifecycleService.interpretationUsable(req2, "system");
		req2 = this.lifecycleService.routeToStaff(req2, "george", "owner preferred manual staff review");

		// 3. Request 3: Model unavailable -> WITH_STAFF (created: now - 2 hours)
		SchedulingRequest req3 = createRequestWithTimestamp(owner, pet1, "Checkup 3", "anytime", now.minusHours(2));
		req3 = this.lifecycleService.consent(req3, "george");
		req3 = this.lifecycleService.interpretationModelUnavailable(req3, "system", "timeout after retry");

		// 4. Request 4: Confirm no feasible slots -> WITH_STAFF (created: now - 1 hour)
		SchedulingRequest req4 = createRequestWithTimestamp(owner, pet1, "Checkup 4", "weekend", now.minusHours(1));
		req4 = this.lifecycleService.consent(req4, "george");
		req4 = this.lifecycleService.interpretationUsable(req4, "system");
		req4 = this.lifecycleService.confirmNoFeasibleSlots(req4, "system", "no slots available");

		// 5. Request 5: AWAITING_CONSENT (should NOT appear in needs-staff tab)
		createRequestWithTimestamp(owner, pet1, "Checkup 5", "monday", now.minusMinutes(30));

		// 6. Request 6: ACCEPTED (terminal, should NOT appear in needs-staff tab)
		SchedulingRequest req6 = createRequestWithTimestamp(owner, pet1, "Checkup 6", "tuesday", now.minusMinutes(10));
		req6 = this.lifecycleService.consent(req6, "george");
		req6 = this.lifecycleService.interpretationUsable(req6, "system");
		Vet vet1 = this.vetRepository.findById(1).orElseThrow();
		req6 = this.lifecycleService.confirmFeasible(req6, "george", vet1, now.plusDays(1), 30);
		this.lifecycleService.acceptSuggestion(req6, "george");

		// Validate service-level queue retrieval
		List<StaffQueueItem> queue = this.staffQueueService.getNeedsStaffQueue();
		assertThat(queue).hasSize(4);

		// Assert oldest first ordering
		assertThat(queue.get(0).getRequest().getId()).isEqualTo(req1.getId());
		assertThat(queue.get(1).getRequest().getId()).isEqualTo(req2.getId());
		assertThat(queue.get(2).getRequest().getId()).isEqualTo(req3.getId());
		assertThat(queue.get(3).getRequest().getId()).isEqualTo(req4.getId());

		// Assert exact triggers
		assertThat(queue.get(0).getTrigger()).isEqualTo("Declined consent");
		assertThat(queue.get(1).getTrigger()).isEqualTo("Owner routed to staff");
		assertThat(queue.get(2).getTrigger()).isEqualTo("Model unavailable");
		assertThat(queue.get(3).getTrigger()).isEqualTo("No slots available");

		// Assert all are WITH_STAFF
		for (StaffQueueItem item : queue) {
			assertThat(item.getRequest().getState()).isEqualTo(RequestState.WITH_STAFF);
		}

		// Validate HTTP rendering
		MockHttpSession staffSession = login("staff", "staff123");
		MvcResult queuePage = this.mockMvc.perform(get("/staff/queue").session(staffSession))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Declined consent")))
			.andExpect(content().string(containsString("Owner routed to staff")))
			.andExpect(content().string(containsString("Model unavailable")))
			.andExpect(content().string(containsString("No slots available")))
			.andExpect(content().string(containsString("Checkup 1")))
			.andExpect(content().string(containsString("Checkup 2")))
			.andReturn();

		String html = queuePage.getResponse().getContentAsString();
		int previousRowPosition = -1;
		for (StaffQueueItem item : queue) {
			String reason = item.getRequest().getReasonText();
			int rowPosition = html.indexOf(reason);
			assertThat(rowPosition).as("rendered position for %s", reason).isGreaterThan(previousRowPosition);
			assertThat(renderedRow(html, reason)).as("rendered request/trigger pair for %s", reason)
				.contains(item.getTrigger());
			previousRowPosition = rowPosition;
		}
	}

	@Test
	@Tag("AC-85")
	void allOpenContainsEveryNonTerminalStateHoldAndAge_AC85() throws Exception {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().get(0);
		Vet vet = this.vetRepository.findById(1).orElseThrow();
		ZonedDateTime now = ZonedDateTime.now(this.clock);

		// 1. AWAITING_CONSENT (age: 60 min)
		SchedulingRequest r1 = createRequestWithTimestamp(owner, pet, "Open R1", "anytime", now.minusMinutes(60));

		// 2. INTERPRETING (age: 50 min)
		SchedulingRequest r2 = createRequestWithTimestamp(owner, pet, "Open R2", "anytime", now.minusMinutes(50));
		r2 = this.lifecycleService.consent(r2, "george");

		// 3. INTERPRETATION_FAILED (age: 40 min)
		SchedulingRequest r3 = createRequestWithTimestamp(owner, pet, "Open R3", "anytime", now.minusMinutes(40));
		r3 = this.lifecycleService.consent(r3, "george");
		r3 = this.lifecycleService.interpretationFailed(r3, "system", "cannotInterpret");

		// 4. INTERPRETED (age: 30 min)
		SchedulingRequest r4 = createRequestWithTimestamp(owner, pet, "Open R4", "anytime", now.minusMinutes(30));
		r4 = this.lifecycleService.consent(r4, "george");
		r4 = this.lifecycleService.interpretationUsable(r4, "system");

		// 5. SUGGESTION_OFFERED with hold (age: 20 min)
		SchedulingRequest r5 = createRequestWithTimestamp(owner, pet, "Open R5", "anytime", now.minusMinutes(20));
		r5 = this.lifecycleService.consent(r5, "george");
		r5 = this.lifecycleService.interpretationUsable(r5, "system");
		ZonedDateTime heldStart = now.plusDays(2).withHour(10).withMinute(0);
		r5 = this.lifecycleService.confirmFeasible(r5, "george", vet, heldStart, 30);

		// 6. WITH_STAFF (age: 10 min)
		SchedulingRequest r6 = createRequestWithTimestamp(owner, pet, "Open R6", "anytime", now.minusMinutes(10));
		r6 = this.lifecycleService.declineConsent(r6, "george");

		// 7. Terminal: ACCEPTED
		SchedulingRequest r7 = createRequestWithTimestamp(owner, pet, "Terminal R7", "anytime", now.minusMinutes(5));
		r7 = this.lifecycleService.consent(r7, "george");
		r7 = this.lifecycleService.interpretationUsable(r7, "system");
		r7 = this.lifecycleService.confirmFeasible(r7, "george", vet, heldStart.plusHours(1), 30);
		this.lifecycleService.acceptSuggestion(r7, "george");

		// 8. Terminal: ABANDONED
		SchedulingRequest r8 = createRequestWithTimestamp(owner, pet, "Terminal R8", "anytime", now.minusMinutes(2));
		this.lifecycleService.abandon(r8, "george", "not needed");

		// Call service
		List<StaffQueueItem> allOpen = this.staffQueueService.getAllOpenQueue();
		assertThat(allOpen).hasSize(6);

		List<RequestState> states = allOpen.stream().map(item -> item.getRequest().getState()).toList();
		assertThat(states).containsExactlyInAnyOrder(RequestState.AWAITING_CONSENT, RequestState.INTERPRETING,
				RequestState.INTERPRETATION_FAILED, RequestState.INTERPRETED, RequestState.SUGGESTION_OFFERED,
				RequestState.WITH_STAFF);

		// Verify hold representation for SUGGESTION_OFFERED
		StaffQueueItem heldItem = allOpen.stream()
			.filter(i -> i.getRequest().getState() == RequestState.SUGGESTION_OFFERED)
			.findFirst()
			.orElseThrow();
		assertThat(heldItem.getHeldSlot()).contains(vet.getFirstName()).contains("30 min");

		// Verify clock-derived age
		for (StaffQueueItem item : allOpen) {
			Duration expectedAge = Duration.between(item.getRequest().getCreatedAt(), now);
			assertThat(item.getAge()).isCloseTo(expectedAge, Duration.ofSeconds(2));
		}

		// Validate HTTP rendering for All Open tab
		MockHttpSession staffSession = login("staff", "staff123");
		MvcResult allOpenPage = this.mockMvc.perform(get("/staff/queue").param("tab", "all-open").session(staffSession))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("AWAITING_CONSENT")))
			.andExpect(content().string(containsString("INTERPRETING")))
			.andExpect(content().string(containsString("INTERPRETATION_FAILED")))
			.andExpect(content().string(containsString("INTERPRETED")))
			.andExpect(content().string(containsString("SUGGESTION_OFFERED")))
			.andExpect(content().string(containsString("WITH_STAFF")))
			.andReturn();

		String html = allOpenPage.getResponse().getContentAsString();
		for (SchedulingRequest request : List.of(r1, r2, r3, r4, r5, r6)) {
			String row = renderedRow(html, request.getReasonText());
			assertThat(row).as("all-open row for %s", request.getReasonText())
				.contains(request.getState().name())
				.contains(formattedAge(Duration.between(request.getCreatedAt(), now)));
			if (request.getId().equals(r5.getId())) {
				assertThat(row).contains(heldItem.getHeldSlot())
					.contains("/staff/requests/" + request.getId() + "/release-hold")
					.contains("Release hold");
			}
			else {
				assertThat(row).contains(">-</td>").doesNotContain("release-hold");
			}
		}
		assertThat(html).doesNotContain("Terminal R7", "Terminal R8");
		assertThat(countOccurrences(html, "/release-hold")).isEqualTo(1);
	}

	@Test
	@Tag("AC-86")
	void releaseHoldMovesToStaffAndClearsTuple_AC86() {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().get(0);
		Vet vet = this.vetRepository.findById(1).orElseThrow();
		ZonedDateTime now = ZonedDateTime.now(this.clock);

		SchedulingRequest request = createRequestWithTimestamp(owner, pet, "Release hold test", "anytime",
				now.minusMinutes(30));
		request = this.lifecycleService.consent(request, "george");
		request = this.lifecycleService.interpretationUsable(request, "system");
		ZonedDateTime heldStart = now.plusDays(1).withHour(11).withMinute(0);
		request = this.lifecycleService.confirmFeasible(request, "george", vet, heldStart, 30);

		assertThat(request.getState()).isEqualTo(RequestState.SUGGESTION_OFFERED);
		assertThat(request.hasHold()).isTrue();

		// Release hold via StaffQueueService
		SchedulingRequest released = this.staffQueueService.releaseHold(request.getId(), "staff",
				"schedule conflict with surgery");

		// Verify state and null hold fields
		assertThat(released.getState()).isEqualTo(RequestState.WITH_STAFF);
		assertThat(released.getHeldVet()).isNull();
		assertThat(released.getHeldStart()).isNull();
		assertThat(released.getHeldDuration()).isNull();
		assertThat(released.hasHold()).isFalse();

		// Verify persisted state
		SchedulingRequest reloaded = this.requestRepository.findById(request.getId()).orElseThrow();
		assertThat(reloaded.getState()).isEqualTo(RequestState.WITH_STAFF);
		assertThat(reloaded.getHeldVet()).isNull();
		assertThat(reloaded.getHeldStart()).isNull();
		assertThat(reloaded.getHeldDuration()).isNull();

		// Verify audit event
		List<SchedulingRequestEvent> events = this.eventRepository.findByRequestIdOrderByTimestampAsc(request.getId());
		SchedulingRequestEvent lastEvent = events.get(events.size() - 1);
		assertThat(lastEvent.getFromState()).isEqualTo(RequestState.SUGGESTION_OFFERED);
		assertThat(lastEvent.getToState()).isEqualTo(RequestState.WITH_STAFF);
		assertThat(lastEvent.getAction()).isEqualTo("staff release hold");
		assertThat(lastEvent.getReason()).isEqualTo("schedule conflict with surgery");
		assertThat(lastEvent.getActor()).isEqualTo("staff");
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
		event.setActor("test");
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

	private static String renderedRow(String html, String uniqueText) {
		int textPosition = html.indexOf(uniqueText);
		assertThat(textPosition).as("rendered text %s", uniqueText).isNotNegative();
		int rowStart = html.lastIndexOf("<tr", textPosition);
		int rowEnd = html.indexOf("</tr>", textPosition);
		assertThat(rowStart).as("row start for %s", uniqueText).isNotNegative();
		assertThat(rowEnd).as("row end for %s", uniqueText).isGreaterThan(textPosition);
		return html.substring(rowStart, rowEnd + "</tr>".length());
	}

	private static String formattedAge(Duration age) {
		long totalMinutes = Math.max(0, age.toMinutes());
		long hours = totalMinutes / 60;
		long minutes = totalMinutes % 60;
		return hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
	}

	private static int countOccurrences(String text, String needle) {
		return (text.length() - text.replace(needle, "").length()) / needle.length();
	}

}
