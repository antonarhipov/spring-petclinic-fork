package org.springframework.samples.petclinic.scheduling.web.owner;

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
import org.springframework.samples.petclinic.scheduling.availability.ClinicSchedulingPolicy;
import org.springframework.samples.petclinic.scheduling.request.OwnerDashboardService;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.RequestWorkflowService;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(OwnerRequestController.class)
@Import({ SecurityConfiguration.class, CurrentOwnerAccount.class })
class UrgentRequestExperienceTests {

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
	org.springframework.samples.petclinic.scheduling.request.OwnerSchedulingQueryService schedulingQueries;

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void newRequestShowsUrgentCareGuidance() throws Exception {
		given(this.accounts.findByUsername("george")).willReturn(Optional.of(ownerAccount()));
		given(this.owners.findById(1)).willReturn(Optional.of(new Owner()));
		ClinicSchedulingPolicy policy = new ClinicSchedulingPolicy();
		policy.setUrgentCareGuidance("Call the emergency clinic now.");
		given(this.policies.currentPolicy()).willReturn(policy);
		this.mockMvc.perform(get("/owner/pets/1/scheduling-requests/new"))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/owner/request-form"))
			.andExpect(model().attribute("urgentCareGuidance", "Call the emergency clinic now."))
			.andExpect(model().attributeDoesNotExist("reassurance"));
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void staffHandlingShowsGuidanceWithoutInternals() throws Exception {
		given(this.accounts.findByUsername("george")).willReturn(Optional.of(ownerAccount()));
		SchedulingRequest request = new SchedulingRequest();
		request.setOwnerId(1);
		request.setState(RequestState.STAFF_HANDLING);
		request.setSuspectedEmergency(true);
		given(this.workflow.requireOwned(5L, 1)).willReturn(request);
		ClinicSchedulingPolicy policy = new ClinicSchedulingPolicy();
		policy.setUrgentCareGuidance("Call the emergency clinic now.");
		given(this.policies.currentPolicy()).willReturn(policy);
		this.mockMvc.perform(get("/owner/scheduling-requests/5/status"))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/owner/staff-status"))
			.andExpect(model().attributeDoesNotExist("errorClassification"))
			.andExpect(model().attributeDoesNotExist("score"));
	}

	private Account ownerAccount() {
		Account account = new Account();
		account.setUsername("george");
		account.setRole(AccountRole.OWNER);
		account.setOwnerId(1);
		return account;
	}

}
