package org.springframework.samples.petclinic.account;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.samples.petclinic.security.PetClinicPrincipal;
import org.springframework.samples.petclinic.security.SecurityTestPrincipals;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountControllerTests {

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
	void testStaffCanAccessAccountManagementFormWithNoStoreHeaders() throws Exception {
		this.mockMvc.perform(get("/staff/owners/1/account").with(user(this.admin)))
			.andExpect(status().isOk())
			.andExpect(view().name("staff/owners/account-form"))
			.andExpect(header().string("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0"));
	}

	@Test
	void newOwnerAccountFormSuggestsUsernameFromNormalizedOwnerName() throws Exception {
		this.accountRepository.findByOwnerId(10).ifPresent(this.accountRepository::delete);

		this.mockMvc.perform(get("/staff/owners/10/account").with(user(this.admin)))
			.andExpect(status().isOk())
			.andExpect(model().attribute("suggestedUsername", "carlos.estaban"));
	}

	@Test
	void testOwnerCannotAccessStaffAccountManagement() throws Exception {
		this.mockMvc.perform(get("/staff/owners/1/account").with(user(this.owner))).andExpect(status().isForbidden());
	}

	@Test
	void testAnonymousUserRedirectedFromStaffAccountManagement() throws Exception {
		this.mockMvc.perform(get("/staff/owners/1/account"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/auth/login"));
	}

	@Test
	void testStaffCanProvisionAccount() throws Exception {
		int existingOwnerId = 10; // Owner 10 (Carlos) exists in data.sql
		this.accountRepository.findByOwnerId(existingOwnerId).ifPresent(this.accountRepository::delete);

		this.mockMvc
			.perform(post("/staff/owners/" + existingOwnerId + "/account/provision").with(user(this.admin))
				.with(csrf())
				.param("username", "carlos.newuser")
				.param("commandId", UUID.randomUUID().toString()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/owners/" + existingOwnerId + "/account/result"))
			.andExpect(header().string("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0"));
	}

	@Test
	void testStaffCanResetPassword() throws Exception {
		this.mockMvc
			.perform(post("/staff/owners/1/account/reset").with(user(this.admin))
				.with(csrf())
				.param("commandId", UUID.randomUUID().toString()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/owners/1/account/result"))
			.andExpect(header().string("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0"));
	}

	@Test
	void testPasswordChangeFormAccessibleToAuthenticatedUser() throws Exception {
		this.mockMvc.perform(get("/auth/password-change").with(user(this.owner)))
			.andExpect(status().isOk())
			.andExpect(view().name("auth/password-change"))
			.andExpect(header().string("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0"));
	}

	@Test
	void testSuccessfulPasswordChangeExecution() throws Exception {
		this.mockMvc
			.perform(post("/auth/password-change").with(user(this.owner))
				.with(csrf())
				.param("currentPassword", "george123")
				.param("newPassword", "brandNewPassword999#")
				.param("confirmPassword", "brandNewPassword999#"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/owner/dashboard"));
	}

	@Test
	void successfulStaffPasswordChangeRedirectsToQueue() throws Exception {
		this.mockMvc
			.perform(post("/auth/password-change").with(user(this.admin))
				.with(csrf())
				.param("currentPassword", "admin123")
				.param("newPassword", "staffPassword999#")
				.param("confirmPassword", "staffPassword999#"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/queue"));
	}

	@Test
	void staffRootRedirectsToQueue() throws Exception {
		this.mockMvc.perform(get("/staff").with(user(this.admin)))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/queue"));
	}

	@Test
	void staffLoginRedirectsToQueue() throws Exception {
		this.mockMvc.perform(formLogin("/auth/login").user("admin").password("admin123"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/queue"));
	}

}
