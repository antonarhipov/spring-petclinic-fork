package org.springframework.samples.petclinic.account;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.samples.petclinic.audit.AuditService;
import org.springframework.samples.petclinic.shared.command.CommandRecord;
import org.springframework.samples.petclinic.shared.command.CommandResult;
import org.springframework.samples.petclinic.shared.command.CommandService;
import org.springframework.samples.petclinic.shared.command.CommandStatus;
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
class PasswordResetServiceTests {

	@Mock
	private AccountRepository accountRepository;

	@Mock
	private AuditService auditService;

	@Mock
	private CommandService commandService;

	private PasswordEncoder passwordEncoder;

	private TemporaryPasswordGenerator temporaryPasswordGenerator;

	private PasswordResetService passwordResetService;

	@BeforeEach
	void setUp() {
		this.passwordEncoder = new BCryptPasswordEncoder();
		this.temporaryPasswordGenerator = new TemporaryPasswordGenerator();
		this.passwordResetService = new PasswordResetService(this.accountRepository, this.passwordEncoder,
				this.temporaryPasswordGenerator, this.auditService, this.commandService);
	}

	@Test
	void testSuccessfulPasswordReset() {
		Long accountId = 15L;
		Long actorId = 100L;
		UUID commandId = UUID.randomUUID();

		Account account = new Account();
		account.setId(accountId);
		account.setUsername("betty.davis");
		account.setRole(Role.OWNER);
		account.setOwnerId(2);
		account.setPasswordHash(this.passwordEncoder.encode("oldPassword123!"));
		account.setPasswordChangeRequired(false);
		account.setSessionVersion(3L);

		when(this.commandService.findById(commandId)).thenReturn(Optional.empty());
		when(this.accountRepository.findById(accountId)).thenReturn(Optional.of(account));
		when(this.accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

		PasswordResetService.PasswordResetResult result = this.passwordResetService.resetPassword(accountId, actorId,
				commandId);

		assertThat(result.accountId()).isEqualTo(accountId);
		assertThat(result.username()).isEqualTo("betty.davis");
		assertThat(result.temporaryPassword()).isNotBlank();
		assertThat(result.expiresAt()).isAfter(Instant.now());

		ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
		verify(this.accountRepository).save(captor.capture());
		Account saved = captor.getValue();

		assertThat(this.passwordEncoder.matches(result.temporaryPassword(), saved.getPasswordHash())).isTrue();
		assertThat(saved.isPasswordChangeRequired()).isTrue();
		assertThat(saved.getTemporaryPasswordExpiresAt()).isEqualTo(result.expiresAt());
		assertThat(saved.getSessionVersion()).isEqualTo(4L); // Session version
																// incremented!

		verify(this.auditService).recordEvent(eq(actorId), eq("PASSWORD_RESET"), eq("Account"), eq("15"), eq("SUCCESS"),
				any(UUID.class), eq(commandId), isNull());
		verify(this.commandService).completeCommand(eq(commandId), any(CommandResult.class));
	}

	@Test
	void testResetNonExistentAccountRejected() {
		when(this.accountRepository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> this.passwordResetService.resetPassword(999L, 100L, UUID.randomUUID()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Account not found for id: 999");

		verify(this.accountRepository, never()).save(any());
	}

	@Test
	void testDuplicateCommandReplayReturnsCanonicalResult() {
		Long accountId = 15L;
		UUID commandId = UUID.randomUUID();
		CommandRecord completed = new CommandRecord();
		completed.setId(commandId);
		completed.setStatus(CommandStatus.COMPLETED);

		Account account = new Account();
		account.setId(accountId);
		account.setUsername("betty.davis");
		account.setTemporaryPasswordExpiresAt(Instant.now().plus(4, ChronoUnit.DAYS));

		when(this.commandService.findById(commandId)).thenReturn(Optional.of(completed));
		when(this.accountRepository.findById(accountId)).thenReturn(Optional.of(account));

		PasswordResetService.PasswordResetResult result = this.passwordResetService.resetPassword(accountId, 100L,
				commandId);

		assertThat(result.accountId()).isEqualTo(accountId);
		assertThat(result.username()).isEqualTo("betty.davis");
		assertThat(result.temporaryPassword()).isNull(); // cleartext only returned on
															// initial call
		assertThat(result.expiresAt()).isEqualTo(account.getTemporaryPasswordExpiresAt());

		verify(this.accountRepository, never()).save(any());
		verify(this.auditService, never()).recordEvent(any(), any(), any(), any(), any(), any(), any(), any());
	}

}
