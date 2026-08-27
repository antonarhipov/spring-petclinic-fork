package org.springframework.samples.petclinic.security;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.scheduling.model.AuditEventType;
import org.springframework.samples.petclinic.scheduling.service.AuditService;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccountService {

	private final AccountRepository accountRepository;

	private final AccountGuardRepository accountGuardRepository;

	private final OwnerRepository ownerRepository;

	private final PasswordEncoder passwordEncoder;

	private final AuditService auditService;

	private final Clock clock;

	@Autowired(required = false)
	private SessionRegistry sessionRegistry;

	public AccountService(AccountRepository accountRepository, AccountGuardRepository accountGuardRepository,
			OwnerRepository ownerRepository, PasswordEncoder passwordEncoder, AuditService auditService, Clock clock) {
		this.accountRepository = accountRepository;
		this.accountGuardRepository = accountGuardRepository;
		this.ownerRepository = ownerRepository;
		this.passwordEncoder = passwordEncoder;
		this.auditService = auditService;
		this.clock = clock;
	}

	public void setSessionRegistry(SessionRegistry sessionRegistry) {
		this.sessionRegistry = sessionRegistry;
	}

	public Account createOwnerAccount(String username, String rawPassword, Integer ownerId, String actorUsername,
			String actorRole) {
		acquireAccountGuard();

		String normalizedUsername = normalizeUsername(username);
		validatePasswordLength(rawPassword);

		if (accountRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
			throw new IllegalArgumentException("Username already exists: " + username);
		}

		Owner owner = ownerRepository.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found: " + ownerId));

		if (accountRepository.findByOwnerId(ownerId).isPresent()) {
			throw new IllegalArgumentException("Owner already has an account: " + ownerId);
		}

		Account account = new Account();
		account.setUsername(normalizedUsername);
		account.setPassword(passwordEncoder.encode(rawPassword));
		account.setRole(AccountRole.OWNER);
		account.setOwner(owner);
		account.setActive(true);
		account.setPasswordChangeRequired(true);
		account.setFailedLoginCount(0);
		account.setCreatedAt(Instant.now(clock));
		account.setUpdatedAt(Instant.now(clock));

		Account saved = accountRepository.save(account);

		auditService.recordEvent(AuditEventType.ACCOUNT_CREATED, actorUsername, actorRole, "ACCOUNT",
				String.valueOf(saved.getId()), null,
				Map.of("username", normalizedUsername, "role", "OWNER", "ownerId", ownerId));

		return saved;
	}

	public Account createStaffAccount(String username, String rawPassword, String actorUsername, String actorRole) {
		acquireAccountGuard();

		String normalizedUsername = normalizeUsername(username);
		validatePasswordLength(rawPassword);

		if (accountRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
			throw new IllegalArgumentException("Username already exists: " + username);
		}

		Account account = new Account();
		account.setUsername(normalizedUsername);
		account.setPassword(passwordEncoder.encode(rawPassword));
		account.setRole(AccountRole.STAFF);
		account.setOwner(null);
		account.setActive(true);
		account.setPasswordChangeRequired(true);
		account.setFailedLoginCount(0);
		account.setCreatedAt(Instant.now(clock));
		account.setUpdatedAt(Instant.now(clock));

		Account saved = accountRepository.save(account);

		auditService.recordEvent(AuditEventType.ACCOUNT_CREATED, actorUsername, actorRole, "ACCOUNT",
				String.valueOf(saved.getId()), null, Map.of("username", normalizedUsername, "role", "STAFF"));

		return saved;
	}

	public void resetPassword(Integer accountId, String rawTemporaryPassword, String actorUsername, String actorRole) {
		validatePasswordLength(rawTemporaryPassword);

		Account account = accountRepository.findById(accountId)
			.orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

		account.setPassword(passwordEncoder.encode(rawTemporaryPassword));
		account.setPasswordChangeRequired(true);
		account.setUpdatedAt(Instant.now(clock));
		accountRepository.save(account);

		invalidateSessionsForUser(account.getUsername());

		auditService.recordEvent(AuditEventType.PASSWORD_RESET, actorUsername, actorRole, "ACCOUNT",
				String.valueOf(account.getId()), null, Map.of("username", account.getUsername()));
	}

	public void changePassword(Integer accountId, String oldPassword, String newPassword, String actorUsername,
			String actorRole) {
		validatePasswordLength(newPassword);

		Account account = accountRepository.findById(accountId)
			.orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

		if (oldPassword != null && !passwordEncoder.matches(oldPassword, account.getPassword())) {
			throw new IllegalArgumentException("Current password does not match");
		}

		if (passwordEncoder.matches(newPassword, account.getPassword())) {
			throw new IllegalArgumentException("Replacement password cannot be the same as the current password");
		}

		account.setPassword(passwordEncoder.encode(newPassword));
		account.setPasswordChangeRequired(false);
		account.setUpdatedAt(Instant.now(clock));
		accountRepository.save(account);

		invalidateSessionsForUser(account.getUsername());

		auditService.recordEvent(AuditEventType.PASSWORD_CHANGED, actorUsername, actorRole, "ACCOUNT",
				String.valueOf(account.getId()), null, Map.of("username", account.getUsername()));
	}

	public void renameAccount(Integer accountId, String newUsername, String actorUsername, String actorRole) {
		acquireAccountGuard();

		String normalizedUsername = normalizeUsername(newUsername);

		Account account = accountRepository.findById(accountId)
			.orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

		if (!account.getUsername().equalsIgnoreCase(normalizedUsername)
				&& accountRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
			throw new IllegalArgumentException("Username already exists: " + newUsername);
		}

		String oldUsername = account.getUsername();
		account.setUsername(normalizedUsername);
		account.setUpdatedAt(Instant.now(clock));
		accountRepository.save(account);

		invalidateSessionsForUser(oldUsername);
		invalidateSessionsForUser(normalizedUsername);

		auditService.recordEvent(AuditEventType.ACCOUNT_RENAMED, actorUsername, actorRole, "ACCOUNT",
				String.valueOf(account.getId()), null,
				Map.of("oldUsername", oldUsername, "newUsername", normalizedUsername));
	}

	public void deactivateAccount(Integer accountId, String actorUsername, String actorRole) {
		acquireAccountGuard();

		Account account = accountRepository.findById(accountId)
			.orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

		if (account.getRole() == AccountRole.STAFF && account.isActive()) {
			long activeStaff = accountRepository.countByRoleAndActiveTrue(AccountRole.STAFF);
			if (activeStaff <= 1) {
				throw new IllegalStateException("Cannot deactivate the final active staff account");
			}
		}

		account.setActive(false);
		account.setUpdatedAt(Instant.now(clock));
		accountRepository.save(account);

		invalidateSessionsForUser(account.getUsername());

		auditService.recordEvent(AuditEventType.ACCOUNT_DEACTIVATED, actorUsername, actorRole, "ACCOUNT",
				String.valueOf(account.getId()), null, Map.of("username", account.getUsername()));
	}

	public void unlockAccount(Integer accountId, String actorUsername, String actorRole) {
		Account account = accountRepository.findById(accountId)
			.orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

		account.setFailedLoginCount(0);
		account.setLockedUntil(null);
		account.setUpdatedAt(Instant.now(clock));
		accountRepository.save(account);

		auditService.recordEvent(AuditEventType.ACCOUNT_UNLOCKED, actorUsername, actorRole, "ACCOUNT",
				String.valueOf(account.getId()), null, Map.of("username", account.getUsername()));
	}

	public void recordFailedLogin(String username) {
		Optional<Account> opt = accountRepository.findByUsernameIgnoreCaseForUpdate(username);
		if (opt.isPresent()) {
			Account account = opt.get();
			Instant now = Instant.now(clock);
			if (account.getLockedUntil() != null && now.isBefore(account.getLockedUntil())) {
				// Already locked
				return;
			}

			int newCount = account.getFailedLoginCount() + 1;
			account.setFailedLoginCount(newCount);
			if (newCount >= 5) {
				account.setLockedUntil(now.plus(15, ChronoUnit.MINUTES));
				auditService.recordEvent(AuditEventType.LOGIN_LOCKED, account.getUsername(), account.getRole().name(),
						"ACCOUNT", String.valueOf(account.getId()), "MAX_FAILED_ATTEMPTS",
						Map.of("username", account.getUsername(), "failedAttempts", newCount));
			}

			account.setUpdatedAt(now);
			accountRepository.save(account);

			auditService.recordEvent(AuditEventType.LOGIN_FAILED, account.getUsername(), account.getRole().name(),
					"ACCOUNT", String.valueOf(account.getId()), "INVALID_CREDENTIALS",
					Map.of("username", account.getUsername(), "failedAttempts", newCount));
		}
	}

	public void recordSuccessfulLogin(String username) {
		Optional<Account> opt = accountRepository.findByUsernameIgnoreCaseForUpdate(username);
		if (opt.isPresent()) {
			Account account = opt.get();
			account.setFailedLoginCount(0);
			account.setLockedUntil(null);
			account.setUpdatedAt(Instant.now(clock));
			accountRepository.save(account);

			auditService.recordEvent(AuditEventType.LOGIN_SUCCESS, account.getUsername(), account.getRole().name(),
					"ACCOUNT", String.valueOf(account.getId()), null, Map.of("username", account.getUsername()));
		}
	}

	@Transactional(readOnly = true)
	public Optional<Account> findById(Integer id) {
		return accountRepository.findById(id);
	}

	@Transactional(readOnly = true)
	public Optional<Account> findByUsername(String username) {
		return accountRepository.findByUsernameIgnoreCase(username);
	}

	@Transactional(readOnly = true)
	public List<Account> findAll() {
		return accountRepository.findAllByOrderByUsernameAsc();
	}

	private void acquireAccountGuard() {
		accountGuardRepository.findByIdForUpdate(1);
	}

	private String normalizeUsername(String username) {
		if (username == null || username.trim().isEmpty()) {
			throw new IllegalArgumentException("Username cannot be blank");
		}
		return username.toLowerCase(Locale.ROOT).trim();
	}

	private void validatePasswordLength(String rawPassword) {
		if (rawPassword == null || rawPassword.length() < 8) {
			throw new IllegalArgumentException("Password must be at least 8 characters long");
		}
	}

	private void invalidateSessionsForUser(String username) {
		if (sessionRegistry == null) {
			return;
		}
		for (Object principal : sessionRegistry.getAllPrincipals()) {
			String pUser = null;
			if (principal instanceof UserPrincipal up) {
				pUser = up.getUsername();
			}
			else if (principal instanceof UserDetails ud) {
				pUser = ud.getUsername();
			}
			else if (principal instanceof String str) {
				pUser = str;
			}
			if (pUser != null && pUser.equalsIgnoreCase(username)) {
				for (SessionInformation session : sessionRegistry.getAllSessions(principal, false)) {
					session.expireNow();
				}
			}
		}
	}

}
