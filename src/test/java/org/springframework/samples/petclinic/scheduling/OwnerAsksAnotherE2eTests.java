/* Copyright 2012-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package org.springframework.samples.petclinic.scheduling;

import java.time.LocalDate;
import java.time.LocalTime;
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
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentLifecycleService;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.interpretation.AvailabilityWindow;
import org.springframework.samples.petclinic.scheduling.interpretation.CareType;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationResult;
import org.springframework.samples.petclinic.scheduling.interpretation.StubRequestInterpreter;
import org.springframework.samples.petclinic.scheduling.request.RejectionScope;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEvent;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEventRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.SuggestionRejection;
import org.springframework.samples.petclinic.vet.Vet;
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

/** Non-transactional UC-2 HTTP scenarios through the real security filter chain. */
@SpringBootTest(properties = "scheduling.interpretation.executor=synchronous")
@Import(TestClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OwnerAsksAnotherE2eTests {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private SchedulingRequestEventRepository eventRepository;

	@Autowired
	private AppointmentLifecycleService appointmentLifecycleService;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private StubRequestInterpreter interpreter;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
		this.interpreter.clear();
	}

	@Test
	@Tag("AC-138")
	void ownerAsksForAnotherOptionSteps1And2() throws Exception {
		OfferedRequest offered = offeredRequest("george", "george123", 1, "Recurring checkup", "Tuesday afternoon");

		SchedulingRequest afterTime = askAnother(offered, RejectionScope.NOT_THIS_TIME);
		SchedulingRequest afterDay = askAnother(offered, RejectionScope.NOT_THIS_DAY);
		SchedulingRequest afterVet = askAnother(offered, RejectionScope.NOT_THIS_VET);

		assertThat(heldTuple(afterTime)).isNotEqualTo(offered.initialTuple());
		assertThat(afterDay.getHeldStart().toLocalDate()).isNotEqualTo(afterTime.getHeldStart().toLocalDate());
		assertThat(afterVet.getHeldVet().getId()).isNotEqualTo(afterDay.getHeldVet().getId());
		assertThat(rejectionEvents(offered.id())).extracting(SchedulingRequestEvent::getReason)
			.containsExactly("NOT_THIS_TIME", "NOT_THIS_DAY", "NOT_THIS_VET");
	}

	@Test
	void exhaustedCandidatesRouteToStaff() throws Exception {
		this.interpreter.setNextResult(new InterpretationResult(
				"One short window", 30, CareType.GENERAL, null, null, false, List.of(AvailabilityWindow
					.preferred(LocalDate.of(2026, 9, 8), LocalTime.of(9, 0), LocalTime.of(9, 30), "one slot")),
				"{\"window\":\"one-slot\"}", "stub-model", "v1"));
		OfferedRequest offered = offeredRequest("george", "george123", 1, "One short window", "One slot only");
		String rejectedTuple = offered.initialTuple();

		this.mockMvc
			.perform(post(offered.location() + "/another").session(offered.session())
				.with(csrf())
				.param("scope", RejectionScope.NOT_THIS_DAY.name()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl(offered.location()));

		SchedulingRequest exhausted = reload(offered.id());
		assertThat(exhausted.getState()).isEqualTo(RequestState.WITH_STAFF);
		assertThat(exhausted.hasHold()).isFalse();
		assertThat(rejectionEvents(offered.id())).singleElement().satisfies(event -> {
			SuggestionRejection rejection = SuggestionRejection.fromEvent(event).orElseThrow();
			assertThat(rejection.scope()).isEqualTo(RejectionScope.NOT_THIS_DAY);
			assertThat(rejection.vetId() + "@" + rejection.start()).isEqualTo(rejectedTuple);
		});
		this.mockMvc.perform(get(offered.location()).session(offered.session()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("WITH_STAFF")))
			.andExpect(content().string(containsString("You have ruled out:")));
	}

	@Test
	@Tag("AC-62")
	void reopeningInvalidHoldReoffersOrHandsOff() throws Exception {
		OfferedRequest offered = offeredRequest("george", "george123", 1, "Reopen invalid hold", "Tuesday afternoon");
		SchedulingRequest beforeConflict = reload(offered.id());
		String originalTuple = heldTuple(beforeConflict);
		Vet heldVet = beforeConflict.getHeldVet();
		Owner otherOwner = this.ownerRepository.findById(2).orElseThrow();
		Pet otherPet = otherOwner.getPets().stream().findFirst().orElseThrow();
		Appointment conflict = this.appointmentLifecycleService.bookAppointment(otherPet, heldVet,
				beforeConflict.getHeldStart(), beforeConflict.getHeldDuration(), "Conflicting booking", null, "staff");

		this.mockMvc.perform(get(offered.location()).session(offered.session()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("That appointment slot is no longer available.")));

		SchedulingRequest recovered = reload(offered.id());
		assertThat(recovered.getState()).isIn(RequestState.SUGGESTION_OFFERED, RequestState.WITH_STAFF);
		assertThat(this.appointmentRepository.findAll()).extracting(Appointment::getId)
			.containsExactly(conflict.getId());
		if (recovered.getState() == RequestState.SUGGESTION_OFFERED) {
			assertThat(heldTuple(recovered)).isNotEqualTo(originalTuple);
		}
		else {
			assertThat(recovered.hasHold()).isFalse();
		}
	}

	private SchedulingRequest askAnother(OfferedRequest offered, RejectionScope scope) throws Exception {
		SchedulingRequest before = reload(offered.id());
		String rejectedTuple = heldTuple(before);
		this.mockMvc
			.perform(post(offered.location() + "/another").session(offered.session())
				.with(csrf())
				.param("scope", scope.name()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl(offered.location()));

		SchedulingRequest after = reload(offered.id());
		assertThat(after.getState()).isEqualTo(RequestState.SUGGESTION_OFFERED);
		assertThat(after.hasHold()).isTrue();
		assertThat(heldTuple(after)).isNotEqualTo(rejectedTuple);
		SchedulingRequestEvent event = rejectionEvents(offered.id()).getLast();
		SuggestionRejection rejection = SuggestionRejection.fromEvent(event).orElseThrow();
		assertThat(rejection.scope()).isEqualTo(scope);
		assertThat(rejection.vetId() + "@" + rejection.start()).isEqualTo(rejectedTuple);
		this.mockMvc.perform(get(offered.location()).session(offered.session()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Suggested appointment")))
			.andExpect(content().string(containsString(scope.name())))
			.andExpect(content().string(containsString(after.getHeldStart().toString())));
		return after;
	}

	private OfferedRequest offeredRequest(String username, String password, int ownerId, String reason,
			String availability) throws Exception {
		MockHttpSession session = login(username, password);
		Pet pet = availablePet(ownerId);
		MvcResult created = this.mockMvc
			.perform(post("/my/requests").session(session)
				.with(csrf())
				.param("petId", pet.getId().toString())
				.param("reasonText", reason)
				.param("availabilityText", availability))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		String location = created.getResponse().getRedirectedUrl();
		assertThat(location).startsWith("/my/requests/");
		Integer requestId = Integer.valueOf(location.substring(location.lastIndexOf('/') + 1));
		assertThat(reload(requestId).getState()).isEqualTo(RequestState.AWAITING_CONSENT);

		this.mockMvc.perform(post(location + "/consent").session(session).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl(location));
		assertThat(reload(requestId).getState()).isEqualTo(RequestState.INTERPRETED);
		this.mockMvc.perform(post(location + "/confirm").session(session).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl(location));
		SchedulingRequest offered = reload(requestId);
		assertThat(offered.getState()).isEqualTo(RequestState.SUGGESTION_OFFERED);
		assertThat(offered.hasHold()).isTrue();
		return new OfferedRequest(session, requestId, location, heldTuple(offered));
	}

	private List<SchedulingRequestEvent> rejectionEvents(Integer requestId) {
		return this.eventRepository.findByRequestIdOrderByTimestampAsc(requestId)
			.stream()
			.filter(event -> SuggestionRejection.EVENT_ACTION.equals(event.getAction()))
			.toList();
	}

	private Pet availablePet(int ownerId) {
		return this.ownerRepository.findById(ownerId)
			.orElseThrow()
			.getPets()
			.stream()
			.filter(pet -> this.requestRepository.findByActivePetId(pet.getId()).isEmpty())
			.findFirst()
			.orElseThrow();
	}

	private MockHttpSession login(String username, String password) throws Exception {
		MvcResult result = this.mockMvc.perform(formLogin().user(username).password(password))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/my/appointments"))
			.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private SchedulingRequest reload(Integer requestId) {
		return this.requestRepository.findById(requestId).orElseThrow();
	}

	private String heldTuple(SchedulingRequest request) {
		return request.getHeldVet().getId() + "@" + request.getHeldStart();
	}

	private record OfferedRequest(MockHttpSession session, Integer id, String location, String initialTuple) {
	}

}
