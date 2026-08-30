package org.springframework.samples.petclinic.account;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class AccountBootstrapTests {

	@Autowired
	AccountRepository accounts;

	@Test
	void demoAndTestProfilesSeedAdminAndOwnerAccounts() {
		assertThat(this.accounts.findByUsername("admin")).isPresent();
		assertThat(this.accounts.findByUsername("george")).isPresent();
		assertThat(this.accounts.findByUsername("owner1")).isEmpty();
		Account admin = this.accounts.findByUsername("admin").orElseThrow();
		assertThat(admin.getRole()).isEqualTo(AccountRole.STAFF);
		assertThat(admin.getPasswordHash()).startsWith("$2");
		assertThat(admin.isMustChangePassword()).isFalse();
		PasswordEncoder encoder = new BCryptPasswordEncoder();
		assertThat(encoder.matches("admin123", admin.getPasswordHash())).isTrue();
		Account george = this.accounts.findByUsername("george").orElseThrow();
		assertThat(george.getRole()).isEqualTo(AccountRole.OWNER);
		assertThat(george.getOwnerId()).isEqualTo(1);
		assertThat(george.isMustChangePassword()).isFalse();
		assertThat(george.getTemporaryCredentialExpiresAt()).isNull();
		assertThat(encoder.matches("george123", george.getPasswordHash())).isTrue();
		assertThat(this.accounts.findAll().stream().filter(account -> account.getRole() == AccountRole.STAFF))
			.hasSize(1);
	}

	@Test
	void firstNameCollisionsUseNumericSuffixes() {
		AccountRepository repo = mock(AccountRepository.class);
		OwnerRepository owners = mock(OwnerRepository.class);
		when(repo.existsByUsername("admin")).thenReturn(false);
		when(repo.existsByUsername("george")).thenReturn(false, true);
		when(repo.existsByUsername("george2")).thenReturn(false);
		when(owners.findAll()).thenReturn(List.of(owner(1, "George"), owner(2, "George")));
		when(repo.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
		AccountBootstrap bootstrap = new AccountBootstrap(repo, owners, new BCryptPasswordEncoder(),
				Clock.fixed(Instant.parse("2026-03-16T12:00:00Z"), ZoneOffset.UTC), true, new InitialStaffProperties());
		bootstrap.run(mock(ApplicationArguments.class));
		verify(repo)
			.save(argThat(account -> "george".equals(account.getUsername()) && !account.isMustChangePassword()));
		verify(repo)
			.save(argThat(account -> "george2".equals(account.getUsername()) && !account.isMustChangePassword()));
	}

	@Test
	void deployedStartupCreatesNoOwnerSeedsAndRequiresProtectedInitialStaff() {
		AccountRepository repo = mock(AccountRepository.class);
		OwnerRepository owners = mock(OwnerRepository.class);
		InitialStaffProperties missing = new InitialStaffProperties();
		AccountBootstrap withoutConfig = new AccountBootstrap(repo, owners, new BCryptPasswordEncoder(),
				Clock.systemUTC(), false, missing);
		assertThatThrownBy(() -> withoutConfig.run(mock(ApplicationArguments.class)))
			.isInstanceOf(IllegalStateException.class);

		InitialStaffProperties demoPassword = new InitialStaffProperties();
		demoPassword.setUsername("ops");
		demoPassword.setPassword("admin123");
		AccountBootstrap demoRejected = new AccountBootstrap(repo, owners, new BCryptPasswordEncoder(),
				Clock.systemUTC(), false, demoPassword);
		assertThatThrownBy(() -> demoRejected.run(mock(ApplicationArguments.class)))
			.isInstanceOf(IllegalStateException.class);

		InitialStaffProperties protectedStaff = new InitialStaffProperties();
		protectedStaff.setUsername("ops");
		protectedStaff.setPassword("deployed-secret");
		when(repo.existsByUsername("ops")).thenReturn(false);
		when(repo.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
		AccountBootstrap deployed = new AccountBootstrap(repo, owners, new BCryptPasswordEncoder(), Clock.systemUTC(),
				false, protectedStaff);
		deployed.run(mock(ApplicationArguments.class));
		verify(owners, never()).findAll();
		verify(repo)
			.save(argThat(account -> "ops".equals(account.getUsername()) && account.getRole() == AccountRole.STAFF
					&& !account.isMustChangePassword() && account.getOwnerId() == null));
	}

	private static Owner owner(int id, String firstName) {
		Owner owner = new Owner();
		owner.setId(id);
		owner.setFirstName(firstName);
		owner.setLastName("Franklin");
		return owner;
	}

}
