package org.springframework.samples.petclinic.scheduling.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationPort;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EmergencyFallbackTests {

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
	void emergencyTextNeverCallsLlmAndCreatesQueueItem() throws Exception {
		this.mockMvc
			.perform(post("/owner/scheduling-requests").with(csrf())
				.param("petId", "1")
				.param("sourceText", "Leo is bleeding from a deep cut and needs help now"))
			.andExpect(status().is3xxRedirection());
		SchedulingRequest request = this.requests.findByOwnerIdOrderByUpdatedAtDesc(1).get(0);
		assertThat(request.getState()).isEqualTo(RequestState.STAFF_HANDLING);
		assertThat(request.isSuspectedEmergency()).isTrue();
		assertThat(this.queueItems.findByRequestId(request.getId())).isPresent();
		verify(this.interpretationPort, never()).interpret(any());
	}

}
