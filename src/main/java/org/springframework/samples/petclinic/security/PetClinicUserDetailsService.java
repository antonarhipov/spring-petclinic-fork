package org.springframework.samples.petclinic.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PetClinicUserDetailsService implements UserDetailsService {

	private final AccountRepository accountRepository;

	public PetClinicUserDetailsService(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		Account account = accountRepository.findByUsernameIgnoreCase(username)
			.orElseThrow(() -> new UsernameNotFoundException("Account not found: " + username));
		return UserPrincipal.fromAccount(account);
	}

}
