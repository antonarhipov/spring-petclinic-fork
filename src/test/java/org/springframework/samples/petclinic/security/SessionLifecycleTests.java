package org.springframework.samples.petclinic.security;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.account.AccountRepository;
import org.springframework.samples.petclinic.account.Role;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SessionLifecycleTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SessionVersionService sessionVersionService;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private PetClinicPrincipal george;

	private Account georgeAccount;

	@BeforeEach
	void setUp() {
		this.georgeAccount = this.accountRepository.findByUsername("george").orElseGet(() -> {
			Account account = new Account();
			account.setUsername("george");
			account.setPasswordHash(this.passwordEncoder.encode("george123"));
			account.setRole(Role.OWNER);
			account.setOwnerId(1);
			account.setEnabled(true);
			account.setPasswordChangeRequired(false);
			account.setSessionVersion(0L);
			return this.accountRepository.save(account);
		});
		this.george = SecurityTestPrincipals.owner(this.georgeAccount.getId(), this.georgeAccount.getUsername(),
				this.georgeAccount.getOwnerId(), this.georgeAccount.getSessionVersion(), false);
	}

	@Test
	void statusPollingPathsAreRecognizedAsNonExtending() {
		assertThat(InteractiveSessionFilter.isStatusPolling("/owner/requests/abc-123/status")).isTrue();
		assertThat(InteractiveSessionFilter.isStatusPolling("/owner/requests/abc-123/status/")).isTrue();
		assertThat(InteractiveSessionFilter.isStatusPolling("/owner/dashboard")).isFalse();
		assertThat(InteractiveSessionFilter.isStatusPolling("/owner/requests")).isFalse();
		assertThat(InteractiveSessionFilter.isStatusPolling("/session/status")).isFalse();
	}

	@Test
	void interactiveRequestExtendsSessionWhileStatusPollingDoesNot() throws Exception {
		MockHttpSession session = new MockHttpSession();
		long initialAccess = System.currentTimeMillis() - 60_000L;
		session.setAttribute(InteractiveSessionFilter.LAST_INTERACTIVE_ACCESS_TIME, initialAccess);

		this.mockMvc.perform(get("/owner/dashboard").session(session).with(user(this.george)))
			.andExpect(status().isOk());

		Long afterInteractive = (Long) session.getAttribute(InteractiveSessionFilter.LAST_INTERACTIVE_ACCESS_TIME);
		assertThat(afterInteractive).isNotNull().isGreaterThan(initialAccess);

		long frozen = afterInteractive;
		this.mockMvc.perform(get("/owner/requests/req-1/status").session(session).with(user(this.george)))
			.andExpect(status().isNotFound()); // no handler yet in Phase 2; filter still
												// runs

		Long afterPolling = (Long) session.getAttribute(InteractiveSessionFilter.LAST_INTERACTIVE_ACCESS_TIME);
		assertThat(afterPolling).isEqualTo(frozen);

		this.mockMvc.perform(get("/session/status").session(session).with(user(this.george)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.authenticated").value(true))
			.andExpect(jsonPath("$.remainingSeconds").isNumber());

		Long afterStatusEndpoint = (Long) session.getAttribute(InteractiveSessionFilter.LAST_INTERACTIVE_ACCESS_TIME);
		assertThat(afterStatusEndpoint).isEqualTo(frozen);
	}

	@Test
	void sessionStatusReportsWarningWhenUnderTwoMinutesRemain() throws Exception {
		MockHttpSession session = new MockHttpSession();
		long almostExpired = System.currentTimeMillis() - (InteractiveSessionFilter.SESSION_TIMEOUT_MS - 90_000L);
		session.setAttribute(InteractiveSessionFilter.LAST_INTERACTIVE_ACCESS_TIME, almostExpired);

		this.mockMvc.perform(get("/session/status").session(session).with(user(this.george)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.authenticated").value(true))
			.andExpect(jsonPath("$.warningRequired").value(true))
			.andExpect(jsonPath("$.remainingSeconds").value(org.hamcrest.Matchers.lessThanOrEqualTo(120)));
	}

	@Test
	void expiredInteractiveSessionRedirectsToLogin() throws Exception {
		MockHttpSession session = new MockHttpSession();
		long expired = System.currentTimeMillis() - InteractiveSessionFilter.SESSION_TIMEOUT_MS - 1_000L;
		session.setAttribute(InteractiveSessionFilter.LAST_INTERACTIVE_ACCESS_TIME, expired);

		this.mockMvc.perform(get("/owner/dashboard").session(session).with(user(this.george)))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/auth/login?expired=true"));
	}

	@Test
	void expiredInteractiveSessionReturnsUnauthorizedJsonForStatusPolling() throws Exception {
		MockHttpSession session = new MockHttpSession();
		long expired = System.currentTimeMillis() - InteractiveSessionFilter.SESSION_TIMEOUT_MS - 1_000L;
		session.setAttribute(InteractiveSessionFilter.LAST_INTERACTIVE_ACCESS_TIME, expired);

		this.mockMvc.perform(get("/owner/requests/req-42/status").session(session).with(user(this.george)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("session_expired"));
	}

	@Test
	void explicitSessionExtendRefreshesInteractiveAccessTime() throws Exception {
		MockHttpSession session = new MockHttpSession();
		long initialAccess = System.currentTimeMillis() - 120_000L;
		session.setAttribute(InteractiveSessionFilter.LAST_INTERACTIVE_ACCESS_TIME, initialAccess);

		this.mockMvc.perform(post("/session/extend").session(session).with(user(this.george)).with(csrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("extended"));

		Long refreshed = (Long) session.getAttribute(InteractiveSessionFilter.LAST_INTERACTIVE_ACCESS_TIME);
		assertThat(refreshed).isNotNull().isGreaterThan(initialAccess);
	}

	@Test
	void sessionVersionInvalidationRejectsStalePrincipal() throws Exception {
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(InteractiveSessionFilter.LAST_INTERACTIVE_ACCESS_TIME, System.currentTimeMillis());

		this.mockMvc.perform(get("/owner/dashboard").session(session).with(user(this.george)))
			.andExpect(status().isOk());

		long previousVersion = this.george.getSessionVersion();
		this.sessionVersionService.invalidateSessions(this.georgeAccount.getId());

		Optional<Account> reloaded = this.accountRepository.findById(this.georgeAccount.getId());
		assertThat(reloaded).isPresent();
		assertThat(reloaded.get().getSessionVersion()).isEqualTo(previousVersion + 1);
		assertThat(
				this.sessionVersionService.isSessionVersionValid(this.george.getId(), this.george.getSessionVersion()))
			.isFalse();

		this.mockMvc.perform(get("/owner/dashboard").session(session).with(user(this.george)))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/auth/login?expired=true"));
	}

	@Test
	void temporaryPasswordPrincipalIsRestrictedToPasswordChangeRoutes() throws Exception {
		PetClinicPrincipal tempOwner = SecurityTestPrincipals.owner(this.georgeAccount.getId(), "george", 1,
				this.georgeAccount.getSessionVersion(), true);
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(InteractiveSessionFilter.LAST_INTERACTIVE_ACCESS_TIME, System.currentTimeMillis());

		this.mockMvc.perform(get("/owner/dashboard").session(session).with(user(tempOwner)))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/auth/password-change"));

		this.mockMvc.perform(get("/auth/password-change").session(session).with(user(tempOwner)))
			.andExpect(status().isOk());
	}

}
