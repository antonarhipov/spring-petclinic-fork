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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountProvisioningServiceTests {

	@Mock
	private AccountRepository accountRepository;

	@Mock
	private AuditService auditService;

	@Mock
	private CommandService commandService;

	private PasswordEncoder passwordEncoder;

	private TemporaryPasswordGenerator temporaryPasswordGenerator;

	private UsernamePolicy usernamePolicy;

	private AccountProvisioningService provisioningService;

	@BeforeEach
	void setUp() {
		this.passwordEncoder = new BCryptPasswordEncoder();
		this.temporaryPasswordGenerator = new TemporaryPasswordGenerator();
		this.usernamePolicy = new UsernamePolicy();
		this.provisioningService = new AccountProvisioningService(this.accountRepository, this.passwordEncoder,
				this.temporaryPasswordGenerator, this.usernamePolicy, this.auditService, this.commandService);
	}

	@Test
	void testUsernamePolicyValidationAndSuggestion() {
		assertThat(this.usernamePolicy.isValid("john.doe")).isTrue();
		assertThat(this.usernamePolicy.isValid("johndoe123")).isTrue();
		assertThat(this.usernamePolicy.isValid("john_doe-1")).isTrue();

		assertThat(this.usernamePolicy.isValid("ab")).isFalse(); // too short
		assertThat(this.usernamePolicy.isValid(".johndoe")).isFalse(); // leading dot
		assertThat(this.usernamePolicy.isValid("johndoe.")).isFalse(); // trailing dot
		assertThat(this.usernamePolicy.isValid("john..doe")).isFalse(); // consecutive
																		// dots
		assertThat(this.usernamePolicy.isValid("john--doe")).isFalse(); // consecutive
																		// hyphens
		assertThat(this.usernamePolicy.isValid("john@doe")).isFalse(); // invalid
																		// character

		assertThat(this.usernamePolicy.suggestUsername("George", "Franklin")).isEqualTo("george.franklin");
		assertThat(this.usernamePolicy.suggestUsername("Betty", "Davis-Smith")).isEqualTo("betty.davissmith");
		assertThat(this.usernamePolicy.suggestUsername("", "Smith")).isEqualTo("smith");
		assertThat(this.usernamePolicy.suggestUsername("", "")).isEqualTo("owner");
	}

	@Test
	void testTemporaryPasswordGenerationAndExpiry() {
		String pwd1 = this.temporaryPasswordGenerator.generateTemporaryPassword();
		String pwd2 = this.temporaryPasswordGenerator.generateTemporaryPassword();

		assertThat(pwd1).hasSize(TemporaryPasswordGenerator.DEFAULT_PASSWORD_LENGTH);
		assertThat(pwd2).hasSize(TemporaryPasswordGenerator.DEFAULT_PASSWORD_LENGTH);
		assertThat(pwd1).isNotEqualTo(pwd2);

		Instant expiry = this.temporaryPasswordGenerator.calculateExpiryInstant();
		Instant now = Instant.now();
		assertThat(expiry).isAfter(now.plus(6, ChronoUnit.DAYS));
		assertThat(expiry).isBefore(now.plus(8, ChronoUnit.DAYS));
	}

	@Test
	void testSuccessfulProvisioning() {
		int ownerId = 1;
		String requestedUsername = "george.franklin";
		Long actorId = 100L;
		UUID commandId = UUID.randomUUID();

		when(this.commandService.findById(commandId)).thenReturn(Optional.empty());
		when(this.accountRepository.findByOwnerId(ownerId)).thenReturn(Optional.empty());
		when(this.accountRepository.existsByUsername(requestedUsername)).thenReturn(false);

		when(this.accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
			Account acc = invocation.getArgument(0);
			acc.setId(10L);
			return acc;
		});

		AccountProvisioningService.ProvisionAccountResult result = this.provisioningService
			.provisionOwnerAccount(ownerId, requestedUsername, actorId, commandId);

		assertThat(result.accountId()).isEqualTo(10L);
		assertThat(result.username()).isEqualTo(requestedUsername);
		assertThat(result.ownerId()).isEqualTo(ownerId);
		assertThat(result.temporaryPassword()).isNotBlank();
		assertThat(result.expiresAt()).isAfter(Instant.now());

		ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
		verify(this.accountRepository).save(accountCaptor.capture());
		Account saved = accountCaptor.getValue();
		assertThat(saved.getUsername()).isEqualTo(requestedUsername);
		assertThat(saved.getRole()).isEqualTo(Role.OWNER);
		assertThat(saved.getOwnerId()).isEqualTo(ownerId);
		assertThat(saved.isEnabled()).isTrue();
		assertThat(saved.isPasswordChangeRequired()).isTrue();
		assertThat(saved.getTemporaryPasswordExpiresAt()).isEqualTo(result.expiresAt());
		assertThat(this.passwordEncoder.matches(result.temporaryPassword(), saved.getPasswordHash())).isTrue();

		verify(this.auditService).recordEvent(eq(actorId), eq("ACCOUNT_PROVISIONED"), eq("Account"), eq("10"),
				eq("SUCCESS"), any(UUID.class), eq(commandId), isNull());
		verify(this.commandService).completeCommand(eq(commandId), any(CommandResult.class));
	}

	@Test
	void testProvisionDuplicateOwnerRejected() {
		int ownerId = 2;
		Account existing = new Account();
		existing.setId(20L);
		existing.setOwnerId(ownerId);

		when(this.accountRepository.findByOwnerId(ownerId)).thenReturn(Optional.of(existing));

		assertThatThrownBy(() -> this.provisioningService.provisionOwnerAccount(ownerId, "betty.davis", 100L, null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Account already exists for owner ID: 2");

		verify(this.accountRepository, never()).save(any());
	}

	@Test
	void testProvisionDuplicateUsernameRejected() {
		int ownerId = 3;
		String username = "taken.username";

		when(this.accountRepository.findByOwnerId(ownerId)).thenReturn(Optional.empty());
		when(this.accountRepository.existsByUsername(username)).thenReturn(true);

		assertThatThrownBy(() -> this.provisioningService.provisionOwnerAccount(ownerId, username, 100L, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Username is already in use");

		verify(this.accountRepository, never()).save(any());
	}

	@Test
	void testDuplicateCommandReplayReturnsCanonicalResult() {
		int ownerId = 4;
		UUID commandId = UUID.randomUUID();
		CommandRecord completedRecord = new CommandRecord();
		completedRecord.setId(commandId);
		completedRecord.setStatus(CommandStatus.COMPLETED);

		Account existingAccount = new Account();
		existingAccount.setId(40L);
		existingAccount.setUsername("harold.davis");
		existingAccount.setOwnerId(ownerId);
		existingAccount.setTemporaryPasswordExpiresAt(Instant.now().plus(5, ChronoUnit.DAYS));

		when(this.commandService.findById(commandId)).thenReturn(Optional.of(completedRecord));
		when(this.accountRepository.findByOwnerId(ownerId)).thenReturn(Optional.of(existingAccount));

		AccountProvisioningService.ProvisionAccountResult result = this.provisioningService
			.provisionOwnerAccount(ownerId, "harold.davis", 100L, commandId);

		assertThat(result.accountId()).isEqualTo(40L);
		assertThat(result.username()).isEqualTo("harold.davis");
		assertThat(result.temporaryPassword()).isNull(); // cleartext password only shown
															// on original creation
		assertThat(result.expiresAt()).isEqualTo(existingAccount.getTemporaryPasswordExpiresAt());

		verify(this.accountRepository, never()).save(any());
		verify(this.auditService, never()).recordEvent(any(), anyString(), anyString(), anyString(), anyString(), any(),
				any(), any());
	}

}
