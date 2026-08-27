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

package org.springframework.samples.petclinic.scheduling;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.scheduling.controller.StaffDiagnosticsController;
import org.springframework.samples.petclinic.scheduling.dto.DiagnosticsDto;
import org.springframework.samples.petclinic.scheduling.service.DiagnosticsService;
import org.springframework.samples.petclinic.security.Account;
import org.springframework.samples.petclinic.security.AccountRepository;
import org.springframework.samples.petclinic.security.AccountRole;
import org.springframework.samples.petclinic.security.CustomAuthenticationFailureHandler;
import org.springframework.samples.petclinic.security.CustomAuthenticationSuccessHandler;
import org.springframework.samples.petclinic.security.UserPrincipal;
import org.springframework.samples.petclinic.security.WebMvcSecurityTestConfig;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StaffDiagnosticsController.class)
@Import(WebMvcSecurityTestConfig.class)
class StaffDiagnosticsControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DiagnosticsService diagnosticsService;

	@MockitoBean
	private org.springframework.samples.petclinic.scheduling.service.NotificationService notificationService;

	@MockitoBean
	private AccountRepository accountRepository;

	@MockitoBean
	private CustomAuthenticationSuccessHandler successHandler;

	@MockitoBean
	private CustomAuthenticationFailureHandler failureHandler;

	private UserPrincipal ownerPrincipal;

	private UserPrincipal staffPrincipal;

	@BeforeEach
	void setUp() {
		Owner owner = new Owner();
		owner.setId(1);
		owner.setFirstName("George");
		owner.setLastName("Franklin");

		Account ownerAccount = new Account();
		ownerAccount.setId(10);
		ownerAccount.setUsername("george");
		ownerAccount.setRole(AccountRole.OWNER);
		ownerAccount.setOwner(owner);
		ownerPrincipal = UserPrincipal.fromAccount(ownerAccount);

		Account staffAccount = new Account();
		staffAccount.setId(20);
		staffAccount.setUsername("vetstaff");
		staffAccount.setRole(AccountRole.STAFF);
		staffPrincipal = UserPrincipal.fromAccount(staffAccount);
	}

	@Test
	void testDiagnosticsUnauthenticatedRedirectsToLogin() throws Exception {
		mockMvc.perform(get("/scheduling/staff/diagnostics")).andExpect(status().is3xxRedirection());
	}

	@Test
	void testDiagnosticsAsOwnerForbidden() throws Exception {
		mockMvc.perform(get("/scheduling/staff/diagnostics").with(user(ownerPrincipal)))
			.andExpect(status().isForbidden());
	}

	@Test
	void testDiagnosticsAsStaffSuccess() throws Exception {
		DiagnosticsDto dto = new DiagnosticsDto();
		dto.setOllamaStatus("UP");
		dto.setOllamaBaseUrl("http://localhost:11434");
		dto.setOllamaModel("llama3.2");
		dto.setSolverStatus("READY");
		dto.setSolverEngine("Timefold Solver 2.5.0");
		dto.setLifecycleWorkerStatus("HEALTHY");

		given(diagnosticsService.getDiagnostics()).willReturn(dto);

		mockMvc.perform(get("/scheduling/staff/diagnostics").with(user(staffPrincipal)))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/staffDiagnostics"))
			.andExpect(model().attributeExists("diagnostics"));
	}

}
