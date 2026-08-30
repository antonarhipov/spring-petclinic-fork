package org.springframework.samples.petclinic.scheduling.web.staff;

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
import org.springframework.samples.petclinic.scheduling.interpretation.ManualInterpretationService;
import org.springframework.samples.petclinic.scheduling.queue.StaffAssistedSchedulingService;
import org.springframework.samples.petclinic.scheduling.queue.StaffQueueItem;
import org.springframework.samples.petclinic.scheduling.queue.StaffQueueService;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
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

@WebMvcTest(StaffQueueController.class)
@Import(SecurityConfiguration.class)
class StaffQueueControllerTests {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	StaffQueueService queue;

	@MockitoBean
	StaffAssistedSchedulingService assisted;

	@MockitoBean
	ManualInterpretationService manualInterpretation;

	@MockitoBean
	SchedulingRequestRepository requests;

	@MockitoBean
	AccountRepository accounts;

	@Test
	@WithMockUser(username = "admin", roles = "STAFF")
	void listShowsQueue() throws Exception {
		given(this.queue.openQueue()).willReturn(List.of());
		this.mockMvc.perform(get("/staff/queue"))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/staff/queue"));
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void ownerForbidden() throws Exception {
		this.mockMvc.perform(get("/staff/queue")).andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(username = "admin", roles = "STAFF")
	void claimRedirects() throws Exception {
		Account account = new Account();
		account.setUsername("admin");
		account.setRole(AccountRole.STAFF);
		ReflectionTestUtils.setField(account, "id", 2L);
		given(this.accounts.findByUsername("admin")).willReturn(Optional.of(account));
		this.mockMvc.perform(post("/staff/queue/9/claim").with(csrf()).param("expectedVersion", "0"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/queue/9"));
		verify(this.queue).claim(9L, 2L, 0);
	}

	@Test
	@WithMockUser(username = "admin", roles = "STAFF")
	void unclaimAndBookAreStaffOnly() throws Exception {
		Account account = new Account();
		account.setUsername("admin");
		account.setRole(AccountRole.STAFF);
		ReflectionTestUtils.setField(account, "id", 2L);
		given(this.accounts.findByUsername("admin")).willReturn(Optional.of(account));
		StaffQueueItem item = new StaffQueueItem();
		item.setRequestId(5L);
		given(this.queue.require(9L)).willReturn(item);
		this.mockMvc.perform(
				post("/staff/queue/9/unclaim").with(csrf()).param("expectedVersion", "0").param("reason", "handoff"))
			.andExpect(status().is3xxRedirection());
		verify(this.queue).unclaim(9L, 2L, 0, "handoff");
		this.mockMvc
			.perform(post("/staff/queue/9/book").with(csrf())
				.param("veterinarianId", "1")
				.param("startAt", "2026-03-16T15:00:00Z")
				.param("endAt", "2026-03-16T15:30:00Z")
				.param("authorizationBasis", "OWNER_AGREEMENT")
				.param("agreementAt", "2026-03-16T14:00:00Z")
				.param("agreementMethod", "PHONE"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/queue"));
	}

	@Test
	@WithMockUser(username = "admin", roles = "STAFF")
	void detailOmitsOwnerLeakFieldsFromTemplateModelOnly() throws Exception {
		StaffQueueItem item = new StaffQueueItem();
		item.setRequestId(5L);
		given(this.queue.require(9L)).willReturn(item);
		given(this.queue.notes(9L)).willReturn(List.of());
		given(this.requests.findById(5L)).willReturn(Optional.of(new SchedulingRequest()));
		this.mockMvc.perform(get("/staff/queue/9"))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/staff/queue-item"));
	}

}
