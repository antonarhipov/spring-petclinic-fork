package org.springframework.samples.petclinic.scheduling.request;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.account.AccountRepository;
import org.springframework.samples.petclinic.account.Role;
import org.springframework.samples.petclinic.appointment.Appointment;
import org.springframework.samples.petclinic.appointment.AppointmentChangeEvent;
import org.springframework.samples.petclinic.appointment.AppointmentChangeEventRepository;
import org.springframework.samples.petclinic.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.availability.AvailabilityAdministrationService;
import org.springframework.samples.petclinic.availability.RecurringShift;
import org.springframework.samples.petclinic.availability.RecurringShiftRepository;
import org.springframework.samples.petclinic.scheduling.interpretation.CatalogChoice;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationCandidate;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationClient;
import org.springframework.samples.petclinic.scheduling.interpretation.Urgency;
import org.springframework.samples.petclinic.scheduling.interpretation.WindowCandidate;
import org.springframework.samples.petclinic.scheduling.job.BackgroundJobRepository;
import org.springframework.samples.petclinic.scheduling.job.BackgroundJobWorker;
import org.springframework.samples.petclinic.scheduling.offer.Offer;
import org.springframework.samples.petclinic.scheduling.offer.OfferRepository;
import org.springframework.samples.petclinic.scheduling.offer.OfferState;
import org.springframework.samples.petclinic.security.PetClinicPrincipal;
import org.springframework.samples.petclinic.security.SecurityTestPrincipals;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * End-to-end synthetic journey test for US1: Owner Books a Routine Appointment. Covers:
 * Prose submission with AI consent -> interpretation processing -> structured review ->
 * Timefold matching -> 10-minute held offer -> owner acceptance -> confirmed appointment.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "petclinic.jobs.enabled=false")
