package org.springframework.samples.petclinic.account;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class PetClinicUserDetailsService implements UserDetailsService {

	private final AccountRepository accounts;

	private final Clock clock;

	public PetClinicUserDetailsService(AccountRepository accounts, Clock clock) {
		this.accounts = accounts;
		this.clock = clock;
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		Account account = this.accounts.findByUsername(username.toLowerCase(Locale.ROOT))
			.orElseThrow(() -> new UsernameNotFoundException(username));
		Instant expiresAt = account.getTemporaryCredentialExpiresAt();
		if (expiresAt != null && !expiresAt.isAfter(Instant.now(this.clock))) {
			throw new UsernameNotFoundException(username);
		}
		return new PetClinicUserDetails(account);
	}

}
