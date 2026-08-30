package org.springframework.samples.petclinic.account;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.Session;

import jakarta.servlet.FilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordAndSessionServiceTests {

	private static final Instant NOW = Instant.parse("2026-03-16T12:00:00Z");

	private final AccountRepository accounts = mock(AccountRepository.class);

	private final PasswordEncoder encoder = new BCryptPasswordEncoder();

	private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

	private final AccountAuditService audit = mock(AccountAuditService.class);

	@SuppressWarnings("unchecked")
	private final FindByIndexNameSessionRepository<Session> sessionRepository = mock(
			FindByIndexNameSessionRepository.class);

	private AccountSessionService sessions;

	private PasswordService passwords;

	@BeforeEach
	void setUp() {
		this.sessions = new AccountSessionService(this.sessionRepository);
		this.passwords = new PasswordService(this.accounts, this.encoder, this.clock, this.sessions, this.audit);
	}

	@Test
	void changePasswordRequiresMinimumLengthAndMatchingConfirmation() {
		Account account = ownerAccount("betty", "temp-pass");
		when(this.accounts.findByUsername("betty")).thenReturn(Optional.of(account));
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.getSession(true);

		assertThatThrownBy(() -> this.passwords.changePassword("betty", "temp-pass", "short", "short", request))
			.isInstanceOf(PasswordChangeException.class);
		assertThatThrownBy(
				() -> this.passwords.changePassword("betty", "temp-pass", "long-enough", "mismatch", request))
			.isInstanceOf(PasswordChangeException.class);
		assertThat(account.isMustChangePassword()).isTrue();
	}

	@Test
	void changePasswordClearsTemporaryFlagRotatesSessionAndIncrementsCredentialVersion() {
		Account account = ownerAccount("betty", "temp-pass");
		account.setCredentialVersion(1);
		when(this.accounts.findByUsername("betty")).thenReturn(Optional.of(account));
		when(this.accounts.save(account)).thenReturn(account);
		MockHttpServletRequest request = new MockHttpServletRequest();
		String originalSessionId = request.getSession(true).getId();

		this.passwords.changePassword("betty", "temp-pass", "new-secret", "new-secret", request);

		assertThat(account.isMustChangePassword()).isFalse();
		assertThat(account.getTemporaryCredentialExpiresAt()).isNull();
		assertThat(account.getCredentialVersion()).isEqualTo(2);
		assertThat(this.encoder.matches("new-secret", account.getPasswordHash())).isTrue();
		assertThat(request.getSession(false).getId()).isNotEqualTo(originalSessionId);
		verify(this.audit).recordPasswordChange(account);
	}

	@Test
	void resetDeletesEverySessionForThePrincipal() {
		MapSession first = new MapSession("s1");
		MapSession second = new MapSession("s2");
		Map<String, Session> found = new LinkedHashMap<>();
		found.put("s1", first);
		found.put("s2", second);
		when(this.sessionRepository.findByPrincipalName("betty")).thenReturn(found);

		this.sessions.invalidateAllSessions("betty");

		verify(this.sessionRepository).deleteById("s1");
		verify(this.sessionRepository).deleteById("s2");
	}

	@Test
	void inactivityTimeoutIsThirtyMinutes() throws Exception {
		Properties properties = new Properties();
		properties.load(getClass().getClassLoader().getResourceAsStream("application.properties"));
		assertThat(properties.getProperty("spring.session.timeout")).isEqualTo("30m");
	}

	@Test
	void temporaryPasswordUsersMayOnlyChangePasswordAndLogout() throws Exception {
		Account account = ownerAccount("betty", "temp-pass");
		when(this.accounts.findByUsername("betty")).thenReturn(Optional.of(account));
		TemporaryPasswordRestrictionFilter filter = new TemporaryPasswordRestrictionFilter(this.accounts);
		FilterChain chain = mock(FilterChain.class);

		MockHttpServletRequest dashboard = new MockHttpServletRequest("GET", "/owner/dashboard");
		dashboard.setServletPath("/owner/dashboard");
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken("betty", "n/a",
					List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));
		MockHttpServletResponse blocked = new MockHttpServletResponse();
		filter.doFilter(dashboard, blocked, chain);
		assertThat(blocked.getRedirectedUrl()).isEqualTo("/account/password/change");

		MockHttpServletRequest change = new MockHttpServletRequest("GET", "/account/password/change");
		change.setServletPath("/account/password/change");
		MockHttpServletResponse allowed = new MockHttpServletResponse();
		filter.doFilter(change, allowed, chain);
		verify(chain).doFilter(change, allowed);

		SecurityContextHolder.clearContext();
	}

	private Account ownerAccount(String username, String rawPassword) {
		Account account = new Account();
		account.setUsername(username);
		account.setPasswordHash(this.encoder.encode(rawPassword));
		account.setRole(AccountRole.OWNER);
		account.setOwnerId(11);
		account.setMustChangePassword(true);
		account.setTemporaryCredentialExpiresAt(NOW.plusSeconds(3600));
		account.setEnabled(true);
		account.setCreatedAt(NOW);
		account.setUpdatedAt(NOW);
		return account;
	}

}
