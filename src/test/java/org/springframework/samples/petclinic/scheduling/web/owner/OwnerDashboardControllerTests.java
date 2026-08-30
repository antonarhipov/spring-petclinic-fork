package org.springframework.samples.petclinic.scheduling.web.owner;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.account.AccountRepository;
import org.springframework.samples.petclinic.account.AccountRole;
import org.springframework.samples.petclinic.account.SecurityConfiguration;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityRepository;
import org.springframework.samples.petclinic.scheduling.request.OwnerDashboardService;
import org.springframework.samples.petclinic.scheduling.request.OwnerSchedulingQueryService;
import org.springframework.samples.petclinic.scheduling.request.RequestWorkflowService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(OwnerRequestController.class)
@Import({ SecurityConfiguration.class, CurrentOwnerAccount.class })
class OwnerDashboardControllerTests {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	AccountRepository accounts;

	@MockitoBean
	OwnerRepository owners;

	@MockitoBean
	RequestWorkflowService workflow;

	@MockitoBean
	OwnerDashboardService dashboard;

	@MockitoBean
	AvailabilityRepository policies;

	@MockitoBean
	OwnerSchedulingQueryService schedulingQueries;

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void dashboardUsesServerResumeUrl() throws Exception {
		Account account = new Account();
		account.setUsername("george");
		account.setRole(AccountRole.OWNER);
		account.setOwnerId(1);
		given(this.accounts.findByUsername("george")).willReturn(Optional.of(account));
		Owner owner = new Owner();
		owner.setId(1);
		given(this.dashboard.load(1)).willReturn(
				new OwnerDashboardService.OwnerDashboardView(owner, null, "/owner/scheduling-requests/5/consent"));
		given(this.schedulingQueries.appointments(1)).willReturn(List.of());
		this.mockMvc.perform(get("/owner/dashboard"))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/owner/dashboard"));
	}

}
