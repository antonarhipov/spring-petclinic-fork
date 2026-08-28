package org.springframework.samples.petclinic.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerAccessService {

	private final AccountRepository accounts;

	public OwnerAccessService(AccountRepository accounts) {
		this.accounts = accounts;
	}

	@Transactional(readOnly = true)
	public AuthenticatedOwner currentOwner(Authentication authentication) {
		Account account = this.accounts.findByUsername(authentication.getName())
			.orElseThrow(() -> new AccessDeniedException("Account is not available"));
		if (account.getRole() != AccountRole.OWNER || account.getOwner() == null) {
			throw new AccessDeniedException("Owner access is required");
		}
		return new AuthenticatedOwner(account, account.getOwner());
	}

	@Transactional(readOnly = true)
	public void requireOwnerOf(Integer ownerId, Authentication authentication) {
		if (!currentOwner(authentication).owner().getId().equals(ownerId)) {
			throw new AccessDeniedException("Access denied");
		}
	}

}
