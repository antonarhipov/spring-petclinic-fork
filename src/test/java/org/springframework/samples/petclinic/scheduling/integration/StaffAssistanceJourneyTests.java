package org.springframework.samples.petclinic.scheduling.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationPort;
import org.springframework.samples.petclinic.scheduling.queue.StaffQueueItem;
import org.springframework.samples.petclinic.scheduling.queue.StaffQueueRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StaffAssistanceJourneyTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	SchedulingRequestRepository requests;

	@Autowired
	StaffQueueRepository queueItems;

	@MockitoBean
	InterpretationPort interpretationPort;

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void declinedConsentGoesToSingleStaffQueueItem() throws Exception {
		this.mockMvc
			.perform(post("/owner/scheduling-requests").with(csrf())
				.param("petId", "1")
				.param("sourceText", "Leo needs a wellness exam next week in the morning"))
			.andExpect(status().is3xxRedirection());
		SchedulingRequest request = this.requests.findByOwnerIdOrderByUpdatedAtDesc(1).get(0);
		this.mockMvc
			.perform(post("/owner/scheduling-requests/{id}/consent/manual", request.getId()).with(csrf())
				.param("expectedVersion", request.getVersion().toString()))
			.andExpect(status().is3xxRedirection());
		request = this.requests.findById(request.getId()).orElseThrow();
		assertThat(request.getState()).isEqualTo(RequestState.STAFF_HANDLING);
		StaffQueueItem item = this.queueItems.findByRequestId(request.getId()).orElseThrow();
		this.mockMvc.perform(get("/owner/scheduling-requests/{id}/status", request.getId()))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/owner/staff-status"));
		this.mockMvc.perform(get("/staff/queue/" + item.getId()).with(user("admin").roles("STAFF")))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/staff/queue-item"));
		verify(this.interpretationPort, never()).interpret(any());
		assertThat(this.queueItems.findByRequestId(request.getId()).orElseThrow().getId()).isEqualTo(item.getId());
	}

}
