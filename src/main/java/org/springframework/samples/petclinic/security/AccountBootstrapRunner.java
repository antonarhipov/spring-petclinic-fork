package org.springframework.samples.petclinic.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Component
public class AccountBootstrapRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AccountBootstrapRunner.class);

	private static final List<DemoOwnerAccount> DEMO_OWNER_ACCOUNTS = List.of(
		new DemoOwnerAccount("george", "george123", 1), new DemoOwnerAccount("betty", "betty123", 2),
		new DemoOwnerAccount("eduardo", "eduardo123", 3), new DemoOwnerAccount("harold", "harold123", 4),
		new DemoOwnerAccount("peter", "peter123", 5), new DemoOwnerAccount("jean", "jean123", 6),
		new DemoOwnerAccount("jeff", "jeff123", 7), new DemoOwnerAccount("maria", "maria123", 8),
		new DemoOwnerAccount("david", "david123", 9), new DemoOwnerAccount("carlos", "carlos123", 10));

	private final Environment environment;

	private final AccountRepository accountRepository;

	private final AccountGuardRepository accountGuardRepository;

	private final OwnerRepository ownerRepository;

	private final PasswordEncoder passwordEncoder;

	@Value("${petclinic.security.bootstrap-staff.username:}")
	private String bootstrapStaffUsername;

	@Value("${petclinic.security.bootstrap-staff.password:}")
	private String bootstrapStaffPassword;

	public AccountBootstrapRunner(Environment environment, AccountRepository accountRepository,
								  AccountGuardRepository accountGuardRepository, OwnerRepository ownerRepository,
								  PasswordEncoder passwordEncoder) {
		this.environment = environment;
		this.accountRepository = accountRepository;
		this.accountGuardRepository = accountGuardRepository;
		this.ownerRepository = ownerRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
		boolean isDemo = activeProfiles.contains("demo-data");
		boolean isProd = activeProfiles.contains("production") || activeProfiles.contains("prod");

		// UC1-AC26: Reject conflicting runtime profiles (demo-data + production)
		if (isDemo && isProd) {
			throw new IllegalStateException(
				"Profile conflict: 'demo-data' cannot be active together with 'production'");
		}

		// Ensure guard row exists
		accountGuardRepository.findByIdForUpdate(1);

		if (isDemo) {
			initializeDemoAccounts();
		} else {
			initializeNonDemoStaff();
		}
	}

	private void initializeDemoAccounts() {
		// Fixed demo staff accounts
		createDemoStaffIfMissing("staff1", "staff123");
		createDemoStaffIfMissing("staff2", "staff123");

		// Fixed demo owner accounts for the existing sample owners
		for (DemoOwnerAccount account : DEMO_OWNER_ACCOUNTS) {
			createDemoOwnerIfMissing(account.username(), account.rawPassword(), account.ownerId());
		}
	}

	private void createDemoStaffIfMissing(String username, String rawPassword) {
		String norm = username.toLowerCase(Locale.ROOT).trim();
		if (!accountRepository.existsByUsernameIgnoreCase(norm)) {
			Account staff = new Account();
			staff.setUsername(norm);
			staff.setPassword(passwordEncoder.encode(rawPassword));
			staff.setRole(AccountRole.STAFF);
			staff.setActive(true);
			staff.setPasswordChangeRequired(false); // UC1-AC23: demo accounts immediately
			// usable without forced change
			accountRepository.save(staff);
			log.info("Created demo staff account: {}", norm);
		}
	}

	private void createDemoOwnerIfMissing(String username, String rawPassword, int ownerId) {
		String norm = username.toLowerCase(Locale.ROOT).trim();
		if (!accountRepository.existsByUsernameIgnoreCase(norm)) {
			Owner owner = ownerRepository.findById(ownerId).orElse(null);
			if (owner != null && accountRepository.findByOwnerId(ownerId).isEmpty()) {
				Account ownerAcc = new Account();
				ownerAcc.setUsername(norm);
				ownerAcc.setPassword(passwordEncoder.encode(rawPassword));
				ownerAcc.setRole(AccountRole.OWNER);
				ownerAcc.setOwner(owner);
				ownerAcc.setActive(true);
				ownerAcc.setPasswordChangeRequired(false); // UC1-AC23: demo accounts
				// immediately usable
				accountRepository.save(ownerAcc);
				log.info("Created demo owner account: {} for ownerId: {}", norm, ownerId);
			}
		}
	}

	private void initializeNonDemoStaff() {
		long activeStaffCount = accountRepository.countByRoleAndActiveTrue(AccountRole.STAFF);
		if (activeStaffCount > 0) {
			return; // UC1-AC21: Preserve existing active staff account during bootstrap
		}

		if (bootstrapStaffUsername != null && !bootstrapStaffUsername.trim().isEmpty() && bootstrapStaffPassword != null
			&& bootstrapStaffPassword.length() >= 8) {
			String norm = bootstrapStaffUsername.toLowerCase(Locale.ROOT).trim();
			Account staff = new Account();
			staff.setUsername(norm);
			staff.setPassword(passwordEncoder.encode(bootstrapStaffPassword));
			staff.setRole(AccountRole.STAFF);
			staff.setActive(true);
			staff.setPasswordChangeRequired(true); // Initial staff must change password
			accountRepository.save(staff);
			log.info("Bootstrapped initial staff account: {}", norm);
		} else {
			// In test environments or when no staff exists and no credentials provided:
			// UC1-AC45: Refuse startup without an administrable staff identity
			// Note: if default profile is active, bootstrap credentials or bootstrap
			// default
			if ("true".equalsIgnoreCase(
				environment.getProperty("petclinic.security.allow-empty-staff-on-startup", "false"))) {
				log.warn("No active staff account present, but allow-empty-staff-on-startup is set to true");
				return;
			}
			// If not in a test that specifically disabled it, throw exception
			if (!environment.matchesProfiles("test")) {
				throw new IllegalStateException(
					"No active staff account found and valid bootstrap credentials (petclinic.security.bootstrap-staff.username/password) were not provided.");
			}
		}
	}

	private record DemoOwnerAccount(String username, String rawPassword, int ownerId) {
	}

}
