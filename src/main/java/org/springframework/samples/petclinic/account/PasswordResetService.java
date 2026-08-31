package org.springframework.samples.petclinic.account;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.samples.petclinic.audit.AuditService;
import org.springframework.samples.petclinic.shared.command.CommandRecord;
import org.springframework.samples.petclinic.shared.command.CommandResult;
import org.springframework.samples.petclinic.shared.command.CommandService;
import org.springframework.samples.petclinic.shared.command.CommandStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PasswordResetService {

	private final AccountRepository accountRepository;

	private final PasswordEncoder passwordEncoder;

	private final TemporaryPasswordGenerator temporaryPasswordGenerator;

	private final AuditService auditService;

	private final CommandService commandService;

	public PasswordResetService(AccountRepository accountRepository, PasswordEncoder passwordEncoder,
			TemporaryPasswordGenerator temporaryPasswordGenerator, AuditService auditService,
			CommandService commandService) {
		this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository must not be null");
		this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder must not be null");
		this.temporaryPasswordGenerator = Objects.requireNonNull(temporaryPasswordGenerator,
				"temporaryPasswordGenerator must not be null");
		this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
		this.commandService = Objects.requireNonNull(commandService, "commandService must not be null");
	}

	public PasswordResetResult resetPassword(Long accountId, Long actorAccountId, UUID commandId) {
		if (accountId == null) {
			throw new IllegalArgumentException("Account ID must not be null");
		}

		Account account = this.accountRepository.findById(accountId)
			.orElseThrow(() -> new IllegalArgumentException("Account not found for id: " + accountId));

		if (commandId != null) {
			String requestHash = CommandService.computeRequestHash("reset:" + accountId);
			Optional<CommandRecord> existing = this.commandService.findById(commandId);
			if (existing.isPresent() && existing.get().getStatus() == CommandStatus.COMPLETED) {
				return new PasswordResetResult(account.getId(), account.getUsername(), null,
						account.getTemporaryPasswordExpiresAt());
			}
			this.commandService.issueToken(commandId, actorAccountId, "RESET_PASSWORD", "account:" + accountId,
					requestHash);
		}

		String tempPassword = this.temporaryPasswordGenerator.generateTemporaryPassword();
		Instant expiresAt = this.temporaryPasswordGenerator.calculateExpiryInstant();

		account.setPasswordHash(this.passwordEncoder.encode(tempPassword));
		account.setPasswordChangeRequired(true);
		account.setTemporaryPasswordExpiresAt(expiresAt);
		account.setSessionVersion(account.getSessionVersion() + 1);

		Account saved = this.accountRepository.save(account);

		this.auditService.recordEvent(actorAccountId, "PASSWORD_RESET", "Account", saved.getId().toString(), "SUCCESS",
				UUID.randomUUID(), commandId, null);

		if (commandId != null) {
			String location = saved.getOwnerId() != null ? "/staff/owners/" + saved.getOwnerId() + "/account"
					: "/staff/accounts/" + saved.getId();
			this.commandService.completeCommand(commandId,
					CommandResult.ok("Account", saved.getId().toString(), location));
		}

		return new PasswordResetResult(saved.getId(), saved.getUsername(), tempPassword, expiresAt);
	}

	public record PasswordResetResult(Long accountId, String username, String temporaryPassword, Instant expiresAt) {
	}

}
