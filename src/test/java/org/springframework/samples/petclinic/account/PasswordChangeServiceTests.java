package org.springframework.samples.petclinic.account;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.samples.petclinic.audit.AuditService;
import org.springframework.samples.petclinic.audit.OwnerHistoryService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordChangeServiceTests {

	@Mock
	private AccountRepository accountRepository;

	@Mock
	private AuditService auditService;

	@Mock
	private OwnerHistoryService ownerHistoryService;

	@Mock
	private HttpServletRequest request;

	private PasswordEncoder passwordEncoder;

	private PasswordChangeService passwordChangeService;

	@BeforeEach
	void setUp() {
		this.passwordEncoder = new BCryptPasswordEncoder();
		this.passwordChangeService = new PasswordChangeService(this.accountRepository, this.passwordEncoder,
				this.auditService, this.ownerHistoryService);
	}

	@Test
	void testSuccessfulPasswordChange() {
		Long accountId = 10L;
		String oldRawPassword = "tempPassword123!";
		String newPassword = "NewStrongPassword456#";

		Account account = new Account();
		account.setId(accountId);
		account.setUsername("george.franklin");
		account.setPasswordHash(this.passwordEncoder.encode(oldRawPassword));
		account.setRole(Role.OWNER);
		account.setOwnerId(1);
		account.setPasswordChangeRequired(true);
		account.setTemporaryPasswordExpiresAt(Instant.now().plus(6, ChronoUnit.DAYS));
		account.setSessionVersion(0L);

		when(this.accountRepository.findById(accountId)).thenReturn(Optional.of(account));
		when(this.accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

		this.passwordChangeService.changePassword(accountId, oldRawPassword, newPassword, newPassword, this.request);

		verify(this.request).changeSessionId();

		ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
		verify(this.accountRepository).save(captor.capture());
		Account updated = captor.getValue();

		assertThat(this.passwordEncoder.matches(newPassword, updated.getPasswordHash())).isTrue();
		assertThat(updated.isPasswordChangeRequired()).isFalse();
		assertThat(updated.getTemporaryPasswordExpiresAt()).isNull();
		assertThat(updated.getSessionVersion()).isEqualTo(1L);

		verify(this.auditService).recordEvent(eq(accountId), eq("PASSWORD_CHANGED"), eq("Account"), eq("10"),
				eq("SUCCESS"), any(), isNull(), isNull());
		verify(this.ownerHistoryService).recordEvent(eq(1), eq("PASSWORD_CHANGED"), any(), eq("Account"), eq("10"));
	}

	@Test
	void testIncorrectCurrentPasswordRejected() {
		Long accountId = 10L;
		Account account = new Account();
		account.setId(accountId);
		account.setPasswordHash(this.passwordEncoder.encode("correctPassword123"));

		when(this.accountRepository.findById(accountId)).thenReturn(Optional.of(account));

		assertThatThrownBy(() -> this.passwordChangeService.changePassword(accountId, "wrongPassword123",
				"NewPassword456#", "NewPassword456#", this.request))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Current password is incorrect");

		verify(this.accountRepository, never()).save(any());
	}

	@Test
	void testExpiredTemporaryPasswordRejected() {
		Long accountId = 10L;
		String oldPassword = "tempPassword123!";
		Account account = new Account();
		account.setId(accountId);
		account.setPasswordHash(this.passwordEncoder.encode(oldPassword));
		account.setPasswordChangeRequired(true);
		account.setTemporaryPasswordExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS)); // Expired!

		when(this.accountRepository.findById(accountId)).thenReturn(Optional.of(account));

		assertThatThrownBy(() -> this.passwordChangeService.changePassword(accountId, oldPassword, "NewPassword456#",
				"NewPassword456#", this.request))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Temporary password has expired");

		verify(this.accountRepository, never()).save(any());
	}

	@Test
	void testPasswordConfirmationMismatchRejected() {
		assertThatThrownBy(() -> this.passwordChangeService.changePassword(10L, "currentPass", "NewPassword1",
				"DifferentPassword2", this.request))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("New password and confirmation do not match");

		verify(this.accountRepository, never()).save(any());
	}

	@Test
	void testPasswordTooShortRejected() {
		assertThatThrownBy(
				() -> this.passwordChangeService.changePassword(10L, "currentPass", "short", "short", this.request))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("at least 8 characters");

		verify(this.accountRepository, never()).save(any());
	}

	@Test
	void testPasswordSameAsCurrentRejected() {
		Long accountId = 10L;
		String samePass = "CurrentPassword123";

		assertThatThrownBy(
				() -> this.passwordChangeService.changePassword(accountId, samePass, samePass, samePass, this.request))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("different from current password");

		verify(this.accountRepository, never()).save(any());
	}

}
