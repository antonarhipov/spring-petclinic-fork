package org.springframework.samples.petclinic.scheduling.web.owner;

import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.account.AccountRepository;
import org.springframework.samples.petclinic.scheduling.request.OwnerResourceNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class CurrentOwnerAccount {

	private final AccountRepository accounts;

	public CurrentOwnerAccount(AccountRepository accounts) {
		this.accounts = accounts;
	}

	public Account require(Authentication authentication) {
		if (authentication == null) {
			throw new OwnerResourceNotFoundException();
		}
		Account account = this.accounts.findByUsername(authentication.getName().toLowerCase())
			.orElseThrow(OwnerResourceNotFoundException::new);
		if (account.getOwnerId() == null) {
			throw new OwnerResourceNotFoundException();
		}
		return account;
	}

}
