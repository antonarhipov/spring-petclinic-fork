package org.springframework.samples.petclinic.scheduling.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.appointment.Offer;
import org.springframework.samples.petclinic.scheduling.appointment.OfferRepository;
import org.springframework.samples.petclinic.scheduling.appointment.OfferStatus;
import org.springframework.samples.petclinic.scheduling.appointment.ReservationBlockRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OwnerSuggestionJourneyTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	SchedulingRequestRepository requests;

	@Autowired
	OfferRepository offers;

	@Autowired
	AppointmentRepository appointments;

	@Autowired
	ReservationBlockRepository blocks;

	@MockitoBean
	InterpretationPort interpretationPort;

	@MockitoBean
	TimefoldSlotSolver solver;

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void ownerCanCompletePreferredSuggestionAndAcceptJourney() throws Exception {
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
		Offer offer = this.offers
			.findFirstByRequestRevisionIdAndStatusOrderByCreatedAtDesc(request.getActiveRequestRevisionId(),
					OfferStatus.HELD)
			.orElseThrow();
		request = this.requests.findById(request.getId()).orElseThrow();
		this.mockMvc
			.perform(post("/owner/scheduling-requests/{id}/offers/{offerId}/accept", request.getId(), offer.getId())
				.with(csrf())
				.param("expectedVersion", request.getVersion().toString()))
			.andExpect(status().is3xxRedirection());

		assertThat(this.requests.findById(request.getId()).orElseThrow().getState()).isEqualTo(RequestState.CONFIRMED);
		Appointment appointment = this.appointments.findByRequestId(request.getId()).get(0);
		assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
		assertThat(this.blocks.findAll()).isNotEmpty().allMatch(block -> block.getAppointmentId() != null);
		assertThat(this.blocks.findAll()).noneMatch(block -> block.getHoldId() != null);
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
