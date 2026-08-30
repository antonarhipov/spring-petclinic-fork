package org.springframework.samples.petclinic.account;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.system.StaleStateException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountProvisioningService {

	private static final Duration ONE_TIME_TTL = Duration.ofDays(7);

	private final AccountRepository accounts;

	private final OwnerRepository owners;

	private final PasswordEncoder encoder;

	private final Clock clock;

	private final UsernameSuggester suggester;

	private final OneTimePasswordGenerator passwords;

	private final AccountAuditService audit;

	private final AccountSessionService sessions;

	public AccountProvisioningService(AccountRepository accounts, OwnerRepository owners, PasswordEncoder encoder,
			Clock clock, UsernameSuggester suggester, OneTimePasswordGenerator passwords, AccountAuditService audit,
			AccountSessionService sessions) {
		this.accounts = accounts;
		this.owners = owners;
		this.encoder = encoder;
		this.clock = clock;
		this.suggester = suggester;
		this.passwords = passwords;
		this.audit = audit;
		this.sessions = sessions;
	}

	@Transactional
	public OneTimeCredentialResult provision(Integer ownerId, String username, Integer expectedVersion,
			Long staffAccountId) {
		Owner owner = this.owners.findById(ownerId).orElseThrow();
		if (this.accounts.findByOwnerId(owner.getId()).isPresent()) {
			throw new AccountConflictException("Owner already has an account");
		}
		String normalized = this.suggester.normalize(username);
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("Username is required");
		}
		if (this.accounts.existsByUsername(normalized)) {
			throw new DuplicateUsernameException();
		}
		Instant now = Instant.now(this.clock);
		String oneTime = this.passwords.generate();
		Account account = new Account();
		account.setUsername(normalized);
		account.setPasswordHash(this.encoder.encode(oneTime));
		account.setRole(AccountRole.OWNER);
		account.setOwnerId(owner.getId());
		account.setMustChangePassword(true);
		account.setTemporaryCredentialExpiresAt(now.plus(ONE_TIME_TTL));
		account.setCredentialVersion(0);
		account.setEnabled(true);
		account.setCreatedAt(now);
		account.setUpdatedAt(now);
		this.accounts.save(account);
		this.audit.recordProvision(staffAccountId, account);
		return new OneTimeCredentialResult(normalized, oneTime, account.getTemporaryCredentialExpiresAt());
	}

	@Transactional
	public OneTimeCredentialResult reset(Integer ownerId, Integer expectedVersion, boolean confirmed,
			Long staffAccountId) {
		if (!confirmed) {
			throw new IllegalArgumentException("Reset confirmation is required");
		}
		Account account = this.accounts.findByOwnerId(ownerId).orElseThrow();
		if (!Objects.equals(account.getVersion(), expectedVersion)) {
			throw new StaleStateException("The page is out of date.", account.getVersion(), expectedVersion);
		}
		Instant now = Instant.now(this.clock);
		String oneTime = this.passwords.generate();
		account.setPasswordHash(this.encoder.encode(oneTime));
		account.setMustChangePassword(true);
		account.setTemporaryCredentialExpiresAt(now.plus(ONE_TIME_TTL));
		account.setCredentialVersion(account.getCredentialVersion() + 1);
		account.setUpdatedAt(now);
		this.accounts.save(account);
		this.sessions.invalidateAllSessions(account.getUsername());
		this.audit.recordReset(staffAccountId, account);
		return new OneTimeCredentialResult(account.getUsername(), oneTime, account.getTemporaryCredentialExpiresAt());
	}

}
