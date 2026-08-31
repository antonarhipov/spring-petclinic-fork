package org.springframework.samples.petclinic.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.account.AccountRepository;
import org.springframework.samples.petclinic.account.Role;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerAccessService;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityRouteMatrixTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private OwnerAccessService ownerAccessService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private PetClinicPrincipal george;

	private PetClinicPrincipal betty;

	private PetClinicPrincipal admin;

	@BeforeEach
	void setUpPrincipals() {
		Account georgeAccount = ensureOwnerAccount("george", 1);
		Account bettyAccount = ensureOwnerAccount("betty", 2);
		Account adminAccount = ensureStaffAccount("admin");

		this.george = SecurityTestPrincipals.owner(georgeAccount.getId(), georgeAccount.getUsername(),
				georgeAccount.getOwnerId(), georgeAccount.getSessionVersion(), false);
		this.betty = SecurityTestPrincipals.owner(bettyAccount.getId(), bettyAccount.getUsername(),
				bettyAccount.getOwnerId(), bettyAccount.getSessionVersion(), false);
		this.admin = SecurityTestPrincipals.staff(adminAccount.getId(), adminAccount.getUsername(),
				adminAccount.getSessionVersion(), false);
	}

	@Test
	void publicRoutesAreReachableAnonymously() throws Exception {
		this.mockMvc.perform(get("/")).andExpect(status().isOk());
		this.mockMvc.perform(get("/auth/login")).andExpect(status().isOk()).andExpect(view().name("auth/login"));
		this.mockMvc.perform(get("/login")).andExpect(status().isOk()).andExpect(view().name("auth/login"));
	}

	@Test
	void anonymousUsersAreRedirectedFromOwnerAndStaffRoutes() throws Exception {
		this.mockMvc.perform(get("/owner/dashboard"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/auth/login"));
		this.mockMvc.perform(get("/staff/calendar/week"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/auth/login"));
	}

	@Test
	void ownerCanAccessOwnerRoutesButNotStaffRoutes() throws Exception {
		this.mockMvc.perform(get("/owner/dashboard").with(user(this.george)))
			.andExpect(status().isOk())
			.andExpect(view().name("owner/dashboard"));
		this.mockMvc.perform(get("/owner/profile").with(user(this.george)))
			.andExpect(status().isOk())
			.andExpect(view().name("owner/profile"));
		this.mockMvc.perform(get("/staff/calendar/week").with(user(this.george))).andExpect(status().isForbidden());
	}

	@Test
	void staffCanAccessStaffRoutesButNotOwnerRoutes() throws Exception {
		this.mockMvc.perform(get("/staff/calendar/week").with(user(this.admin)))
			.andExpect(status().isOk())
			.andExpect(view().name("staff/calendar/week"));
		this.mockMvc.perform(get("/owner/dashboard").with(user(this.admin))).andExpect(status().isForbidden());
	}

	@Test
	void passwordChangePageIsAvailableToAuthenticatedUsers() throws Exception {
		this.mockMvc.perform(get("/auth/password-change").with(user(this.george)))
			.andExpect(status().isOk())
			.andExpect(view().name("auth/password-change"));
		this.mockMvc.perform(get("/account/password-change").with(user(this.admin)))
			.andExpect(status().isOk())
			.andExpect(view().name("auth/password-change"));
	}

	@Test
	void mutatingOwnerProfileRequiresCsrf() throws Exception {
		this.mockMvc
			.perform(post("/owner/profile").with(user(this.george))
				.with(csrf().useInvalidToken())
				.param("address", "1 Main St")
				.param("city", "Madison")
				.param("telephone", "6085550000"))
			.andExpect(status().isForbidden());

		this.mockMvc
			.perform(post("/owner/profile").with(user(this.george))
				.with(csrf())
				.param("address", "1 Main St")
				.param("city", "Madison")
				.param("telephone", "6085550000"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/owner/profile"));
	}

	@Test
	void ownerAccessServiceDeniesCrossOwnerAccess() {
		assertThatThrownBy(() -> this.ownerAccessService.getOwnerProfile(2, this.george))
			.isInstanceOf(AccessDeniedException.class);

		Owner ownProfile = this.ownerAccessService.getOwnerProfile(1, this.george);
		assertThat(ownProfile.getId()).isEqualTo(1);
	}

	@Test
	void ownerProfileUpdateAllowsContactFieldsOnly() {
		Owner before = this.ownerRepository.findById(1).orElseThrow();
		String originalFirstName = before.getFirstName();
		String originalLastName = before.getLastName();

		Owner updated = this.ownerAccessService.updateOwnerContact(1, "999 Updated Ave", "Sun Prairie", "6085559999",
				this.george);

		assertThat(updated.getAddress()).isEqualTo("999 Updated Ave");
		assertThat(updated.getCity()).isEqualTo("Sun Prairie");
		assertThat(updated.getTelephone()).isEqualTo("6085559999");
		assertThat(updated.getFirstName()).isEqualTo(originalFirstName);
		assertThat(updated.getLastName()).isEqualTo(originalLastName);

		assertThatThrownBy(() -> this.ownerAccessService.updateOwnerContact(1, "x", "y", "z", this.betty))
			.isInstanceOf(AccessDeniedException.class);
	}

	private Account ensureOwnerAccount(String username, int ownerId) {
		return this.accountRepository.findByUsername(username).orElseGet(() -> {
			Account account = new Account();
			account.setUsername(username);
			account.setPasswordHash(this.passwordEncoder.encode(username + "123"));
			account.setRole(Role.OWNER);
			account.setOwnerId(ownerId);
			account.setEnabled(true);
			account.setPasswordChangeRequired(false);
			account.setSessionVersion(0L);
			return this.accountRepository.save(account);
		});
	}

	private Account ensureStaffAccount(String username) {
		return this.accountRepository.findByUsername(username).orElseGet(() -> {
			Account account = new Account();
			account.setUsername(username);
			account.setPasswordHash(this.passwordEncoder.encode(username + "123"));
			account.setRole(Role.STAFF);
			account.setOwnerId(null);
			account.setEnabled(true);
			account.setPasswordChangeRequired(false);
			account.setSessionVersion(0L);
			return this.accountRepository.save(account);
		});
	}

}
