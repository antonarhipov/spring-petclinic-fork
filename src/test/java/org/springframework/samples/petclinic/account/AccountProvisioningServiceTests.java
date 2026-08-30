package org.springframework.samples.petclinic.account;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.samples.petclinic.system.StaleStateException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountProvisioningServiceTests {

	private static final Instant NOW = Instant.parse("2026-03-16T12:00:00Z");

	private final AccountRepository accounts = mock(AccountRepository.class);

	private final OwnerRepository owners = mock(OwnerRepository.class);

	private final PasswordEncoder encoder = new BCryptPasswordEncoder();

	private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

	private final UsernameSuggester suggester = new UsernameSuggester();

	private final OneTimePasswordGenerator passwords = new OneTimePasswordGenerator();

	private final AccountAuditService audit = mock(AccountAuditService.class);

	private final AccountSessionService sessions = mock(AccountSessionService.class);

	private AccountProvisioningService service;

	@BeforeEach
	void setUp() {
		this.service = new AccountProvisioningService(this.accounts, this.owners, this.encoder, this.clock,
				this.suggester, this.passwords, this.audit, this.sessions);
		when(this.accounts.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void suggestedUsernameNormalizesFirstNameAndAddsNumericSuffixOnCollision() {
		Set<String> taken = new HashSet<>();
		assertThat(this.suggester.suggest("George", taken::contains)).isEqualTo("george");
		taken.add("george");
		assertThat(this.suggester.suggest("George", taken::contains)).isEqualTo("george2");
		taken.add("george2");
		assertThat(this.suggester.suggest(" GEORGE ", taken::contains)).isEqualTo("george3");
	}

	@Test
	void oneTimePasswordsAreCryptographicallyRandomAndAtLeastSixCharacters() {
		String first = this.passwords.generate();
		String second = this.passwords.generate();
		assertThat(first).hasSizeGreaterThanOrEqualTo(6);
		assertThat(second).hasSizeGreaterThanOrEqualTo(6);
		assertThat(first).isNotEqualTo(second);
	}

	@Test
	void provisionPersistsBcryptOnlyWithSevenDayExpiryAndReturnsPlaintextOnce() {
		Owner owner = owner(11, "Betty");
		when(this.owners.findById(11)).thenReturn(Optional.of(owner));
		when(this.accounts.findByOwnerId(11)).thenReturn(Optional.empty());
		when(this.accounts.existsByUsername("betty")).thenReturn(false);

		OneTimeCredentialResult result = this.service.provision(11, "Betty", 0, 99L);

		assertThat(result.username()).isEqualTo("betty");
		assertThat(result.oneTimePassword()).hasSizeGreaterThanOrEqualTo(6);
		assertThat(result.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
		verify(this.accounts).save(any(Account.class));
		Account saved = capturedAccount();
		assertThat(saved.getUsername()).isEqualTo("betty");
		assertThat(saved.getOwnerId()).isEqualTo(11);
		assertThat(saved.getRole()).isEqualTo(AccountRole.OWNER);
		assertThat(saved.isMustChangePassword()).isTrue();
		assertThat(saved.getTemporaryCredentialExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
		assertThat(saved.getPasswordHash()).startsWith("$2");
		assertThat(saved.getPasswordHash()).doesNotContain(result.oneTimePassword());
		assertThat(this.encoder.matches(result.oneTimePassword(), saved.getPasswordHash())).isTrue();
		verify(this.audit).recordProvision(99L, saved);
	}

	@Test
	void secondProvisionForSameOwnerIsConflictNotASecondRow() {
		Owner owner = owner(11, "Betty");
		when(this.owners.findById(11)).thenReturn(Optional.of(owner));
		Account existing = new Account();
		existing.setUsername("betty");
		existing.setOwnerId(11);
		when(this.accounts.findByOwnerId(11)).thenReturn(Optional.of(existing));

		assertThatThrownBy(() -> this.service.provision(11, "betty", 0, 99L))
			.isInstanceOf(AccountConflictException.class);
		verify(this.accounts, never()).save(any(Account.class));
	}

	@Test
	void duplicateUsernameIsRejected() {
		Owner owner = owner(11, "Betty");
		when(this.owners.findById(11)).thenReturn(Optional.of(owner));
		when(this.accounts.findByOwnerId(11)).thenReturn(Optional.empty());
		when(this.accounts.existsByUsername("taken")).thenReturn(true);

		assertThatThrownBy(() -> this.service.provision(11, "taken", 0, 99L))
			.isInstanceOf(DuplicateUsernameException.class);
		verify(this.accounts, never()).save(any(Account.class));
	}

	@Test
	void resetIssuesNewOneTimePasswordIncrementsCredentialVersionAndInvalidatesSessions() {
		Account existing = new Account();
		existing.setUsername("betty");
		existing.setOwnerId(11);
		existing.setRole(AccountRole.OWNER);
		existing.setPasswordHash(this.encoder.encode("old-secret"));
		existing.setCredentialVersion(2);
		existing.setMustChangePassword(false);
		ReflectionTestUtils.setField(existing, "version", 4);
		when(this.accounts.findByOwnerId(11)).thenReturn(Optional.of(existing));
		when(this.accounts.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

		OneTimeCredentialResult result = this.service.reset(11, 4, true, 99L);

		assertThat(result.username()).isEqualTo("betty");
		assertThat(result.oneTimePassword()).hasSizeGreaterThanOrEqualTo(6);
		assertThat(result.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
		assertThat(existing.isMustChangePassword()).isTrue();
		assertThat(existing.getCredentialVersion()).isEqualTo(3);
		assertThat(existing.getPasswordHash()).startsWith("$2");
		assertThat(existing.getPasswordHash()).doesNotContain(result.oneTimePassword());
		assertThat(this.encoder.matches(result.oneTimePassword(), existing.getPasswordHash())).isTrue();
		verify(this.sessions).invalidateAllSessions("betty");
		verify(this.audit).recordReset(99L, existing);
	}

	@Test
	void resetRejectsStaleVersion() {
		Account existing = new Account();
		existing.setUsername("betty");
		existing.setOwnerId(11);
		ReflectionTestUtils.setField(existing, "version", 4);
		when(this.accounts.findByOwnerId(11)).thenReturn(Optional.of(existing));

		assertThatThrownBy(() -> this.service.reset(11, 3, true, 99L)).isInstanceOf(StaleStateException.class);
		verify(this.sessions, never()).invalidateAllSessions(any());
	}

	private Account capturedAccount() {
		org.mockito.ArgumentCaptor<Account> captor = org.mockito.ArgumentCaptor.forClass(Account.class);
		verify(this.accounts).save(captor.capture());
		return captor.getValue();
	}

	private static Owner owner(int id, String firstName) {
		Owner owner = new Owner();
		owner.setId(id);
		owner.setFirstName(firstName);
		owner.setLastName("Tester");
		return owner;
	}

}
