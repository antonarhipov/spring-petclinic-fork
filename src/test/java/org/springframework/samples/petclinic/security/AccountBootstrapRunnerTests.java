package org.springframework.samples.petclinic.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccountBootstrapRunnerTests {

	@Mock
	private AccountRepository accountRepository;

	@Mock
	private AccountGuardRepository accountGuardRepository;

	@Mock
	private OwnerRepository ownerRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Test
	void demoProfileCreatesSpecifiedOwnerAccountsWithoutRequiredPasswordChange() throws Exception {
		MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "demo-data");
		environment.setActiveProfiles("demo-data");
		when(ownerRepository.findById(anyInt()))
			.thenAnswer(invocation -> Optional.of(owner(invocation.getArgument(0))));
		when(accountRepository.findByOwnerId(anyInt())).thenReturn(Optional.empty());
		when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> "encoded:" + invocation.getArgument(0));

		new AccountBootstrapRunner(environment, accountRepository, accountGuardRepository, ownerRepository,
				passwordEncoder)
			.run(new DefaultApplicationArguments());

		ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
		verify(accountRepository, org.mockito.Mockito.times(12)).save(accountCaptor.capture());
		List<Account> ownerAccounts = accountCaptor.getAllValues()
			.stream()
			.filter(account -> account.getRole() == AccountRole.OWNER)
			.toList();

		Map<String, String> expectedPasswords = new LinkedHashMap<>();
		expectedPasswords.put("george", "george123");
		expectedPasswords.put("betty", "betty123");
		expectedPasswords.put("eduardo", "eduardo123");
		expectedPasswords.put("harold", "harold123");
		expectedPasswords.put("peter", "peter123");
		expectedPasswords.put("jean", "jean123");
		expectedPasswords.put("jeff", "jeff123");
		expectedPasswords.put("maria", "maria123");
		expectedPasswords.put("david", "david123");
		expectedPasswords.put("carlos", "carlos123");

		assertThat(ownerAccounts).hasSize(10);
		assertThat(ownerAccounts).extracting(Account::getUsername)
			.containsExactlyElementsOf(expectedPasswords.keySet());
		assertThat(ownerAccounts).allSatisfy(account -> {
			assertThat(account.getPassword()).isEqualTo("encoded:" + expectedPasswords.get(account.getUsername()));
			assertThat(account.isPasswordChangeRequired()).isFalse();
			assertThat(account.isActive()).isTrue();
			assertThat(account.getOwner()).isNotNull();
		});
		assertThat(ownerAccounts).extracting(account -> account.getOwner().getId())
			.containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
	}

	@Test
	void demoProfilePreservesExistingAccounts() throws Exception {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("demo-data");
		when(accountRepository.existsByUsernameIgnoreCase(anyString())).thenReturn(true);

		new AccountBootstrapRunner(environment, accountRepository, accountGuardRepository, ownerRepository,
				passwordEncoder)
			.run(new DefaultApplicationArguments());

		verify(accountRepository, never()).save(org.mockito.ArgumentMatchers.any(Account.class));
		verify(passwordEncoder, never()).encode(anyString());
		verify(ownerRepository, never()).findById(anyInt());
	}

	private static Owner owner(Integer id) {
		Owner owner = new Owner();
		owner.setId(id);
		return owner;
	}

}
