package org.springframework.samples.petclinic.security;

import java.time.Clock;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.samples.petclinic.owner.OwnerRepository;

@Component
@Profile({ "default", "local", "test" })
class DemoAccountInitializer implements CommandLineRunner {

	private final AccountRepository accounts;

	private final OwnerRepository owners;

	private final PasswordEncoder passwordEncoder;

	DemoAccountInitializer(AccountRepository accounts, OwnerRepository owners, PasswordEncoder passwordEncoder,
			Clock clock) {
		this.accounts = accounts;
		this.owners = owners;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	@Transactional
	public void run(String... args) {
		this.owners.findAll().forEach(owner -> {
			String username = owner.getFirstName().toLowerCase();
			this.accounts.findByUsername(username)
				.orElseGet(() -> this.accounts.save(new Account(username, this.passwordEncoder.encode(username + "123"),
						AccountRole.OWNER, owner)));
		});
		this.accounts.findByUsername("admin")
			.orElseGet(() -> this.accounts
				.save(new Account("admin", this.passwordEncoder.encode("admin123"), AccountRole.STAFF, null)));
	}

}
