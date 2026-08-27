/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SecurityAccessControlTests {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc;

	@Autowired
	private AppUserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
	}

	@Test
	void anonymousRedirectsToLoginOnSecuredEndpoints() throws Exception {
		this.mockMvc.perform(get("/owners/find"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));

		this.mockMvc.perform(get("/owners/1"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));

		this.mockMvc.perform(get("/owners/new"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));

		this.mockMvc.perform(get("/owners/1/pets/new"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));

		this.mockMvc.perform(get("/owners/1/pets/1/visits/new"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));

		this.mockMvc.perform(get("/owners/1/pets/1/appointments/new"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));

		this.mockMvc.perform(get("/staff/appointments/new"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));

		this.mockMvc.perform(get("/staff/fallback"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));

		this.mockMvc.perform(get("/staff/appointments"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));

		this.mockMvc.perform(get("/owners/1/appointments"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));

		this.mockMvc.perform(get("/vets.html"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));

		this.mockMvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
	}

	@Test
	void anonymousCanAccessPublicResources() throws Exception {
		this.mockMvc.perform(get("/login")).andExpect(status().isOk()).andExpect(view().name("login"));

		this.mockMvc.perform(get("/urgent-care")).andExpect(status().isOk()).andExpect(view().name("urgentCare"));
	}

	@Test
	@WithUserDetails("george")
	void ownerCanOnlyAccessOwnRecordsAndVetList() throws Exception {
		// George is owner ID 1
		this.mockMvc.perform(get("/owners/1")).andExpect(status().isOk()).andExpect(view().name("owners/ownerDetails"));

		this.mockMvc.perform(get("/owners/1/edit"))
			.andExpect(status().isOk())
			.andExpect(view().name("owners/createOrUpdateOwnerForm"));

		this.mockMvc.perform(get("/owners/1/pets/new"))
			.andExpect(status().isOk())
			.andExpect(view().name("pets/createOrUpdatePetForm"));

		this.mockMvc.perform(get("/owners/1/pets/1/visits/new"))
			.andExpect(status().isOk())
			.andExpect(view().name("pets/createOrUpdateVisitForm"));

		this.mockMvc.perform(get("/owners/1/appointments"))
			.andExpect(status().isOk())
			.andExpect(view().name("owners/appointments"));

		// Vets viewable by owner
		this.mockMvc.perform(get("/vets.html")).andExpect(status().isOk()).andExpect(view().name("vets/vetList"));

		// Accessing other owner (ID 2) is forbidden
		this.mockMvc.perform(get("/owners/2")).andExpect(status().isForbidden());

		this.mockMvc.perform(get("/owners/2/edit")).andExpect(status().isForbidden());

		this.mockMvc.perform(get("/owners/2/pets/new")).andExpect(status().isForbidden());

		this.mockMvc.perform(get("/owners/2/pets/2/visits/new")).andExpect(status().isForbidden());

		this.mockMvc.perform(get("/owners/2/appointments")).andExpect(status().isForbidden());

		// Finding or listing all owners or creating owners is forbidden for OWNER role
		this.mockMvc.perform(get("/owners/find")).andExpect(status().isForbidden());

		this.mockMvc.perform(get("/owners")).andExpect(status().isForbidden());

		this.mockMvc.perform(get("/owners/new")).andExpect(status().isForbidden());

		// Staff direct booking is forbidden for OWNER role
		this.mockMvc.perform(get("/owners/1/pets/1/appointments/new")).andExpect(status().isForbidden());

		this.mockMvc.perform(get("/staff/appointments/new")).andExpect(status().isForbidden());

		this.mockMvc.perform(get("/staff/fallback")).andExpect(status().isForbidden());

		this.mockMvc.perform(get("/staff/appointments")).andExpect(status().isForbidden());
	}

	@Test
	@WithUserDetails("staff")
	void staffCanActOnBehalfOfAnyOwner() throws Exception {
		// Staff can view owner 1 and owner 2
		this.mockMvc.perform(get("/owners/1")).andExpect(status().isOk());

		this.mockMvc.perform(get("/owners/2")).andExpect(status().isOk());

		// Staff can find/list/create owners
		this.mockMvc.perform(get("/owners/find")).andExpect(status().isOk());

		this.mockMvc.perform(get("/owners/new")).andExpect(status().isOk());

		// Staff can add pet / visit to any owner
		this.mockMvc.perform(get("/owners/1/pets/new")).andExpect(status().isOk());

		this.mockMvc.perform(get("/owners/2/pets/new")).andExpect(status().isOk());

		// Staff can access direct appointment booking for any owner
		this.mockMvc.perform(get("/owners/1/pets/1/appointments/new")).andExpect(status().isOk());

		this.mockMvc.perform(get("/owners/2/pets/2/appointments/new")).andExpect(status().isOk());

		this.mockMvc.perform(get("/owners/1/appointments")).andExpect(status().isOk());

		this.mockMvc.perform(get("/staff/fallback")).andExpect(status().isOk());

		this.mockMvc.perform(get("/staff/appointments")).andExpect(status().isOk());

		// Staff can reset owner password
		this.mockMvc.perform(post("/staff/owners/4/reset-password").with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/owners/4"));
	}

	@Test
	void ownerDetailsBookingLinkIsRenderedOnlyForStaff() throws Exception {
		String staffPage = this.mockMvc.perform(get("/owners/1").with(user("staff").roles("STAFF")))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String ownerPage = this.mockMvc.perform(get("/owners/1").with(user("george").roles("OWNER")))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(staffPage).contains("href=\"/owners/1/pets/1/appointments/new\"").contains("Book Appointment");
		assertThat(ownerPage).doesNotContain("/owners/1/pets/1/appointments/new").doesNotContain("Book Appointment");
	}

	@Test
	void authenticationFormLogin() throws Exception {
		// Valid credentials
		this.mockMvc.perform(formLogin("/login").user("george").password("george123"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/"));

		this.mockMvc.perform(formLogin("/login").user("staff").password("staff123"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/"));

		// Invalid password
		this.mockMvc.perform(formLogin("/login").user("george").password("wrongpassword"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login?error"));
	}

	@Test
	@WithUserDetails("george")
	void demoOwnerAccountSkipsForcedPasswordChange() throws Exception {
		AppUser george = this.userRepository.findByUsername("george").orElseThrow();
		assertThat(george.isMustChangePassword()).isFalse();
		assertThat(george.isEnabled()).isTrue();

		// George can access own owner details directly without redirect to
		// change-password
		this.mockMvc.perform(get("/owners/1")).andExpect(status().isOk()).andExpect(view().name("owners/ownerDetails"));
	}

	@Test
	@WithUserDetails("staff")
	void demoStaffAccountSkipsForcedPasswordChange() throws Exception {
		AppUser staff = this.userRepository.findByUsername("staff").orElseThrow();
		assertThat(staff.isMustChangePassword()).isFalse();
		assertThat(staff.isEnabled()).isTrue();

		// Staff can access owner search directly without redirect to change-password
		this.mockMvc.perform(get("/owners/find")).andExpect(status().isOk());
	}

	@Test
	void forcedPasswordChangeRedirectionAndBlocking() throws Exception {
		AppUser mustChangeAppUser = this.userRepository.findByUsername("mustchangeuser").orElseGet(() -> {
			AppUser u = new AppUser();
			u.setUsername("mustchangeuser");
			u.setPasswordHash(this.passwordEncoder.encode("oldpass123"));
			u.setRole(UserRole.OWNER);
			u.setEnabled(true);
			u.setMustChangePassword(true);
			return this.userRepository.save(u);
		});
		mustChangeAppUser.setMustChangePassword(true);
		this.userRepository.save(mustChangeAppUser);

		AppUserDetails userWithMustChange = new AppUserDetails(mustChangeAppUser.getId(), "mustchangeuser",
				mustChangeAppUser.getPasswordHash(), UserRole.OWNER, true, true, null);

		// Secured pages are blocked and redirected to /change-password
		this.mockMvc.perform(get("/owners/1").with(user(userWithMustChange)))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/change-password"));

		this.mockMvc.perform(get("/vets.html").with(user(userWithMustChange)))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/change-password"));

		// Change password page itself is accessible
		this.mockMvc.perform(get("/change-password").with(user(userWithMustChange)))
			.andExpect(status().isOk())
			.andExpect(view().name("security/changePassword"));

		// Form validation: mismatched passwords
		this.mockMvc
			.perform(post("/change-password").with(csrf())
				.with(user(userWithMustChange))
				.param("newPassword", "newpass123")
				.param("confirmPassword", "different123"))
			.andExpect(status().isOk())
			.andExpect(view().name("security/changePassword"))
			.andExpect(model().attributeExists("error"));

		// Submitting valid password change clears the flag and redirects to /
		this.mockMvc
			.perform(post("/change-password").with(csrf())
				.with(user(userWithMustChange))
				.param("newPassword", "newpass123")
				.param("confirmPassword", "newpass123"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/"));

		// Verify database user now has mustChangePassword = false
		AppUser updatedUser = this.userRepository.findByUsername("mustchangeuser").orElseThrow();
		assertThat(updatedUser.isMustChangePassword()).isFalse();
		assertThat(this.passwordEncoder.matches("newpass123", updatedUser.getPasswordHash())).isTrue();
	}

	@Test
	@WithUserDetails("staff")
	void staffPasswordResetAndProvisioningArmsMustChangePassword() throws Exception {
		// Reset password for owner 2 (Betty Davis)
		this.mockMvc
			.perform(post("/staff/owners/2/reset-password").with(csrf()).param("temporaryPassword", "tempPass123"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/owners/2"));

		AppUser bettyUser = this.userRepository.findByOwnerId(2).orElseThrow();
		assertThat(bettyUser.isMustChangePassword()).isTrue();
		assertThat(this.passwordEncoder.matches("tempPass123", bettyUser.getPasswordHash())).isTrue();
	}

}
