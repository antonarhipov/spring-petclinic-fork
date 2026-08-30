package org.springframework.samples.petclinic.scheduling.web.owner;

import java.time.Instant;
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
import org.springframework.samples.petclinic.scheduling.appointment.Hold;
import org.springframework.samples.petclinic.scheduling.appointment.Offer;
import org.springframework.samples.petclinic.scheduling.appointment.OfferAcceptanceService;
import org.springframework.samples.petclinic.scheduling.appointment.OfferService;
import org.springframework.samples.petclinic.scheduling.appointment.OfferStatus;
import org.springframework.samples.petclinic.scheduling.appointment.OfferUnavailableException;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityRepository;
import org.springframework.samples.petclinic.scheduling.availability.ClinicSchedulingPolicy;
import org.springframework.samples.petclinic.scheduling.matching.MatchingCoordinator;
import org.springframework.samples.petclinic.scheduling.matching.MatchingMode;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.RequestWorkflowService;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest({ OwnerSuggestionController.class, OwnerOfferController.class })
@Import({ SecurityConfiguration.class, CurrentOwnerAccount.class })
class OwnerSuggestionAndOfferControllerTests {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	AccountRepository accounts;

	@MockitoBean
	RequestWorkflowService workflow;

	@MockitoBean
	MatchingCoordinator matching;

	@MockitoBean
	OfferService offers;

	@MockitoBean
	OfferAcceptanceService acceptance;

	@MockitoBean
	org.springframework.samples.petclinic.scheduling.appointment.OfferDecisionService decisions;

	@MockitoBean
	VetRepository vets;

	@MockitoBean
	AvailabilityRepository policies;

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void requestSuggestionRedirectsToProcessing() throws Exception {
		given(this.accounts.findByUsername("george")).willReturn(Optional.of(ownerAccount()));
		UUID operationId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
		given(this.matching.requestSuggestion(5L, 1, 0, MatchingMode.PREFERRED_ONLY)).willReturn(operationId);
		this.mockMvc
			.perform(post("/owner/scheduling-requests/5/suggestions").with(csrf()).param("expectedVersion", "0"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/owner/scheduling-requests/5/processing/" + operationId));
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void fallbackChoiceOmitsSolverInternals() throws Exception {
		given(this.accounts.findByUsername("george")).willReturn(Optional.of(ownerAccount()));
		SchedulingRequest request = new SchedulingRequest();
		request.setOwnerId(1);
		request.setState(RequestState.AWAITING_FALLBACK_CHOICE);
		given(this.workflow.requireOwned(5L, 1)).willReturn(request);
		this.mockMvc.perform(get("/owner/scheduling-requests/5/fallback-choice"))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/owner/fallback-choice"))
			.andExpect(model().attributeDoesNotExist("errorClassification"))
			.andExpect(model().attributeDoesNotExist("score"))
			.andExpect(model().attributeDoesNotExist("percentage"));
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void offerPageSeedsHeldOfferWithoutScores() throws Exception {
		given(this.accounts.findByUsername("george")).willReturn(Optional.of(ownerAccount()));
		given(this.vets.findAll()).willReturn(List.of());
		given(this.policies.currentPolicy()).willReturn(clinicPolicy());
		SchedulingRequest request = new SchedulingRequest();
		request.setOwnerId(1);
		request.setState(RequestState.OFFER_HELD);
		Offer offer = new Offer();
		offer.setStatus(OfferStatus.HELD);
		offer.setVeterinarianId(1);
		offer.setExpiresAt(Instant.parse("2099-01-01T00:00:00Z"));
		Hold hold = new Hold();
		hold.setExpiresAt(Instant.parse("2099-01-01T00:00:00Z"));
		given(this.offers.loadOwned(5L, 1, 8L)).willReturn(new OfferService.OfferView(request, offer, hold));
		this.mockMvc.perform(get("/owner/scheduling-requests/5/offers/8"))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/owner/offer"))
			.andExpect(model().attributeDoesNotExist("errorClassification"))
			.andExpect(model().attributeDoesNotExist("score"));
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void offerPageExposesClinicZoneAndHoldExpiryForCountdown() throws Exception {
		given(this.accounts.findByUsername("george")).willReturn(Optional.of(ownerAccount()));
		given(this.vets.findAll()).willReturn(List.of());
		given(this.policies.currentPolicy()).willReturn(clinicPolicy());
		SchedulingRequest request = new SchedulingRequest();
		request.setOwnerId(1);
		request.setState(RequestState.OFFER_HELD);
		Offer offer = new Offer();
		offer.setStatus(OfferStatus.HELD);
		offer.setVeterinarianId(1);
		offer.setExpiresAt(Instant.parse("2099-01-01T00:00:00Z"));
		Hold hold = new Hold();
		hold.setExpiresAt(Instant.parse("2099-01-01T00:00:00Z"));
		given(this.offers.loadOwned(5L, 1, 8L)).willReturn(new OfferService.OfferView(request, offer, hold));
		this.mockMvc.perform(get("/owner/scheduling-requests/5/offers/8"))
			.andExpect(status().isOk())
			.andExpect(model().attribute("clinicZone", "Europe/Amsterdam"))
			.andExpect(model().attribute("hold", hold));
	}

	private ClinicSchedulingPolicy clinicPolicy() {
		ClinicSchedulingPolicy policy = new ClinicSchedulingPolicy();
		policy.setZoneId("Europe/Amsterdam");
		return policy;
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void acceptRedirectsToDashboard() throws Exception {
		given(this.accounts.findByUsername("george")).willReturn(Optional.of(ownerAccount()));
		this.mockMvc
			.perform(post("/owner/scheduling-requests/5/offers/8/accept").with(csrf()).param("expectedVersion", "0"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/owner/dashboard"));
		verify(this.acceptance).accept(5L, 1, 8L, 0);
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void expiredAcceptShowsUnavailable() throws Exception {
		given(this.accounts.findByUsername("george")).willReturn(Optional.of(ownerAccount()));
		given(this.acceptance.accept(5L, 1, 8L, 0)).willThrow(new OfferUnavailableException());
		SchedulingRequest request = new SchedulingRequest();
		request.setOwnerId(1);
		given(this.offers.loadOwned(5L, 1, 8L)).willReturn(new OfferService.OfferView(request, new Offer(), null));
		this.mockMvc
			.perform(post("/owner/scheduling-requests/5/offers/8/accept").with(csrf()).param("expectedVersion", "0"))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/owner/offer-unavailable"));
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void rejectReturnsToSuggestion() throws Exception {
		given(this.accounts.findByUsername("george")).willReturn(Optional.of(ownerAccount()));
		this.mockMvc
			.perform(post("/owner/scheduling-requests/5/offers/8/reject").with(csrf()).param("expectedVersion", "0"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/owner/scheduling-requests/5/suggestion"));
		verify(this.decisions).reject(5L, 1, 8L, 0, null);
	}

	private Account ownerAccount() {
		Account account = new Account();
		account.setUsername("george");
		account.setRole(AccountRole.OWNER);
		account.setOwnerId(1);
		return account;
	}

}
