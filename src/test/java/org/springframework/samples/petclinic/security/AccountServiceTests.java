package org.springframework.samples.petclinic.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.scheduling.model.AuditEventType;
import org.springframework.samples.petclinic.scheduling.service.AuditService;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccountServiceTests {

	private static final Instant FIXED_NOW = Instant.parse("2026-01-15T12:00:00Z");

	@Mock
	private AccountRepository accountRepository;

	@Mock
	private AccountGuardRepository accountGuardRepository;

	@Mock
	private OwnerRepository ownerRepository;

	@Mock
	private AuditService auditService;

	@Mock
	private SessionRegistry sessionRegistry;

	@Mock
	private SessionInformation sessionInformation;

	private PasswordEncoder passwordEncoder;

	private AccountService accountService;

	@BeforeEach
	void setUp() {
		passwordEncoder = new BCryptPasswordEncoder();
		Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
		accountService = new AccountService(accountRepository, accountGuardRepository, ownerRepository, passwordEncoder,
				auditService, clock);
		accountService.setSessionRegistry(sessionRegistry);
	}

	@Test
	void createOwnerAccountRejectsDuplicateUsername() {
		when(accountRepository.existsByUsernameIgnoreCase("owner1")).thenReturn(true);

		assertThatThrownBy(() -> accountService.createOwnerAccount("owner1", "password1", 1, "staff1", "STAFF"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Username already exists");
	}

	@Test
	void createStaffAccountRejectsShortPassword() {
		assertThatThrownBy(() -> accountService.createStaffAccount("staff2", "short", "staff1", "STAFF"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("at least 8 characters");
	}

	@Test
	void createOwnerAccountHashesPasswordAndRequiresChange() {
		Owner owner = new Owner();
		owner.setId(1);
		when(accountRepository.existsByUsernameIgnoreCase("owner1")).thenReturn(false);
		when(ownerRepository.findById(1)).thenReturn(Optional.of(owner));
		when(accountRepository.findByOwnerId(1)).thenReturn(Optional.empty());
		when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
			Account account = invocation.getArgument(0);
			account.setId(10);
			return account;
		});

		Account saved = accountService.createOwnerAccount("Owner1", "password1", 1, "staff1", "STAFF");

		assertThat(saved.getUsername()).isEqualTo("owner1");
		assertThat(saved.getRole()).isEqualTo(AccountRole.OWNER);
		assertThat(saved.isPasswordChangeRequired()).isTrue();
		assertThat(passwordEncoder.matches("password1", saved.getPassword())).isTrue();
		assertThat(saved.getPassword()).doesNotContain("password1");
		verify(auditService).recordEvent(eq(AuditEventType.ACCOUNT_CREATED), eq("staff1"), eq("STAFF"), eq("ACCOUNT"),
				eq("10"), eq(null), any());
	}

	@Test
	void recordFailedLoginLocksAfterFiveAttempts() {
		Account account = staffAccount(5, "staff1", "hashed");
		account.setFailedLoginCount(4);
		when(accountRepository.findByUsernameIgnoreCaseForUpdate("staff1")).thenReturn(Optional.of(account));
		when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

		accountService.recordFailedLogin("staff1");

		assertThat(account.getFailedLoginCount()).isEqualTo(5);
		assertThat(account.getLockedUntil()).isEqualTo(FIXED_NOW.plus(15, ChronoUnit.MINUTES));
		verify(auditService).recordEvent(eq(AuditEventType.LOGIN_LOCKED), eq("staff1"), eq("STAFF"), eq("ACCOUNT"),
				eq("5"), eq("MAX_FAILED_ATTEMPTS"), any());
	}

	@Test
	void changePasswordRejectsReuseOfCurrentPasswordAndClearsFlag() {
		Account account = staffAccount(5, "staff1", passwordEncoder.encode("password1"));
		account.setPasswordChangeRequired(true);
		when(accountRepository.findById(5)).thenReturn(Optional.of(account));
		when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(sessionRegistry.getAllPrincipals()).thenReturn(List.of());

		assertThatThrownBy(() -> accountService.changePassword(5, "password1", "password1", "staff1", "STAFF"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("same as the current password");

		accountService.changePassword(5, "password1", "password2", "staff1", "STAFF");

		assertThat(account.isPasswordChangeRequired()).isFalse();
		assertThat(passwordEncoder.matches("password2", account.getPassword())).isTrue();
		verify(auditService).recordEvent(eq(AuditEventType.PASSWORD_CHANGED), eq("staff1"), eq("STAFF"), eq("ACCOUNT"),
				eq("5"), eq(null), any());
	}

	@Test
	void deactivateAccountBlocksFinalActiveStaff() {
		Account account = staffAccount(5, "staff1", "hashed");
		when(accountRepository.findById(5)).thenReturn(Optional.of(account));
		when(accountRepository.countByRoleAndActiveTrue(AccountRole.STAFF)).thenReturn(1L);

		assertThatThrownBy(() -> accountService.deactivateAccount(5, "staff1", "STAFF"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("final active staff");
		verify(accountRepository, never()).save(any(Account.class));
	}

	@Test
	void deactivateAccountInvalidatesSessions() {
		Account account = staffAccount(5, "staff1", "hashed");
		UserPrincipal principal = UserPrincipal.fromAccount(account);
		when(accountRepository.findById(5)).thenReturn(Optional.of(account));
		when(accountRepository.countByRoleAndActiveTrue(AccountRole.STAFF)).thenReturn(2L);
		when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(sessionRegistry.getAllPrincipals()).thenReturn(List.of(principal));
		when(sessionRegistry.getAllSessions(principal, false)).thenReturn(List.of(sessionInformation));

		accountService.deactivateAccount(5, "admin", "STAFF");

		assertThat(account.isActive()).isFalse();
		verify(sessionInformation).expireNow();
		verify(auditService).recordEvent(eq(AuditEventType.ACCOUNT_DEACTIVATED), eq("admin"), eq("STAFF"),
				eq("ACCOUNT"), eq("5"), eq(null), any());
	}

	@Test
	void renameAccountInvalidatesOldAndNewUsernameSessions() {
		Account account = staffAccount(5, "olduser", "hashed");
		when(accountRepository.findById(5)).thenReturn(Optional.of(account));
		when(accountRepository.existsByUsernameIgnoreCase("newuser")).thenReturn(false);
		when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(sessionRegistry.getAllPrincipals()).thenReturn(List.of());

		accountService.renameAccount(5, "NewUser", "admin", "STAFF");

		assertThat(account.getUsername()).isEqualTo("newuser");
		verify(auditService).recordEvent(eq(AuditEventType.ACCOUNT_RENAMED), eq("admin"), eq("STAFF"), eq("ACCOUNT"),
				eq("5"), eq(null), any());
	}

	private static Account staffAccount(Integer id, String username, String password) {
		Account account = new Account();
		account.setId(id);
		account.setUsername(username);
		account.setPassword(password);
		account.setRole(AccountRole.STAFF);
		account.setActive(true);
		account.setPasswordChangeRequired(false);
		account.setFailedLoginCount(0);
		return account;
	}

}