@Transactional
class OwnerRoutineSchedulingJourneyTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private InterpretationClient interpretationClient;

	@Autowired
	private BackgroundJobWorker backgroundJobWorker;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private ActiveSchedulingRequestRepository activeRequestRepository;

	@Autowired
	private WorkflowRevisionRepository workflowRevisionRepository;

	@Autowired
	private BackgroundJobRepository jobRepository;

	@Autowired
	private OfferRepository offerRepository;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private AppointmentChangeEventRepository changeEventRepository;

	@Autowired
	private RecurringShiftRepository recurringShiftRepository;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private Clock clock;

	private PetClinicPrincipal george;

	@BeforeEach
	void setUp() {
		cleanupData();

		Account account = this.accountRepository.findByUsername("george").orElseGet(() -> {
			Account newAccount = new Account();
			newAccount.setUsername("george");
			newAccount.setPasswordHash(this.passwordEncoder.encode("george123"));
			newAccount.setRole(Role.OWNER);
			newAccount.setOwnerId(1);
			newAccount.setEnabled(true);
			newAccount.setPasswordChangeRequired(false);
			newAccount.setSessionVersion(0L);
			return this.accountRepository.save(newAccount);
		});
		this.george = SecurityTestPrincipals.owner(account.getId(), account.getUsername(), account.getOwnerId(),
				account.getSessionVersion(), false);

		// Ensure Vet 1 (James Carter) has recurring shifts across all weekdays
		for (DayOfWeek dow : DayOfWeek.values()) {
			if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
				RecurringShift shift = new RecurringShift(1, dow, LocalTime.of(8, 0), LocalTime.of(17, 0));
				this.recurringShiftRepository.save(shift);
			}
		}
	}

	private void cleanupData() {
		this.offerRepository.deleteAll();
		this.jobRepository.deleteAll();
		this.activeRequestRepository.deleteAll();
		for (SchedulingRequest req : this.requestRepository.findAll()) {
			req.setCurrentWorkflowRevision(null);
			req.setCurrentTextRevision(null);
			this.requestRepository.save(req);
		}
		this.workflowRevisionRepository.deleteAll();
		this.requestRepository.deleteAll();
		this.changeEventRepository.deleteAll();
		this.appointmentRepository.deleteAll();
		this.recurringShiftRepository.deleteAll();
	}

	@Test
	@DisplayName("US1 Routine Journey: Submission -> Interpretation -> Review -> Timefold Match -> Offer -> Acceptance")
	void ownerRoutineSchedulingJourney_endToEnd_successfulBooking() throws Exception {
		LocalDate targetDate = LocalDate.now().plusDays(2);
		LocalTime startTime = LocalTime.of(9, 0);
		LocalTime endTime = LocalTime.of(12, 0);

		WindowCandidate window = new WindowCandidate(WindowClassification.ALLOWED, WindowShape.ONE_OFF, targetDate,
				null, null, null, startTime, endTime, "Morning appointment", null);
		InterpretationCandidate aiCandidate = new InterpretationCandidate("1", "Annual routine health checkup",
				Urgency.ROUTINE, List.of(), 30, new CatalogChoice(1, "James Carter"), null, List.of(window), List.of(),
				List.of());

		given(this.interpretationClient.interpret(any())).willReturn(aiCandidate);

		// 1. Owner views new request form
		this.mockMvc.perform(get("/owner/requests/new").with(user(this.george)))
			.andExpect(status().isOk())
			.andExpect(view().name("owner/requests/new"));

		// 2. Owner submits prose request with AI consent
		MvcResult postResult = this.mockMvc
			.perform(post("/owner/requests").with(user(this.george))
				.with(csrf())
				.param("petId", "1")
				.param("prose", "Leo needs an annual routine health checkup with Dr. Carter on " + targetDate)
				.param("aiConsent", "true"))
			.andExpect(status().is3xxRedirection())
			.andReturn();

		String redirectUrl = postResult.getResponse().getRedirectedUrl();
		assertThat(redirectUrl).startsWith("/owner/requests/");
		Long requestId = Long.parseLong(redirectUrl.substring("/owner/requests/".length()));

		// 3. Status is initial AWAITING_INTERPRETATION
		this.mockMvc.perform(get("/owner/requests/{requestId}/status", requestId).with(user(this.george)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.requestId").value(requestId))
			.andExpect(jsonPath("$.displayState").value("INTERPRETING_REQUEST"));

		// 4. Background worker executes Interpretation Job
		boolean processedInterpretation = this.backgroundJobWorker.processNextJob();
		assertThat(processedInterpretation).isTrue();

		// 5. Status advances to AWAITING_REVIEW with primaryActionUrl
		this.mockMvc.perform(get("/owner/requests/{requestId}/status", requestId).with(user(this.george)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.displayState").value("REVIEW_INTERPRETATION"))
			.andExpect(jsonPath("$.primaryAction.url").value("/owner/requests/" + requestId + "/interpretation"));

		// 6. Owner opens interpretation review page
		this.mockMvc.perform(get("/owner/requests/{requestId}/interpretation", requestId).with(user(this.george)))
			.andExpect(status().isOk())
			.andExpect(view().name("owner/requests/interpretation"));

		// 7. Owner confirms interpretation
		this.mockMvc
			.perform(post("/owner/requests/{requestId}/interpretation/confirm", requestId).with(user(this.george))
				.with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/owner/requests/" + requestId));

		// 8. Background worker executes Matching Job (Timefold)
		boolean processedMatching = this.backgroundJobWorker.processNextJob();
		assertThat(processedMatching).isTrue();

		// 9. Status advances to OFFERED with offer action URL
		MvcResult statusResult = this.mockMvc
			.perform(get("/owner/requests/{requestId}/status", requestId).with(user(this.george)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.displayState").value("APPOINTMENT_OFFERED"))
			.andReturn();

		List<Offer> offers = this.offerRepository.findAll();
		assertThat(offers).hasSize(1);
		Offer offer = offers.get(0);
		assertThat(offer.getState()).isEqualTo(OfferState.HELD);
		assertThat(offer.getVetId()).isEqualTo(1);

		// 10. Owner reviews offer page
		this.mockMvc
			.perform(get("/owner/requests/{requestId}/offers/{offerId}", requestId, offer.getId())
				.with(user(this.george)))
			.andExpect(status().isOk())
			.andExpect(view().name("owner/requests/offer"));

		// 11. Owner accepts offer
		this.mockMvc
			.perform(post("/owner/requests/{requestId}/offers/{offerId}/accept", requestId, offer.getId())
				.with(user(this.george))
				.with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/owner/requests/" + requestId));

		// 12. Status is now CONFIRMED
		this.mockMvc.perform(get("/owner/requests/{requestId}/status", requestId).with(user(this.george)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.displayState").value("CONFIRMED"))
			.andExpect(jsonPath("$.primaryAction.url").isString());

		// 13. Verify Database state
		List<Appointment> appointments = this.appointmentRepository.findByOwnerIdOrderByStartAtDesc(1);
		assertThat(appointments).hasSize(1);
		Appointment confirmedAppointment = appointments.get(0);
		assertThat(confirmedAppointment.getOwnerId()).isEqualTo(1);
		assertThat(confirmedAppointment.getPetId()).isEqualTo(1);
		assertThat(confirmedAppointment.getVetId()).isEqualTo(1);
		assertThat(confirmedAppointment.getOriginatingRequestId()).isEqualTo(requestId);
		assertThat(confirmedAppointment.getOriginatingOfferId()).isEqualTo(offer.getId());

		List<AppointmentChangeEvent> changeEvents = this.changeEventRepository
			.findByAppointmentIdOrderByOccurredAtAsc(confirmedAppointment.getId());
		assertThat(changeEvents).hasSize(1);
		assertThat(changeEvents.get(0).getEventType()).isEqualTo("OFFER_ACCEPTED");
		assertThat(changeEvents.get(0).getActorRole()).isEqualTo("ROLE_OWNER");

		Offer finalOffer = this.offerRepository.findById(offer.getId()).orElseThrow();
		assertThat(finalOffer.getState()).isEqualTo(OfferState.ACCEPTED);
		assertThat(finalOffer.getAppointmentId()).isEqualTo(confirmedAppointment.getId());

		assertThat(this.activeRequestRepository.findById(1)).isEmpty();
	}

	@Test
	@DisplayName("Declined AI consent routes request immediately to staff queue without calling AI")
	void ownerRoutineSchedulingJourney_declinedConsent_routesToStaffQueue() throws Exception {
		// 1. Owner submits prose request with AI consent = false
		MvcResult postResult = this.mockMvc
			.perform(post("/owner/requests").with(user(this.george))
				.with(csrf())
				.param("petId", "1")
				.param("prose", "Leo has a minor ear itch, please help schedule a visit")
				.param("aiConsent", "false"))
			.andExpect(status().is3xxRedirection())
			.andReturn();

		String redirectUrl = postResult.getResponse().getRedirectedUrl();
		Long requestId = Long.parseLong(redirectUrl.substring("/owner/requests/".length()));

		// 2. Status is immediately STAFF_HANDLING
		this.mockMvc.perform(get("/owner/requests/{requestId}/status", requestId).with(user(this.george)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.requestId").value(requestId))
			.andExpect(jsonPath("$.displayState").value("WITH_CLINIC_STAFF"));

		// 3. Verify AI client was never called
		verifyNoInteractions(this.interpretationClient);
	}

}
