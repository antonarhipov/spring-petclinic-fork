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
public class AccountProvisioningService {

	private final AccountRepository accountRepository;

	private final PasswordEncoder passwordEncoder;

	private final TemporaryPasswordGenerator temporaryPasswordGenerator;

	private final UsernamePolicy usernamePolicy;

	private final AuditService auditService;

	private final CommandService commandService;

	public AccountProvisioningService(AccountRepository accountRepository, PasswordEncoder passwordEncoder,
			TemporaryPasswordGenerator temporaryPasswordGenerator, UsernamePolicy usernamePolicy,
			AuditService auditService, CommandService commandService) {
		this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository must not be null");
		this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder must not be null");
		this.temporaryPasswordGenerator = Objects.requireNonNull(temporaryPasswordGenerator,
				"temporaryPasswordGenerator must not be null");
		this.usernamePolicy = Objects.requireNonNull(usernamePolicy, "usernamePolicy must not be null");
		this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
		this.commandService = Objects.requireNonNull(commandService, "commandService must not be null");
	}

	public ProvisionAccountResult provisionOwnerAccount(Integer ownerId, String requestedUsername, Long actorAccountId,
			UUID commandId) {
		if (ownerId == null) {
			throw new IllegalArgumentException("Owner ID must not be null");
		}

		String normalizedUsername = this.usernamePolicy.normalize(requestedUsername);
		this.usernamePolicy.validate(normalizedUsername);

		if (commandId != null) {
			String requestHash = CommandService.computeRequestHash(ownerId + ":" + normalizedUsername);
			Optional<CommandRecord> existing = this.commandService.findById(commandId);
			if (existing.isPresent() && existing.get().getStatus() == CommandStatus.COMPLETED) {
				Account account = this.accountRepository.findByOwnerId(ownerId)
					.orElseThrow(() -> new IllegalStateException("Provisioned account not found for replay"));
				return new ProvisionAccountResult(account.getId(), account.getUsername(), null,
						account.getTemporaryPasswordExpiresAt(), ownerId);
			}
			this.commandService.issueToken(commandId, actorAccountId, "PROVISION_OWNER_ACCOUNT", "owner:" + ownerId,
					requestHash);
		}

		if (this.accountRepository.findByOwnerId(ownerId).isPresent()) {
			throw new IllegalStateException("Account already exists for owner ID: " + ownerId);
		}

		if (this.accountRepository.existsByUsername(normalizedUsername)) {
			throw new IllegalArgumentException("Username is already in use: " + normalizedUsername);
		}

		String tempPassword = this.temporaryPasswordGenerator.generateTemporaryPassword();
		Instant expiresAt = this.temporaryPasswordGenerator.calculateExpiryInstant();

		Account account = new Account();
		account.setUsername(normalizedUsername);
		account.setPasswordHash(this.passwordEncoder.encode(tempPassword));
		account.setRole(Role.OWNER);
		account.setOwnerId(ownerId);
		account.setEnabled(true);
		account.setPasswordChangeRequired(true);
		account.setTemporaryPasswordExpiresAt(expiresAt);
		account.setSessionVersion(0L);

		Account saved = this.accountRepository.save(account);

		this.auditService.recordEvent(actorAccountId, "ACCOUNT_PROVISIONED", "Account", saved.getId().toString(),
				"SUCCESS", UUID.randomUUID(), commandId, null);

		if (commandId != null) {
			this.commandService.completeCommand(commandId, CommandResult.created("Account", saved.getId().toString(),
					"/staff/owners/" + ownerId + "/account"));
		}

		return new ProvisionAccountResult(saved.getId(), saved.getUsername(), tempPassword, expiresAt, ownerId);
	}

	public record ProvisionAccountResult(Long accountId, String username, String temporaryPassword, Instant expiresAt,
			Integer ownerId) {
	}

}
