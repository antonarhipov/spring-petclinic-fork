package org.springframework.samples.petclinic.account;

import org.springframework.samples.petclinic.scheduling.audit.AuditEvent;
import org.springframework.samples.petclinic.scheduling.audit.AuditService;
import org.springframework.stereotype.Service;

@Service
public class AccountAuditService {

	private final AuditService audit;

	public AccountAuditService(AuditService audit) {
		this.audit = audit;
	}

	public AuditEvent recordProvision(Long staffAccountId, Account account) {
		return this.audit.record("STAFF", staffAccountId, "ACCOUNT_PROVISIONED", "ACCOUNT", id(account), null, null,
				redacted(account));
	}

	public AuditEvent recordReset(Long staffAccountId, Account account) {
		return this.audit.record("STAFF", staffAccountId, "ACCOUNT_RESET", "ACCOUNT", id(account), null, null,
				redacted(account));
	}

	public AuditEvent recordPasswordChange(Account account) {
		return this.audit.record(account.getRole().name(), account.getId(), "PASSWORD_CHANGED", "ACCOUNT", id(account),
				null, null, redacted(account));
	}

	private static String id(Account account) {
		return account.getId() == null ? "" : String.valueOf(account.getId());
	}

	private static String redacted(Account account) {
		return "{\"changeRequired\":" + account.isMustChangePassword() + ",\"credentialVersion\":"
				+ account.getCredentialVersion() + ",\"role\":\"" + account.getRole() + "\"}";
	}

}
