package org.springframework.samples.petclinic.scheduling.web.owner;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.account.AccountRepository;
import org.springframework.samples.petclinic.account.AccountRole;
import org.springframework.samples.petclinic.account.SecurityConfiguration;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.appointment.OfferAcceptanceService;
import org.springframework.samples.petclinic.scheduling.appointment.OfferDecisionService;
import org.springframework.samples.petclinic.scheduling.appointment.OfferService;
import org.springframework.samples.petclinic.scheduling.appointment.OwnerAppointmentService;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityRepository;
import org.springframework.samples.petclinic.scheduling.availability.ClinicSchedulingPolicy;
import org.springframework.samples.petclinic.scheduling.request.OwnerDashboardService;
import org.springframework.samples.petclinic.scheduling.request.OwnerResourceNotFoundException;
import org.springframework.samples.petclinic.scheduling.request.OwnerSchedulingQueryService;
import org.springframework.samples.petclinic.scheduling.interpretation.ManualInterpretationService;
import org.springframework.samples.petclinic.scheduling.queue.StaffAssistedSchedulingService;
import org.springframework.samples.petclinic.scheduling.queue.StaffQueueService;
import org.springframework.samples.petclinic.scheduling.request.RequestWorkflowService;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.web.staff.StaffQueueController;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ OwnerAppointmentController.class, OwnerRequestController.class, OwnerProfileController.class,
		OwnerOfferController.class, OwnerHistoryController.class, StaffQueueController.class })
@Import({ SecurityConfiguration.class, CurrentOwnerAccount.class })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OwnerSchedulingAuthorizationTests {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	AccountRepository accounts;

	@MockitoBean
	OwnerSchedulingQueryService queries;

	@MockitoBean
	OwnerAppointmentService cancellations;

	@MockitoBean
	OwnerRepository owners;

	@MockitoBean
	RequestWorkflowService workflow;

	@MockitoBean
	OwnerDashboardService dashboard;

	@MockitoBean
	AvailabilityRepository policies;

	@MockitoBean
	StaffQueueService queue;

	@MockitoBean
	StaffAssistedSchedulingService assisted;

	@MockitoBean
	ManualInterpretationService manualInterpretation;

	@MockitoBean
	SchedulingRequestRepository requestRepository;

	@MockitoBean
	OfferService offers;

	@MockitoBean
	OfferAcceptanceService acceptance;

	@MockitoBean
	OfferDecisionService decisions;

	@MockitoBean
	VetRepository vets;

	@Test
	void unauthenticatedOwnerPagesRedirectToLogin() throws Exception {
		this.mockMvc.perform(get("/owner/dashboard").accept(MediaType.TEXT_HTML))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
	}

	@Test
	@WithMockUser(roles = "OWNER")
	void ownerCannotAccessStaffQueue() throws Exception {
		this.mockMvc.perform(get("/staff/queue")).andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void crossOwnerAppointmentIsNotFound() throws Exception {
		given(this.accounts.findByUsername("george")).willReturn(Optional.of(ownerAccount()));
		given(this.queries.appointment(1, 99L)).willThrow(new OwnerResourceNotFoundException());
		this.mockMvc.perform(get("/owner/appointments/99")).andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void crossOwnerRequestIsNotFound() throws Exception {
		given(this.accounts.findByUsername("george")).willReturn(Optional.of(ownerAccount()));
		given(this.workflow.requireOwned(77L, 1)).willThrow(new OwnerResourceNotFoundException());
		this.mockMvc.perform(get("/owner/scheduling-requests/77/consent")).andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void crossOwnerPetIsNotFound() throws Exception {
		given(this.accounts.findByUsername("george")).willReturn(Optional.of(ownerAccount()));
		Owner owner = new Owner();
		owner.setId(1);
		Pet pet = new Pet();
		owner.addPet(pet);
		pet.setId(1);
		given(this.queries.profile(1)).willReturn(owner);
		this.mockMvc.perform(get("/owner/pets/2")).andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void crossOwnerOfferIsNotFound() throws Exception {
		given(this.accounts.findByUsername("george")).willReturn(Optional.of(ownerAccount()));
		given(this.offers.loadOwned(1L, 1, 5L)).willThrow(new OwnerResourceNotFoundException());
		this.mockMvc.perform(get("/owner/scheduling-requests/1/offers/5")).andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void profileUsesSessionOwner() throws Exception {
		given(this.accounts.findByUsername("george")).willReturn(Optional.of(ownerAccount()));
		Owner owner = new Owner();
		owner.setId(1);
		given(this.queries.profile(1)).willReturn(owner);
		this.mockMvc.perform(get("/owner/profile")).andExpect(status().isOk());
	}

	private Account ownerAccount() {
		Account account = new Account();
		account.setUsername("george");
		account.setRole(AccountRole.OWNER);
		account.setOwnerId(1);
		return account;
	}

}
