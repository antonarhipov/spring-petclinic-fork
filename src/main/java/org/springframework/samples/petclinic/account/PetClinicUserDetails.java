package org.springframework.samples.petclinic.account;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class PetClinicUserDetails extends User {

	private final Long accountId;

	private final Integer ownerId;

	private final boolean mustChangePassword;

	public PetClinicUserDetails(Account account) {
		super(account.getUsername(), account.getPasswordHash(), account.isEnabled(), true, true, true,
				List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name())));
		this.accountId = account.getId();
		this.ownerId = account.getOwnerId();
		this.mustChangePassword = account.isMustChangePassword();
	}

	public Long getAccountId() {
		return this.accountId;
	}

	public Integer getOwnerId() {
		return this.ownerId;
	}

	public boolean isMustChangePassword() {
		return this.mustChangePassword;
	}

}
