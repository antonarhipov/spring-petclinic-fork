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
import org.springframework.samples.petclinic.scheduling.appointment.OfferDecisionService;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityRepository;
import org.springframework.samples.petclinic.scheduling.request.OwnerRequestHistoryService;
import org.springframework.samples.petclinic.scheduling.request.OwnerResumeRouteResolver;
import org.springframework.samples.petclinic.scheduling.request.RequestRevisionRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestRevisionService;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.RequestWithdrawalService;
import org.springframework.samples.petclinic.scheduling.request.RequestWorkflowService;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest({ OwnerRequestRecoveryController.class, OwnerOfferController.class })
@Import({ SecurityConfiguration.class, CurrentOwnerAccount.class })
class OwnerRequestRecoveryControllerTests {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	AccountRepository accounts;

	@MockitoBean
	RequestWorkflowService workflow;

	@MockitoBean
	RequestRevisionService revisions;

	@MockitoBean
	RequestRevisionRepository revisionRepository;

	@MockitoBean
	RequestWithdrawalService withdrawals;

	@MockitoBean
	OwnerRequestHistoryService history;

	@MockitoBean
	OwnerResumeRouteResolver resumeRoutes;

	@MockitoBean
	org.springframework.samples.petclinic.scheduling.appointment.OfferService offers;

	@MockitoBean
	org.springframework.samples.petclinic.scheduling.appointment.OfferAcceptanceService acceptance;

	@MockitoBean
	OfferDecisionService decisions;

	@MockitoBean
	org.springframework.samples.petclinic.vet.VetRepository vets;

	@MockitoBean
	AvailabilityRepository policies;

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void withdrawDialogAndPostCloseRequest() throws Exception {
		given(this.accounts.findByUsername("george")).willReturn(Optional.of(ownerAccount()));
		SchedulingRequest request = request(RequestState.READY_FOR_SUGGESTION);
		given(this.workflow.requireOwned(5L, 1)).willReturn(request);
		this.mockMvc.perform(get("/owner/scheduling-requests/5/withdraw"))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/owner/withdraw-dialog"));
		this.mockMvc.perform(post("/owner/scheduling-requests/5/withdraw").with(csrf()).param("expectedVersion", "3"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/owner/dashboard"));
		verify(this.withdrawals).withdraw(5L, 1, 9L, 3);
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void resumeUsesServerDerivedRoute() throws Exception {
		given(this.accounts.findByUsername("george")).willReturn(Optional.of(ownerAccount()));
		SchedulingRequest request = request(RequestState.READY_FOR_SUGGESTION);
		given(this.workflow.requireOwned(5L, 1)).willReturn(request);
		given(this.resumeRoutes.resumeUrl(request)).willReturn("/owner/scheduling-requests/5/suggestion");
		this.mockMvc.perform(get("/owner/scheduling-requests/5/resume"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/owner/scheduling-requests/5/suggestion"));
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void historyPageIsOwnerSafe() throws Exception {
		given(this.accounts.findByUsername("george")).willReturn(Optional.of(ownerAccount()));
		SchedulingRequest request = request(RequestState.READY_FOR_SUGGESTION);
		given(this.history.load(5L, 1)).willReturn(new OwnerRequestHistoryService.HistoryView(request, List.of()));
		this.mockMvc.perform(get("/owner/scheduling-requests/5/history"))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/owner/request-history"));
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void rejectUsesInlineConfirmationPost() throws Exception {
		given(this.accounts.findByUsername("george")).willReturn(Optional.of(ownerAccount()));
		this.mockMvc
			.perform(post("/owner/scheduling-requests/5/offers/8/reject").with(csrf())
				.param("expectedVersion", "0")
				.param("reason", "too late"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/owner/scheduling-requests/5/suggestion"));
		verify(this.decisions).reject(5L, 1, 8L, 0, "too late");
	}

	private Account ownerAccount() {
		Account account = new Account();
		account.setUsername("george");
		account.setRole(AccountRole.OWNER);
		account.setOwnerId(1);
		ReflectionTestUtils.setField(account, "id", 9L);
		return account;
	}

	private SchedulingRequest request(RequestState state) {
		SchedulingRequest request = new SchedulingRequest();
		request.setOwnerId(1);
		request.setPetId(1);
		request.setState(state);
		ReflectionTestUtils.setField(request, "version", 3);
		return request;
	}

}
