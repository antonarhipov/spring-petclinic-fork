package org.springframework.samples.petclinic.scheduling.web.owner;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityRepository;
import org.springframework.samples.petclinic.scheduling.availability.ClinicSchedulingPolicy;
import org.springframework.samples.petclinic.scheduling.request.OwnerDashboardService;
import org.springframework.samples.petclinic.scheduling.request.RequestRevision;
import org.springframework.samples.petclinic.scheduling.request.RequestRevisionRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.RequestWindowRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestWorkflowService;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest({ OwnerRequestController.class, OwnerInterpretationController.class })
@Import({ SecurityConfiguration.class, CurrentOwnerAccount.class })
class OwnerInterpretationControllerTests {

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
	RequestRevisionRepository revisions;

	@MockitoBean
	RequestWindowRepository windows;

	@MockitoBean
	org.springframework.samples.petclinic.scheduling.request.OwnerSchedulingQueryService schedulingQueries;

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void consentPageLeavesAgreementUnchecked() throws Exception {
		Account account = ownerAccount();
		given(this.accounts.findByUsername("george")).willReturn(Optional.of(account));
		SchedulingRequest request = new SchedulingRequest();
		request.setOwnerId(1);
		request.setState(RequestState.AWAITING_CONSENT);
		given(this.workflow.requireOwned(5L, 1)).willReturn(request);
		given(this.policies.currentPolicy()).willReturn(new ClinicSchedulingPolicy());
		this.mockMvc.perform(get("/owner/scheduling-requests/5/consent"))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/owner/consent"));
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void declineIsSeparateAction() throws Exception {
		Account account = ownerAccount();
		given(this.accounts.findByUsername("george")).willReturn(Optional.of(account));
		this.mockMvc
			.perform(post("/owner/scheduling-requests/5/consent/manual").with(csrf()).param("expectedVersion", "0"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/owner/scheduling-requests/5/status"));
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void dashboardExposesResume() throws Exception {
		Account account = ownerAccount();
		given(this.accounts.findByUsername("george")).willReturn(Optional.of(account));
		Owner owner = new Owner();
		owner.setId(1);
		given(this.dashboard.load(1)).willReturn(
				new OwnerDashboardService.OwnerDashboardView(owner, null, "/owner/scheduling-requests/5/consent"));
		this.mockMvc.perform(get("/owner/dashboard"))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/owner/dashboard"));
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void reviewAllowsOwnerFields() throws Exception {
		Account account = ownerAccount();
		given(this.accounts.findByUsername("george")).willReturn(Optional.of(account));
		SchedulingRequest request = new SchedulingRequest();
		request.setOwnerId(1);
		request.setState(RequestState.INTERPRETATION_REVIEW);
		request.setActiveRequestRevisionId(9L);
		given(this.workflow.requireOwned(5L, 1)).willReturn(request);
		RequestRevision revision = new RequestRevision();
		revision.setVisitReason("Checkup");
		revision.setDurationMinutes(30);
		given(this.revisions.findById(9L)).willReturn(Optional.of(revision));
		given(this.windows.findByRequestRevisionId(9L)).willReturn(List.of());
		this.mockMvc.perform(get("/owner/scheduling-requests/5/interpretation"))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/owner/interpretation-review"))
			.andExpect(model().attribute("confirmEnabled", true));
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void createRequestRedirectsToConsent() throws Exception {
		Account account = ownerAccount();
		given(this.accounts.findByUsername("george")).willReturn(Optional.of(account));
		Owner owner = new Owner();
		owner.setId(1);
		Pet pet = new Pet();
		owner.addPet(pet);
		pet.setId(1);
		given(this.owners.findById(1)).willReturn(Optional.of(owner));
		SchedulingRequest created = new SchedulingRequest();
		given(this.workflow.createRequest(eq(1), eq(1), any())).willReturn(created);
		this.mockMvc
			.perform(post("/owner/scheduling-requests").with(csrf())
				.param("petId", "1")
				.param("sourceText", "Need a wellness exam next week please"))
			.andExpect(status().is3xxRedirection());
	}

	private Account ownerAccount() {
		Account account = new Account();
		account.setUsername("george");
		account.setRole(AccountRole.OWNER);
		account.setOwnerId(1);
		return account;
	}

}
