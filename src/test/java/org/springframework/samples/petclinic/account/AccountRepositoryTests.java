package org.springframework.samples.petclinic.account;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(AccountRepositoryTests.EncoderConfig.class)
class AccountRepositoryTests {

	@Autowired
	AccountRepository accounts;

	@Test
	void usernameIsUniqueAndPasswordIsBcrypt() {
		PasswordEncoder encoder = new BCryptPasswordEncoder();
		Account account = newAccount("george", encoder.encode("secret"), AccountRole.OWNER, 1);
		this.accounts.saveAndFlush(account);
		assertThat(account.getPasswordHash()).startsWith("$2");
		Account duplicate = newAccount("george", encoder.encode("other"), AccountRole.OWNER, 2);
		assertThatThrownBy(() -> this.accounts.saveAndFlush(duplicate))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void staffHasNoOwnerLink() {
		PasswordEncoder encoder = new BCryptPasswordEncoder();
		Account staff = newAccount("staffer", encoder.encode("secret"), AccountRole.STAFF, null);
		this.accounts.saveAndFlush(staff);
		assertThat(this.accounts.findByUsername("staffer")).isPresent();
		assertThat(staff.getOwnerId()).isNull();
		assertThat(staff.getRole()).isEqualTo(AccountRole.STAFF);
	}

	private static Account newAccount(String username, String hash, AccountRole role, Integer ownerId) {
		Account account = new Account();
		account.setUsername(username);
		account.setPasswordHash(hash);
		account.setRole(role);
		account.setOwnerId(ownerId);
		account.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
		account.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
		account.setEnabled(true);
		return account;
	}

	static class EncoderConfig {

	}

}
