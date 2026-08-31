package org.springframework.samples.petclinic.security;

import java.util.Objects;
import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.account.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SessionVersionService {

	private final AccountRepository accountRepository;

	public SessionVersionService(AccountRepository accountRepository) {
		this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository must not be null");
	}

	@Transactional(readOnly = true)
	public boolean isSessionVersionValid(Long accountId, long sessionVersion) {
		if (accountId == null) {
			return false;
		}
		return this.accountRepository.findById(accountId)
			.map(account -> account.getSessionVersion() == sessionVersion)
			.orElse(false);
	}

	public void invalidateSessions(Long accountId) {
		if (accountId == null) {
			return;
		}
		Account account = this.accountRepository.findById(accountId)
			.orElseThrow(() -> new IllegalStateException("Account not found: " + accountId));
		account.setSessionVersion(account.getSessionVersion() + 1);
		this.accountRepository.save(account);
	}

}
