package org.springframework.samples.petclinic.scheduling.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.account.AccountRepository;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.appointment.Offer;
import org.springframework.samples.petclinic.scheduling.appointment.OfferRepository;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationPort;
import org.springframework.samples.petclinic.scheduling.matching.CandidateSlot;
import org.springframework.samples.petclinic.scheduling.matching.SlotScorePolicy;
import org.springframework.samples.petclinic.scheduling.matching.SlotSelectionResult;
import org.springframework.samples.petclinic.scheduling.matching.SlotSelectionSnapshot;
import org.springframework.samples.petclinic.scheduling.matching.TimefoldSlotSolver;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SmartSchedulingEndToEndTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AccountRepository accounts;

	@Autowired
	SchedulingRequestRepository requests;

	@Autowired
	OfferRepository offers;

	@Autowired
	AppointmentRepository appointments;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoBean
	InterpretationPort interpretationPort;

	@MockitoBean
	TimefoldSlotSolver solver;

	@Test
	@Timeout(40)
	@WithMockUser(username = "george", roles = "OWNER")
	void ownerRequestToOfferAcceptStaffCompleteAndOwnerHistory() throws Exception {
		String interpretation = """
				{"schemaVersion":"1.0","visitReason":"Wellness examination","durationMinutes":30,
				"careType":"GENERAL","requiredSpecialtyCode":null,"allowedWindows":[{"sourcePhrase":"Tuesday morning",
				"resolvedStart":"2026-09-01T09:00:00-05:00","resolvedEnd":"2026-09-01T12:00:00-05:00",
				"resolution":"RESOLVED","fallbackAllowed":true}],"preferredWindows":[],"excludedWindows":[],
				"preferredVeterinarianCode":null,"veterinarianPreferenceStrength":"NONE",
				"urgency":"NO_CONCERN_IDENTIFIED","unresolvedDates":[],"uncertainties":[],"confidence":0.91}
				""";
		given(this.interpretationPort.interpret(any())).willReturn(new InterpretationPort.InterpretationCallResult(
				interpretation, "gemma4:latest", "gemma4:latest", false));
		given(this.solver.solve(any(), any())).willAnswer(invocation -> selected(invocation.getArgument(0)));

		this.mockMvc
			.perform(post("/owner/scheduling-requests").with(csrf())
				.param("petId", "1")
				.param("sourceText", "Leo needs a wellness exam next week in the morning"))
			.andExpect(status().is3xxRedirection());
		SchedulingRequest request = this.requests.findByOwnerIdOrderByUpdatedAtDesc(1).get(0);
		Account account = this.accounts.findByUsername("george").orElseThrow();

		this.mockMvc
			.perform(post("/owner/scheduling-requests/{id}/consent/interpret", request.getId()).with(csrf())
				.param("expectedVersion", request.getVersion().toString())
				.param("agree", "true"))
			.andExpect(status().is3xxRedirection());
		awaitState(request.getId(), RequestState.INTERPRETATION_REVIEW);
		request = this.requests.findById(request.getId()).orElseThrow();

		this.mockMvc
			.perform(post("/owner/scheduling-requests/{id}/interpretation/confirm", request.getId()).with(csrf())
				.param("expectedVersion", request.getVersion().toString())
				.param("visitReason", "Wellness examination")
				.param("durationMinutes", "30"))
			.andExpect(status().is3xxRedirection());
		request = this.requests.findById(request.getId()).orElseThrow();
		assertThat(request.getState()).isEqualTo(RequestState.READY_FOR_SUGGESTION);

		this.mockMvc
			.perform(post("/owner/scheduling-requests/{id}/suggestions", request.getId()).with(csrf())
				.param("expectedVersion", request.getVersion().toString()))
			.andExpect(status().is3xxRedirection());
		awaitState(request.getId(), RequestState.OFFER_HELD);
		request = this.requests.findById(request.getId()).orElseThrow();
		Long revisionId = request.getActiveRequestRevisionId();
		Offer offer = this.offers.findAll()
			.stream()
			.filter(item -> revisionId.equals(item.getRequestRevisionId()))
			.findFirst()
			.orElseThrow();

		this.mockMvc
			.perform(post("/owner/scheduling-requests/{id}/offers/{offerId}/accept", request.getId(), offer.getId())
				.with(csrf())
				.param("expectedVersion",
						this.requests.findById(request.getId()).orElseThrow().getVersion().toString()))
			.andExpect(status().is3xxRedirection());
		assertThat(this.requests.findById(request.getId()).orElseThrow().getState()).isEqualTo(RequestState.CONFIRMED);
		Long requestId = request.getId();
		Appointment appointment = this.appointments.findAll()
			.stream()
			.filter(item -> requestId.equals(item.getRequestId()))
			.findFirst()
			.orElseThrow();
		assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);

		this.jdbc.update("update appointments set start_at = ?, end_at = ? where id = ?",
				java.sql.Timestamp.from(java.time.Instant.parse("2026-01-01T10:00:00Z")),
				java.sql.Timestamp.from(java.time.Instant.parse("2026-01-01T10:30:00Z")), appointment.getId());

		this.mockMvc
			.perform(post("/staff/appointments/{id}/complete", appointment.getId()).with(user("admin").roles("STAFF"))
				.with(csrf())
				.param("note", "Completed wellness exam"))
			.andExpect(status().is3xxRedirection());
		assertThat(this.appointments.findById(appointment.getId()).orElseThrow().getStatus())
			.isEqualTo(AppointmentStatus.COMPLETED);

		this.mockMvc.perform(get("/owner/history"))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/owner/history"));
		assertThat(account.getOwnerId()).isEqualTo(1);
	}

	private SlotSelectionResult selected(SlotSelectionSnapshot snapshot) {
		CandidateSlot slot = snapshot.candidates()
			.stream()
			.filter(candidate -> SlotScorePolicy.baseEligible(snapshot, candidate))
			.findFirst()
			.orElseThrow();
		return new SlotSelectionResult("SELECTED", slot, SlotScorePolicy.score(snapshot, slot), true);
	}

	private void awaitState(Long requestId, RequestState expected) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 10_000;
		RequestState actual;
		do {
			actual = this.requests.findById(requestId).orElseThrow().getState();
			if (actual == expected) {
				return;
			}
			Thread.sleep(50);
		}
		while (System.currentTimeMillis() < deadline);
		assertThat(actual).isEqualTo(expected);
	}

}
