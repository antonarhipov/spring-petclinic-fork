package org.springframework.samples.petclinic.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.scheduling.controller.UrgentCareController;
import org.springframework.samples.petclinic.scheduling.model.ClinicSettings;
import org.springframework.samples.petclinic.scheduling.service.ClinicSettingsService;
import org.springframework.samples.petclinic.system.WelcomeController;
import org.springframework.samples.petclinic.scheduling.service.AuditService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;

@WebMvcTest(controllers = { WelcomeController.class, LoginController.class, UrgentCareController.class,
		PasswordChangeController.class, AccountManagementController.class })
@Import(WebMvcSecurityTestConfig.class)
class SecurityRouteMatrixTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AccountService accountService;

	@MockitoBean
	private AuditService auditService;

	@MockitoBean
	private ClinicSettingsService clinicSettingsService;

	@MockitoBean
	private CustomAuthenticationSuccessHandler successHandler;

	@MockitoBean
	private CustomAuthenticationFailureHandler failureHandler;

	@Test
	void anonymousCanAccessPublicRoutes() throws Exception {
		given(clinicSettingsService.getSettings()).willReturn(new ClinicSettings());
		mockMvc.perform(get("/")).andExpect(status().isOk());
		mockMvc.perform(get("/login")).andExpect(status().isOk());
		mockMvc.perform(get("/guidance/urgent-care")).andExpect(status().isOk());
	}

	@Test
	void anonymousIsRedirectedFromStaffRoutes() throws Exception {
		mockMvc.perform(get("/owners/find")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
		mockMvc.perform(get("/admin/accounts"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
	}

	@Test
	void anonymousIsRedirectedFromOwnerSelfService() throws Exception {
		mockMvc.perform(get("/my-profile")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
	}

	@Test
	void ownerCannotAccessStaffRoutes() throws Exception {
		mockMvc.perform(get("/owners/find").with(user("owner1").roles("OWNER"))).andExpect(status().isForbidden());
		mockMvc.perform(get("/admin/accounts").with(user("owner1").roles("OWNER"))).andExpect(status().isForbidden());
	}

	@Test
	void staffCanAccessStaffRoutes() throws Exception {
		mockMvc.perform(get("/admin/accounts").with(user("staff1").roles("STAFF"))).andExpect(status().isOk());
	}

	@Test
	void ownerCanAccessOwnerSelfServicePaths() throws Exception {
		// Password change is authenticated for either role
		mockMvc.perform(get("/change-password").with(user("owner1").roles("OWNER"))).andExpect(status().isOk());
	}

	@Test
	void postWithoutCsrfIsRejected() throws Exception {
		mockMvc
			.perform(post("/change-password").with(user("staff1").roles("STAFF"))
				.param("oldPassword", "password1")
				.param("newPassword", "password2")
				.param("confirmPassword", "password2"))
			.andExpect(status().isForbidden());
	}

	@Test
	void postWithCsrfIsAcceptedByFilterChain() throws Exception {
		// Without a UserPrincipal principal the controller redirects to login; CSRF must
		// still pass.
		mockMvc
			.perform(post("/change-password").with(user("staff1").roles("STAFF"))
				.with(csrf())
				.param("oldPassword", "password1")
				.param("newPassword", "password2")
				.param("confirmPassword", "password2"))
			.andExpect(status().is3xxRedirection());
	}

}
