package org.springframework.samples.petclinic.account;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SyntheticAccountSeeder {

	private static final Logger log = LoggerFactory.getLogger(SyntheticAccountSeeder.class);

	private final AccountRepository accountRepository;

	private final PasswordEncoder passwordEncoder;

	public SyntheticAccountSeeder(AccountRepository accountRepository, PasswordEncoder passwordEncoder) {
		this.accountRepository = accountRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void seedSyntheticAccounts() {
		seedStaff("admin", "admin123");
		seedStaff("staff2", "staff123");

		seedOwner("george", "george123", 1);
		seedOwner("betty", "betty123", 2);
		seedOwner("eduardo", "eduardo123", 3);
		seedOwner("harold", "harold123", 4);
		seedOwner("peter", "peter123", 5);
		seedOwner("jean", "jean123", 6);
		seedOwner("jeff", "jeff123", 7);
		seedOwner("maria", "maria123", 8);
		seedOwner("david", "david123", 9);
		seedOwner("carlos", "carlos123", 10);
	}

	private void seedStaff(String username, String rawPassword) {
		if (this.accountRepository.existsByUsername(username)) {
			return;
		}
		Account account = new Account();
		account.setUsername(username);
		account.setPasswordHash(this.passwordEncoder.encode(rawPassword));
		account.setRole(Role.STAFF);
		account.setOwnerId(null);
		account.setEnabled(true);
		account.setPasswordChangeRequired(false);
		account.setSessionVersion(0L);
		this.accountRepository.save(account);
		log.info("Seeded synthetic staff account: {}", username);
	}

	private void seedOwner(String username, String rawPassword, int ownerId) {
		if (this.accountRepository.existsByUsername(username)) {
			return;
		}
		if (this.accountRepository.findByOwnerId(ownerId).isPresent()) {
			return;
		}
		Account account = new Account();
		account.setUsername(username);
		account.setPasswordHash(this.passwordEncoder.encode(rawPassword));
		account.setRole(Role.OWNER);
		account.setOwnerId(ownerId);
		account.setEnabled(true);
		account.setPasswordChangeRequired(false);
		account.setSessionVersion(0L);
		this.accountRepository.save(account);
		log.info("Seeded synthetic owner account: {} (ownerId={})", username, ownerId);
	}

}
