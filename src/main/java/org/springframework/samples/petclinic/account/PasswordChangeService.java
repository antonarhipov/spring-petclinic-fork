package org.springframework.samples.petclinic.account;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.samples.petclinic.audit.AuditService;
import org.springframework.samples.petclinic.audit.OwnerHistoryService;
import org.springframework.samples.petclinic.security.PetClinicPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PasswordChangeService {

	public static final int MIN_PASSWORD_LENGTH = 8;

	private final AccountRepository accountRepository;

	private final PasswordEncoder passwordEncoder;

	private final AuditService auditService;

	private final OwnerHistoryService ownerHistoryService;

	public PasswordChangeService(AccountRepository accountRepository, PasswordEncoder passwordEncoder,
			AuditService auditService, OwnerHistoryService ownerHistoryService) {
		this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository must not be null");
		this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder must not be null");
		this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
		this.ownerHistoryService = Objects.requireNonNull(ownerHistoryService, "ownerHistoryService must not be null");
	}

	public void changePassword(Long accountId, String currentPassword, String newPassword, String confirmPassword,
			HttpServletRequest request) {
		if (accountId == null) {
			throw new IllegalArgumentException("Account ID must not be null");
		}
		if (currentPassword == null || currentPassword.isBlank()) {
			throw new IllegalArgumentException("Current password must not be blank");
		}
		if (newPassword == null || newPassword.isBlank()) {
			throw new IllegalArgumentException("New password must not be blank");
		}
		if (!newPassword.equals(confirmPassword)) {
			throw new IllegalArgumentException("New password and confirmation do not match");
		}
		if (newPassword.length() < MIN_PASSWORD_LENGTH) {
			throw new IllegalArgumentException(
					"New password must be at least " + MIN_PASSWORD_LENGTH + " characters long");
		}
		if (currentPassword.equals(newPassword)) {
			throw new IllegalArgumentException("New password must be different from current password");
		}

		Account account = this.accountRepository.findById(accountId)
			.orElseThrow(() -> new IllegalArgumentException("Account not found for id: " + accountId));

		if (account.getTemporaryPasswordExpiresAt() != null
				&& Instant.now().isAfter(account.getTemporaryPasswordExpiresAt())) {
			throw new IllegalStateException("Temporary password has expired. Please contact staff to reset access.");
		}

		if (!this.passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
			throw new IllegalArgumentException("Current password is incorrect");
		}

		account.setPasswordHash(this.passwordEncoder.encode(newPassword));
		account.setPasswordChangeRequired(false);
		account.setTemporaryPasswordExpiresAt(null);
		long newSessionVersion = account.getSessionVersion() + 1;
		account.setSessionVersion(newSessionVersion);

		Account saved = this.accountRepository.save(account);

		// Rotate session ID and refresh principal in SecurityContext
		if (request != null) {
			try {
				request.changeSessionId();
			}
			catch (IllegalStateException ignored) {
				// No session or session already invalidated
			}

			PetClinicPrincipal updatedPrincipal = new PetClinicPrincipal(saved.getId(), saved.getUsername(),
					saved.getPasswordHash(), saved.getRole(), saved.getOwnerId(), saved.isEnabled(), null, false,
					newSessionVersion);

			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			if (auth != null) {
				UsernamePasswordAuthenticationToken newAuth = new UsernamePasswordAuthenticationToken(updatedPrincipal,
						auth.getCredentials(), updatedPrincipal.getAuthorities());
				SecurityContextHolder.getContext().setAuthentication(newAuth);
			}
		}

		this.auditService.recordEvent(saved.getId(), "PASSWORD_CHANGED", "Account", saved.getId().toString(), "SUCCESS",
				UUID.randomUUID(), null, null);

		if (saved.getRole() == Role.OWNER && saved.getOwnerId() != null) {
			this.ownerHistoryService.recordEvent(saved.getOwnerId(), "PASSWORD_CHANGED",
					"Account password was changed successfully", "Account", saved.getId().toString());
		}
	}

}
