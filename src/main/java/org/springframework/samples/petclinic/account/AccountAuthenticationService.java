package org.springframework.samples.petclinic.account;

import java.util.Objects;
import org.springframework.samples.petclinic.security.PetClinicPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccountAuthenticationService implements UserDetailsService {

	private final AccountRepository accountRepository;

	public AccountAuthenticationService(AccountRepository accountRepository) {
		this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository must not be null");
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		if (username == null || username.isBlank()) {
			throw new UsernameNotFoundException("Username must not be empty");
		}
		String normalized = username.trim().toLowerCase();
		Account account = this.accountRepository.findByUsername(normalized)
			.orElseThrow(() -> new UsernameNotFoundException("Account not found for username: " + username));

		return new PetClinicPrincipal(account.getId(), account.getUsername(), account.getPasswordHash(),
				account.getRole(), account.getOwnerId(), account.isEnabled(), account.getTemporaryPasswordExpiresAt(),
				account.isPasswordChangeRequired(), account.getSessionVersion());
	}

}
