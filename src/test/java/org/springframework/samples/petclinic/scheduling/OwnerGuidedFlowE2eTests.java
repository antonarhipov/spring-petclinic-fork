/* Copyright 2012-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package org.springframework.samples.petclinic.scheduling;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.interpretation.AvailabilityWindow;
import org.springframework.samples.petclinic.scheduling.interpretation.CareType;
import org.springframework.samples.petclinic.scheduling.interpretation.Interpretation;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationRepository;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationResult;
import org.springframework.samples.petclinic.scheduling.interpretation.StubRequestInterpreter;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEvent;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEventRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Non-transactional UC-1 HTTP scenarios through the real security filter chain. */
@SpringBootTest(properties = "scheduling.interpretation.executor=synchronous")
@Import(TestClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OwnerGuidedFlowE2eTests {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private SchedulingRequestEventRepository eventRepository;

	@Autowired
	private InterpretationRepository interpretationRepository;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private AppointmentLifecycleService appointmentLifecycleService;

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
	void ownerGuidedFlowSteps1Through8() throws Exception {
		MockHttpSession session = login("george", "george123");
		Pet pet = availablePet(1);
		this.mockMvc.perform(get("/my/requests/new").session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(pet.getName())))
			.andExpect(content().string(containsString("Call 555-0199.")));

		StartedRequest started = startRequest(session, pet, "Annual wellness checkup and vaccinations",
				"Monday morning preferred");
		assertState(started.id(), RequestState.AWAITING_CONSENT);
		this.mockMvc.perform(get(started.location()).session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(pet.getName())))
			.andExpect(content().string(containsString("Annual wellness checkup and vaccinations")))
			.andExpect(content().string(containsString("Grant consent")));

		this.mockMvc.perform(post(started.location() + "/consent").session(session).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl(started.location()));
		assertState(started.id(), RequestState.INTERPRETED);
		Interpretation interpretation = this.interpretationRepository.findTopByRequestIdOrderByVersionDesc(started.id())
			.orElseThrow();
		assertThat(interpretation.getReasonSummary()).isEqualTo("Annual wellness checkup and vaccinations");
		assertThat(this.eventRepository.findByRequestIdOrderByTimestampAsc(started.id()))
			.extracting(SchedulingRequestEvent::getAction)
			.containsSubsequence("CONSENT_GRANTED", "INTERPRETATION_APPLIED");
		this.mockMvc.perform(get(started.location()).session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(interpretation.getReasonSummary())))
			.andExpect(content().string(containsString("Confirm interpretation")));

		this.mockMvc.perform(post(started.location() + "/confirm").session(session).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl(started.location()));
		SchedulingRequest offered = reload(started.id());
		assertThat(offered.getState()).isEqualTo(RequestState.SUGGESTION_OFFERED);
		assertThat(offered.hasHold()).isTrue();
		assertThat(offered.getHeldVet()).isNotNull();
		assertThat(offered.getHeldStart()).isNotNull();
		this.mockMvc.perform(get(started.location()).session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Suggested appointment")))
			.andExpect(content().string(containsString("Accept suggestion")));

		this.mockMvc.perform(post(started.location() + "/accept").session(session).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/my/appointments"));
		assertState(started.id(), RequestState.ACCEPTED);
		Appointment appointment = this.appointmentRepository.findByPetId(pet.getId())
			.stream()
			.filter(candidate -> candidate.getStatus() == AppointmentStatus.CONFIRMED)
			.findFirst()
			.orElseThrow();
		assertThat(appointment.getRequest().getId()).isEqualTo(started.id());
		this.mockMvc.perform(get("/my/appointments").session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(pet.getName())))
			.andExpect(content().string(containsString("CONFIRMED")));
	}

	@Test
	@Tag("AC-25")
	void declineConsentRoutesWithoutAi() throws Exception {
		MockHttpSession session = login("george", "george123");
		StartedRequest started = startRequest(session, availablePet(1), "No model consent", "Tuesday morning");
		assertThat(this.interpretationRepository.findByRequestIdOrderByVersionDesc(started.id())).isEmpty();

		this.mockMvc.perform(post(started.location() + "/decline").session(session).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl(started.location()));

		assertState(started.id(), RequestState.WITH_STAFF);
		assertThat(this.interpretationRepository.findByRequestIdOrderByVersionDesc(started.id())).isEmpty();
		assertThat(this.eventRepository.findByRequestIdOrderByTimestampAsc(started.id()))
			.extracting(SchedulingRequestEvent::getAction)
			.containsExactly("CREATE_REQUEST", "decline consent");
		this.mockMvc.perform(get(started.location()).session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("WITH_STAFF")));
	}

	@Test
	void otherSpecialtyAndNoSlotsRouteToStaff() throws Exception {
		this.interpreter.setNextResult(new InterpretationResult("Oncology referral", 30, CareType.SPECIALTY,
				"OTHER: oncology", null, false, List.of(AvailabilityWindow.preferredDayOfWeek(DayOfWeek.TUESDAY,
						LocalTime.of(9, 0), LocalTime.of(12, 0), "Tuesday morning")),
				"{\"specialty\":\"oncology\"}", "stub-model", "v1"));
		MockHttpSession george = login("george", "george123");
		StartedRequest other = startRequest(george, availablePet(1), "Needs oncology", "Tuesday morning");
		consent(other);
		assertState(other.id(), RequestState.WITH_STAFF);
		assertThat(this.interpretationRepository.findTopByRequestIdOrderByVersionDesc(other.id())).get()
			.extracting(Interpretation::getSpecialty)
			.isEqualTo("OTHER: oncology");

		LocalDate closedSunday = LocalDate.of(2026, 9, 13);
		this.interpreter.setNextResult(new InterpretationResult(
				"Sunday only", 30, CareType.GENERAL, null, null, false, List.of(AvailabilityWindow
					.preferred(closedSunday, LocalTime.of(10, 0), LocalTime.of(11, 0), "closed Sunday")),
				"{\"window\":\"Sunday\"}", "stub-model", "v1"));
		MockHttpSession betty = login("betty", "betty123");
		StartedRequest noSlots = startRequest(betty, availablePet(2), "Sunday visit", "Sunday only");
		consent(noSlots);
		assertState(noSlots.id(), RequestState.INTERPRETED);
		this.mockMvc.perform(post(noSlots.location() + "/confirm").session(betty).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl(noSlots.location()));
		SchedulingRequest routed = reload(noSlots.id());
		assertThat(routed.getState()).isEqualTo(RequestState.WITH_STAFF);
		assertThat(routed.hasHold()).isFalse();
	}

	@Test
	@Tag("AC-64")
	void lostHoldReoffers() throws Exception {
		MockHttpSession session = login("george", "george123");
		StartedRequest started = startRequest(session, availablePet(1), "Lost hold request", "Tuesday afternoon");
		consent(started);
		this.mockMvc.perform(post(started.location() + "/confirm").session(session).with(csrf()))
			.andExpect(status().is3xxRedirection());
		SchedulingRequest offered = reload(started.id());
		assertThat(offered.getState()).isEqualTo(RequestState.SUGGESTION_OFFERED);
		Vet heldVet = offered.getHeldVet();
		var originalStart = offered.getHeldStart();
		String originalTuple = heldVet.getId() + "@" + originalStart;
		Owner otherOwner = this.ownerRepository.findById(2).orElseThrow();
		Pet otherPet = otherOwner.getPets().stream().findFirst().orElseThrow();
		Appointment conflict = this.appointmentLifecycleService.bookAppointment(otherPet, heldVet, originalStart,
				offered.getHeldDuration(), "Conflicting booking", null, "staff");

		this.mockMvc.perform(post(started.location() + "/accept").session(session).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl(started.location()))
			.andExpect(flash().attribute("holdUnavailable", true));
		SchedulingRequest recovered = reload(started.id());
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

	@Test
	@Tag("AC-141")
	void concurrentConfirmAndSecondRequestHaveOneWinner() throws Exception {
		StartedRequest george = interpretedRequest("george", "george123", 1, "Concurrent George");
		StartedRequest betty = interpretedRequest("betty", "betty123", 2, "Concurrent Betty");

		List<MvcResult> confirmations = race(
				() -> post(george.location() + "/confirm").session(george.session()).with(csrf()),
				() -> post(betty.location() + "/confirm").session(betty.session()).with(csrf()));
		assertThat(confirmations).allSatisfy(result -> assertThat(result.getResponse().getStatus()).isEqualTo(302));
		List<SchedulingRequest> confirmed = List.of(reload(george.id()), reload(betty.id()));
		assertThat(confirmed).filteredOn(SchedulingRequest::hasHold).isNotEmpty();
		assertThat(confirmed).filteredOn(SchedulingRequest::hasHold)
			.extracting(this::heldTuple)
			.doesNotHaveDuplicates();

		this.mockMvc.perform(post(george.location() + "/abandon").session(george.session()).with(csrf()))
			.andExpect(status().is3xxRedirection());
		assertState(george.id(), RequestState.ABANDONED);
		Pet secondPet = george.pet();
		MockHttpSession georgeA = login("george", "george123");
		MockHttpSession georgeB = login("george", "george123");
		List<MvcResult> creations = race(
				() -> post("/my/requests").session(georgeA)
					.with(csrf())
					.param("petId", secondPet.getId().toString())
					.param("reasonText", "Concurrent first")
					.param("availabilityText", "Tuesday morning"),
				() -> post("/my/requests").session(georgeB)
					.with(csrf())
					.param("petId", secondPet.getId().toString())
					.param("reasonText", "Concurrent second")
					.param("availabilityText", "Tuesday morning"));
		assertThat(creations).extracting(result -> result.getResponse().getRedirectedUrl())
			.filteredOn(location -> location != null && location.startsWith("/my/requests/")
					&& !location.equals("/my/requests/new"))
			.hasSize(1);
		assertThat(creations).extracting(result -> result.getResponse().getRedirectedUrl())
			.contains("/my/requests/new");
		assertThat(this.requestRepository.findAll())
			.filteredOn(request -> secondPet.getId().equals(request.getActivePetId()))
			.singleElement();
	}

	private StartedRequest interpretedRequest(String username, String password, int ownerId, String reason)
			throws Exception {
		MockHttpSession session = login(username, password);
		StartedRequest started = startRequest(session, availablePet(ownerId), reason, "Tuesday afternoon");
		consent(started);
		assertState(started.id(), RequestState.INTERPRETED);
		return started;
	}

	private void consent(StartedRequest started) throws Exception {
		this.mockMvc.perform(post(started.location() + "/consent").session(started.session()).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl(started.location()));
	}

	private StartedRequest startRequest(MockHttpSession session, Pet pet, String reason, String availability)
			throws Exception {
		MvcResult created = this.mockMvc
			.perform(post("/my/requests").session(session)
				.with(csrf())
				.param("petId", pet.getId().toString())
				.param("reasonText", reason)
				.param("availabilityText", availability))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		String location = created.getResponse().getRedirectedUrl();
		assertThat(location).startsWith("/my/requests/").isNotEqualTo("/my/requests/new");
		return new StartedRequest(session, pet, Integer.valueOf(location.substring(location.lastIndexOf('/') + 1)),
				location);
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

	private void assertState(Integer requestId, RequestState expected) {
		assertThat(reload(requestId).getState()).isEqualTo(expected);
	}

	private String heldTuple(SchedulingRequest request) {
		return request.getHeldVet().getId() + "@" + request.getHeldStart();
	}

	private List<MvcResult> race(RequestBuilderFactory firstFactory, RequestBuilderFactory secondFactory)
			throws Exception {
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<MvcResult> first = executor.submit(() -> performAfterBarrier(firstFactory, ready, start));
			Future<MvcResult> second = executor.submit(() -> performAfterBarrier(secondFactory, ready, start));
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
		}
		finally {
			executor.shutdownNow();
		}
	}

	private MvcResult performAfterBarrier(RequestBuilderFactory factory, CountDownLatch ready, CountDownLatch start)
			throws Exception {
		ready.countDown();
		if (!start.await(10, TimeUnit.SECONDS)) {
			throw new IllegalStateException("HTTP race did not start");
		}
		return this.mockMvc.perform(factory.create()).andReturn();
	}

	private record StartedRequest(MockHttpSession session, Pet pet, Integer id, String location) {
	}

	@FunctionalInterface
	private interface RequestBuilderFactory {

		org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder create();

	}

}
