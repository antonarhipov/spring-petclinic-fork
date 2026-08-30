package org.springframework.samples.petclinic.account;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class PasswordService {

	private final AccountRepository accounts;

	private final PasswordEncoder encoder;

	private final Clock clock;

	private final AccountSessionService sessions;

	private final AccountAuditService audit;

	public PasswordService(AccountRepository accounts, PasswordEncoder encoder, Clock clock,
			AccountSessionService sessions, AccountAuditService audit) {
		this.accounts = accounts;
		this.encoder = encoder;
		this.clock = clock;
		this.sessions = sessions;
		this.audit = audit;
	}

	@Transactional
	public void changePassword(String username, String currentPassword, String newPassword, String confirmPassword,
			HttpServletRequest request) {
		if (newPassword == null || newPassword.length() < 6) {
			throw new PasswordChangeException("password.tooShort");
		}
		if (!newPassword.equals(confirmPassword)) {
			throw new PasswordChangeException("password.mismatch");
		}
		Account account = this.accounts.findByUsername(username.toLowerCase())
			.orElseThrow(() -> new PasswordChangeException("password.currentInvalid"));
		if (!this.encoder.matches(currentPassword, account.getPasswordHash())) {
			throw new PasswordChangeException("password.currentInvalid");
		}
		Instant now = Instant.now(this.clock);
		account.setPasswordHash(this.encoder.encode(newPassword));
		account.setMustChangePassword(false);
		account.setTemporaryCredentialExpiresAt(null);
		account.setCredentialVersion(account.getCredentialVersion() + 1);
		account.setUpdatedAt(now);
		this.accounts.save(account);
		this.sessions.rotateSessionId(request);
		this.audit.recordPasswordChange(account);
	}

}
