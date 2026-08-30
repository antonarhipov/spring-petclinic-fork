package org.springframework.samples.petclinic.scheduling.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.account.AccountRepository;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationPort;
import org.springframework.samples.petclinic.scheduling.request.InterpretationRecord;
import org.springframework.samples.petclinic.scheduling.request.InterpretationRecordRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestRevision;
import org.springframework.samples.petclinic.scheduling.request.RequestRevisionRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OwnerInterpretationJourneyTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AccountRepository accounts;

	@Autowired
	SchedulingRequestRepository requests;

	@Autowired
	RequestRevisionRepository revisions;

	@Autowired
	InterpretationRecordRepository interpretations;

	@MockitoBean
	InterpretationPort interpretationPort;

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void ownerCanCompleteInterpretationJourneyAndResumeFromDashboard() throws Exception {
		String interpretation = """
				{"schemaVersion":"1.0","visitReason":"Wellness examination","durationMinutes":30,
				"careType":"GENERAL","requiredSpecialtyCode":null,"allowedWindows":[{"sourcePhrase":"Tuesday morning",
				"resolvedStart":"2026-09-01T09:00:00-05:00","resolvedEnd":"2026-09-01T09:30:00-05:00",
				"resolution":"RESOLVED","fallbackAllowed":true}],"preferredWindows":[],"excludedWindows":[],
				"preferredVeterinarianCode":null,"veterinarianPreferenceStrength":"NONE",
				"urgency":"NO_CONCERN_IDENTIFIED","unresolvedDates":[],"uncertainties":[],"confidence":0.91}
				""";
		given(this.interpretationPort.interpret(any())).willReturn(new InterpretationPort.InterpretationCallResult(
				interpretation, "gemma4:latest", "gemma4:latest", false));
		this.mockMvc
			.perform(post("/owner/scheduling-requests").with(csrf())
				.param("petId", "1")
				.param("sourceText", "Leo needs a wellness exam next week in the morning"))
			.andExpect(status().is3xxRedirection());
		SchedulingRequest request = this.requests.findByOwnerIdOrderByUpdatedAtDesc(1).get(0);
		assertThat(request.getState()).isEqualTo(RequestState.AWAITING_CONSENT);
		this.mockMvc.perform(get("/owner/dashboard"))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/owner/dashboard"));
		Account account = this.accounts.findByUsername("george").orElseThrow();
		assertThat(account.getOwnerId()).isEqualTo(1);

		this.mockMvc
			.perform(post("/owner/scheduling-requests/{id}/consent/interpret", request.getId()).with(csrf())
				.param("expectedVersion", request.getVersion().toString())
				.param("agree", "true"))
			.andExpect(status().is3xxRedirection());

		awaitState(request.getId(), RequestState.INTERPRETATION_REVIEW);
		request = this.requests.findById(request.getId()).orElseThrow();
		assertThat(this.interpretations.findAll()).anySatisfy(record -> {
			assertThat(record.getOutcome()).isEqualTo("VALID_REVIEWABLE");
			assertThat(record.getUnknownFieldsJson()).contains("confidence");
		});
		RequestRevision revision = this.revisions.findById(request.getActiveRequestRevisionId()).orElseThrow();
		assertThat(revision.getVisitReason()).isEqualTo("Wellness examination");
		this.mockMvc.perform(get("/owner/dashboard"))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/owner/dashboard"));
		this.mockMvc.perform(get("/owner/scheduling-requests/{id}/interpretation", request.getId()))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/owner/interpretation-review"));

		this.mockMvc
			.perform(post("/owner/scheduling-requests/{id}/interpretation/confirm", request.getId()).with(csrf())
				.param("expectedVersion", request.getVersion().toString())
				.param("visitReason", "Wellness examination")
				.param("durationMinutes", "30"))
			.andExpect(status().is3xxRedirection());
		assertThat(this.requests.findById(request.getId()).orElseThrow().getState())
			.isEqualTo(RequestState.READY_FOR_SUGGESTION);
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
