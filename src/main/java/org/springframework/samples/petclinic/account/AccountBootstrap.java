package org.springframework.samples.petclinic.account;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@EnableConfigurationProperties(InitialStaffProperties.class)
public class AccountBootstrap implements ApplicationRunner {

	private static final String DEMO_STAFF_PASSWORD = "admin123";

	private final AccountRepository accounts;

	private final OwnerRepository owners;

	private final PasswordEncoder passwordEncoder;

	private final Clock clock;

	private final boolean bootstrapEnabled;

	private final InitialStaffProperties initialStaff;

	private final UsernameSuggester suggester = new UsernameSuggester();

	public AccountBootstrap(AccountRepository accounts, OwnerRepository owners, PasswordEncoder passwordEncoder,
			Clock clock, @Value("${petclinic.account.bootstrap-enabled:false}") boolean bootstrapEnabled,
			InitialStaffProperties initialStaff) {
		this.accounts = accounts;
		this.owners = owners;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
		this.bootstrapEnabled = bootstrapEnabled;
		this.initialStaff = initialStaff;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (this.bootstrapEnabled) {
			seedDemo();
			return;
		}
		seedDeployedStaff();
	}

	private void seedDemo() {
		Instant now = Instant.now(this.clock);
		if (!this.accounts.existsByUsername("admin")) {
			saveStaff("admin", DEMO_STAFF_PASSWORD, now);
		}
		List<Owner> allOwners = this.owners.findAll();
		for (Owner owner : allOwners) {
			if (this.accounts.findByOwnerId(owner.getId()).isPresent()) {
				continue;
			}
			String username = this.suggester.suggest(owner.getFirstName(), this.accounts::existsByUsername);
			Account account = new Account();
			account.setUsername(username);
			account.setPasswordHash(this.passwordEncoder.encode(username + "123"));
			account.setRole(AccountRole.OWNER);
			account.setOwnerId(owner.getId());
			account.setMustChangePassword(false);
			account.setEnabled(true);
			account.setCreatedAt(now);
			account.setUpdatedAt(now);
			this.accounts.save(account);
		}
	}

	private void seedDeployedStaff() {
		String username = this.initialStaff.getUsername() == null ? ""
				: this.suggester.normalize(this.initialStaff.getUsername());
		String password = this.initialStaff.getPassword();
		if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
			throw new IllegalStateException(
					"Deployed startup requires petclinic.account.initial-staff.username and password");
		}
		if (DEMO_STAFF_PASSWORD.equals(password)) {
			throw new IllegalStateException("Initial staff password must not be the demo password");
		}
		if (!this.accounts.existsByUsername(username)) {
			saveStaff(username, password, Instant.now(this.clock));
		}
	}

	private void saveStaff(String username, String rawPassword, Instant now) {
		Account admin = new Account();
		admin.setUsername(username);
		admin.setPasswordHash(this.passwordEncoder.encode(rawPassword));
		admin.setRole(AccountRole.STAFF);
		admin.setMustChangePassword(false);
		admin.setEnabled(true);
		admin.setCreatedAt(now);
		admin.setUpdatedAt(now);
		this.accounts.save(admin);
	}

}
