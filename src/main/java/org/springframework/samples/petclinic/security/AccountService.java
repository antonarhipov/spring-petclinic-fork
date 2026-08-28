package org.springframework.samples.petclinic.security;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService implements UserDetailsService {

	private final AccountRepository accounts;

	private final PasswordEncoder passwordEncoder;

	private final Clock clock;

	private final int maximumAttempts;

	private final int lockMinutes;

	public AccountService(AccountRepository accounts, PasswordEncoder passwordEncoder, Clock clock,
			@Value("${scheduling.sign-in.max-attempts:5}") int maximumAttempts,
			@Value("${scheduling.sign-in.lock-minutes:15}") int lockMinutes) {
		this.accounts = accounts;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
		this.maximumAttempts = maximumAttempts;
		this.lockMinutes = lockMinutes;
	}

	@Override
	@Transactional
	public UserDetails loadUserByUsername(String username) {
		Account account = findAccount(username);
		Instant now = this.clock.instant();
		if (account.isLocked(now) || account.isTemporaryPasswordExpired(now)) {
			throw new DisabledException("Account is temporarily unavailable");
		}
		return User.withUsername(account.getUsername())
			.password(account.getPasswordHash())
			.authorities(new SimpleGrantedAuthority("ROLE_" + account.getRole().name()))
			.build();
	}

	@Transactional
	public Account provisionOwner(String username, String temporaryPassword,
			org.springframework.samples.petclinic.owner.Owner owner) {
		validatePassword(temporaryPassword);
		if (this.accounts.findByUsername(username.strip().toLowerCase()).isPresent()) {
			throw new IllegalArgumentException("Username is already in use");
		}
		Account account = new Account(username, this.passwordEncoder.encode(temporaryPassword), AccountRole.OWNER,
				owner);
		account.requirePasswordChangeUntil(this.clock.instant().plusSeconds(7 * 24 * 60 * 60));
		return this.accounts.save(account);
	}

	@Transactional
	public void changePassword(String username, String password) {
		validatePassword(password);
		findAccount(username).changePassword(this.passwordEncoder.encode(password));
	}

	@Transactional
	public void signInFailed(String username) {
		this.accounts.findByUsername(username.strip().toLowerCase())
			.ifPresent(account -> account.signInFailed(this.clock.instant(), this.maximumAttempts, this.lockMinutes));
	}

	@Transactional
	public void signInSucceeded(String username) {
		this.accounts.findByUsername(username.strip().toLowerCase()).ifPresent(Account::signInSucceeded);
	}

	private Account findAccount(String username) {
		return this.accounts.findByUsername(username.strip().toLowerCase())
			.orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
	}

	private void validatePassword(String password) {
		if (password == null || password.length() < 6) {
			throw new IllegalArgumentException("Password must contain at least six characters");
		}
	}

}
