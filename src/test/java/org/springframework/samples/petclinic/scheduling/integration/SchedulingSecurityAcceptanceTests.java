package org.springframework.samples.petclinic.scheduling.integration;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.account.AccountRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SchedulingSecurityAcceptanceTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AccountRepository accounts;

	@Autowired
	SchedulingRequestRepository requests;

	@Test
	void unauthenticatedHtmlRedirectsToLogin() throws Exception {
		this.mockMvc.perform(get("/owner/dashboard").accept(MediaType.TEXT_HTML))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
	}

	@Test
	void unauthenticatedJsonReceives401() throws Exception {
		this.mockMvc
			.perform(get("/api/owner/scheduling-requests/1/operations/{id}", UUID.randomUUID())
				.accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void ownerCannotReachStaffRoutes() throws Exception {
		this.mockMvc.perform(get("/staff/queue").accept(MediaType.TEXT_HTML)).andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(username = "admin", roles = "STAFF")
	void staffCannotReachOwnerRoutes() throws Exception {
		this.mockMvc.perform(get("/owner/dashboard").accept(MediaType.TEXT_HTML)).andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void ownerDashboardSucceeds() throws Exception {
		this.mockMvc.perform(get("/owner/dashboard").accept(MediaType.TEXT_HTML))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/owner/dashboard"));
	}

	@Test
	@WithMockUser(username = "admin", roles = "STAFF")
	void staffQueueSucceeds() throws Exception {
		this.mockMvc.perform(get("/staff/queue").accept(MediaType.TEXT_HTML))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/staff/queue"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "/owner/dashboard", "/owner/appointments", "/owner/history" })
	@WithMockUser(username = "george", roles = "OWNER")
	void ownerContractGetsSucceed(String path) throws Exception {
		this.mockMvc.perform(get(path).accept(MediaType.TEXT_HTML)).andExpect(status().isOk());
	}

	@ParameterizedTest
	@ValueSource(strings = { "/staff/queue", "/staff/calendar", "/staff/availability", "/staff/settings" })
	@WithMockUser(username = "admin", roles = "STAFF")
	void staffContractGetsSucceed(String path) throws Exception {
		this.mockMvc.perform(get(path).accept(MediaType.TEXT_HTML)).andExpect(status().isOk());
	}

	@ParameterizedTest
	@ValueSource(strings = { "/staff/queue", "/staff/calendar", "/staff/availability", "/staff/settings" })
	@WithMockUser(username = "george", roles = "OWNER")
	void ownerForbiddenOnStaffContractGets(String path) throws Exception {
		this.mockMvc.perform(get(path).accept(MediaType.TEXT_HTML)).andExpect(status().isForbidden());
	}

	@Test
	void publicLoginIsReachable() throws Exception {
		this.mockMvc.perform(get("/login").accept(MediaType.TEXT_HTML)).andExpect(status().isOk());
	}

	@Test
	@WithMockUser(username = "admin", roles = "STAFF")
	void staffMutatingWithoutCsrfIsForbidden() throws Exception {
		this.mockMvc.perform(post("/staff/queue/1/claim")).andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(username = "betty", roles = "OWNER")
	void crossOwnerRequestIsNotFound() throws Exception {
		this.mockMvc
			.perform(post("/owner/scheduling-requests").with(csrf())
				.param("petId", "1")
				.param("sourceText", "Leo needs a wellness exam next week"))
			.andExpect(status().isNotFound());
		SchedulingRequest foreign = this.requests.findByOwnerIdOrderByUpdatedAtDesc(1)
			.stream()
			.findFirst()
			.orElseGet(() -> createRequestForGeorge());
		this.mockMvc.perform(get("/owner/scheduling-requests/{id}/status", foreign.getId()))
			.andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void mutatingWithoutCsrfIsForbidden() throws Exception {
		this.mockMvc
			.perform(post("/owner/scheduling-requests").param("petId", "1")
				.param("sourceText", "Leo needs a wellness exam next week"))
			.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void staleExpectedVersionIsConflict() throws Exception {
		SchedulingRequest request = createRequestForGeorge();
		this.mockMvc
			.perform(post("/owner/scheduling-requests/{id}/consent/interpret", request.getId()).with(csrf())
				.accept(MediaType.TEXT_HTML)
				.param("expectedVersion", "999")
				.param("agree", "true"))
			.andExpect(status().isConflict());
	}

	@Test
	void temporaryPasswordUserCannotReachOwnerWorkspace() throws Exception {
		Account george = this.accounts.findByUsername("george").orElseThrow();
		george.setMustChangePassword(true);
		this.accounts.saveAndFlush(george);
		try {
			this.mockMvc
				.perform(get("/owner/dashboard").accept(MediaType.TEXT_HTML).with(user("george").roles("OWNER")))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/account/password/change"));
			this.mockMvc
				.perform(
						get("/account/password/change").accept(MediaType.TEXT_HTML).with(user("george").roles("OWNER")))
				.andExpect(status().isOk());
		}
		finally {
			Account latest = this.accounts.findByUsername("george").orElseThrow();
			latest.setMustChangePassword(false);
			this.accounts.saveAndFlush(latest);
		}
	}

	private SchedulingRequest createRequestForGeorge() {
		SchedulingRequest request = new SchedulingRequest();
		request.setOwnerId(1);
		request.setPetId(1);
		request.setState(RequestState.AWAITING_CONSENT);
		request.setOwnerStatusCode("AWAITING_CONSENT");
		request.setCreatedAt(java.time.Instant.parse("2026-01-01T00:00:00Z"));
		request.setUpdatedAt(java.time.Instant.parse("2026-01-01T00:00:00Z"));
		return this.requests.saveAndFlush(request);
	}

}
