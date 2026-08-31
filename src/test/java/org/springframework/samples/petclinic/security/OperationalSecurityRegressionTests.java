package org.springframework.samples.petclinic.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.account.AccountRepository;
import org.springframework.samples.petclinic.account.Role;
import org.springframework.samples.petclinic.config.SensitiveLoggingConfiguration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OperationalSecurityRegressionTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private PetClinicPrincipal admin;

	private PetClinicPrincipal owner;

	@BeforeEach
	void setUp() {
		Account adminAccount = this.accountRepository.findByUsername("admin").orElseGet(() -> {
			Account acc = new Account();
			acc.setUsername("admin");
			acc.setPasswordHash(this.passwordEncoder.encode("admin123"));
			acc.setRole(Role.STAFF);
			acc.setEnabled(true);
			acc.setPasswordChangeRequired(false);
			acc.setSessionVersion(0L);
			return this.accountRepository.save(acc);
		});

		Account ownerAccount = this.accountRepository.findByUsername("george").orElseGet(() -> {
			Account acc = new Account();
			acc.setUsername("george");
			acc.setPasswordHash(this.passwordEncoder.encode("george123"));
			acc.setRole(Role.OWNER);
			acc.setOwnerId(1);
			acc.setEnabled(true);
			acc.setPasswordChangeRequired(false);
			acc.setSessionVersion(0L);
			return this.accountRepository.save(acc);
		});

		this.admin = SecurityTestPrincipals.staff(adminAccount.getId(), adminAccount.getUsername(),
				adminAccount.getSessionVersion(), false);
		this.owner = SecurityTestPrincipals.owner(ownerAccount.getId(), ownerAccount.getUsername(),
				ownerAccount.getOwnerId(), ownerAccount.getSessionVersion(), false);
	}

	@Test
	void testProtectedAccountRoutesHaveNoStoreHeaders() throws Exception {
		this.mockMvc.perform(get("/staff/owners/1/account").with(user(this.admin)))
			.andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0"));

		this.mockMvc.perform(get("/auth/password-change").with(user(this.owner)))
			.andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0"));
	}

	@Test
	void testSensitiveDataMaskerRedactsSensitiveInformation() {
		String rawLog = "User login attempt with password=SecretPassword123! and token=xyz987token";
		String masked = SensitiveLoggingConfiguration.maskSensitiveData(rawLog);
		assertThat(masked).doesNotContain("SecretPassword123!");
		assertThat(masked).doesNotContain("xyz987token");
		assertThat(masked).contains("password=[REDACTED]");
		assertThat(masked).contains("token=[REDACTED]");

		String prose = "My dog has been coughing since Tuesday";
		assertThat(SensitiveLoggingConfiguration.redactProse(prose)).isEqualTo("[REDACTED_PROSE len=38]");

		String clinical = "Prescribed 50mg amoxicillin BID for 7 days";
		assertThat(SensitiveLoggingConfiguration.redactClinical(clinical)).isEqualTo("[REDACTED_CLINICAL len=42]");

		assertThat(SensitiveLoggingConfiguration.redactCredential("rawPassword")).isEqualTo("[REDACTED_CREDENTIAL]");
		assertThat(SensitiveLoggingConfiguration.redactKey("aesKeyMaterial")).isEqualTo("[REDACTED_KEY]");
	}

	@Test
	void legacyCrashEndpointIsNotPubliclyAvailable() throws Exception {
		this.mockMvc.perform(get("/oups"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/auth/login"));
	}

}
