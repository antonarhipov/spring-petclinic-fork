package org.springframework.samples.petclinic.account;

import java.time.Instant;
import java.util.Optional;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest({ AccountWebController.class, LoginController.class })
@Import(SecurityConfiguration.class)
class AccountControllerTests {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	AccountRepository accounts;

	@MockitoBean
	OwnerRepository owners;

	@MockitoBean
	AccountProvisioningService provisioning;

	@MockitoBean
	PasswordService passwords;

	@MockitoBean
	UsernameSuggester suggester;

	@Test
	void loginPageIsPublic() throws Exception {
		this.mockMvc.perform(get("/login")).andExpect(status().isOk()).andExpect(view().name("account/login"));
	}

	@Test
	void repeatedFailedLoginsAreNotThrottled() throws Exception {
		for (int i = 0; i < 8; i++) {
			this.mockMvc.perform(post("/login").with(csrf()).param("username", "unknown").param("password", "wrong"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login?error"));
		}
	}

	@Test
	@WithMockUser(username = "admin", roles = "STAFF")
	void provisionRequiresCsrf() throws Exception {
		this.mockMvc.perform(post("/staff/owners/1/account").param("username", "betty").param("expectedVersion", "0"))
			.andExpect(status().isForbidden());
		verify(this.provisioning, never()).provision(any(), any(), any(), any());
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void ownerCannotProvision() throws Exception {
		this.mockMvc.perform(
				post("/staff/owners/1/account").with(csrf()).param("username", "betty").param("expectedVersion", "0"))
			.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(username = "admin", roles = "STAFF")
	void provisionDisplaysOneTimePasswordOnceViaFlash() throws Exception {
		Account staff = staffAccount();
		given(this.accounts.findByUsername("admin")).willReturn(Optional.of(staff));
		given(this.provisioning.provision(eq(1), eq("betty"), eq(0), eq(2L)))
			.willReturn(new OneTimeCredentialResult("betty", "once-only", Instant.parse("2026-03-23T12:00:00Z")));
		this.mockMvc.perform(
				post("/staff/owners/1/account").with(csrf()).param("username", "betty").param("expectedVersion", "0"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/owners/1/account/one-time"))
			.andExpect(flash().attribute("oneTimePassword", "once-only"))
			.andExpect(flash().attribute("username", "betty"));
	}

	@Test
	@WithMockUser(username = "admin", roles = "STAFF")
	void oneTimeCredentialPageIsNotStoreCached() throws Exception {
		this.mockMvc
			.perform(get("/staff/owners/1/account/one-time").flashAttr("oneTimePassword", "once-only")
				.flashAttr("username", "betty")
				.flashAttr("expiresAt", Instant.parse("2026-03-23T12:00:00Z")))
			.andExpect(status().isOk())
			.andExpect(view().name("account/one-time-credential"))
			.andExpect(header().string("Cache-Control", Matchers.containsString("no-store")));
	}

	@Test
	@WithMockUser(username = "admin", roles = "STAFF")
	void resetRequiresCsrfAndConfirmation() throws Exception {
		this.mockMvc
			.perform(post("/staff/owners/1/account/reset").param("expectedVersion", "0").param("confirm", "true"))
			.andExpect(status().isForbidden());
		Account staff = staffAccount();
		given(this.accounts.findByUsername("admin")).willReturn(Optional.of(staff));
		given(this.provisioning.reset(eq(1), eq(0), eq(true), eq(2L)))
			.willReturn(new OneTimeCredentialResult("betty", "reset-once", Instant.parse("2026-03-23T12:00:00Z")));
		this.mockMvc
			.perform(post("/staff/owners/1/account/reset").with(csrf())
				.param("expectedVersion", "0")
				.param("confirm", "true"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/owners/1/account/one-time"));
	}

	@Test
	@WithMockUser(username = "admin", roles = "STAFF")
	void provisionFormRendersSuggestion() throws Exception {
		Owner owner = new Owner();
		owner.setId(1);
		owner.setFirstName("Betty");
		given(this.owners.findById(1)).willReturn(Optional.of(owner));
		given(this.accounts.findByOwnerId(1)).willReturn(Optional.empty());
		given(this.suggester.suggest(eq("Betty"), any())).willReturn("betty");
		this.mockMvc.perform(get("/staff/owners/1/account"))
			.andExpect(status().isOk())
			.andExpect(view().name("account/provision-owner"));
	}

	@Test
	@WithMockUser(username = "betty", roles = "OWNER")
	void temporaryUserCannotReachOwnerPagesUntilPasswordChange() throws Exception {
		Account account = new Account();
		account.setUsername("betty");
		account.setRole(AccountRole.OWNER);
		account.setOwnerId(1);
		account.setMustChangePassword(true);
		given(this.accounts.findByUsername("betty")).willReturn(Optional.of(account));
		this.mockMvc.perform(get("/owner/dashboard"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/account/password/change"));
		this.mockMvc.perform(get("/account/password/change"))
			.andExpect(status().isOk())
			.andExpect(view().name("account/change-password"));
	}

	@Test
	@WithMockUser(username = "betty", roles = "OWNER")
	void passwordChangeDoesNotEchoSecretsOnValidationFailure() throws Exception {
		doThrow(new PasswordChangeException("password.mismatch")).when(this.passwords)
			.changePassword(eq("betty"), eq("temp"), eq("new-secret"), eq("mismatch"), any());
		this.mockMvc
			.perform(post("/account/password/change").with(csrf())
				.param("currentPassword", "temp")
				.param("newPassword", "new-secret")
				.param("confirmPassword", "mismatch"))
			.andExpect(status().isOk())
			.andExpect(view().name("account/change-password"))
			.andExpect(content().string(Matchers.not(Matchers.containsString("new-secret"))));
	}

	@Test
	@WithMockUser(username = "betty", roles = "OWNER")
	void logoutInvalidatesSession() throws Exception {
		this.mockMvc.perform(post("/logout").with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login?logout"));
	}

	private static Account staffAccount() {
		Account staff = new Account();
		staff.setUsername("admin");
		staff.setRole(AccountRole.STAFF);
		ReflectionTestUtils.setField(staff, "id", 2L);
		return staff;
	}

}
