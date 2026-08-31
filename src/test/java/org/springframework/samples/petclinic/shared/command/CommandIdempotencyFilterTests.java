package org.springframework.samples.petclinic.shared.command;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.samples.petclinic.account.Role;
import org.springframework.samples.petclinic.audit.AuditEvent;
import org.springframework.samples.petclinic.audit.AuditService;
import org.springframework.samples.petclinic.security.PetClinicPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommandIdempotencyFilterTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AtomicInteger executions;

	@Autowired
	private AuditService auditService;

	private PetClinicPrincipal staff;

	@BeforeEach
	void setUp() {
		this.executions.set(0);
		this.staff = new PetClinicPrincipal(1L, "staff", "password", Role.STAFF, null, true, null, false, 0);
	}

	@Test
	void duplicateSubmissionReplaysCanonicalRedirectWithoutRepeatingMutation() throws Exception {
		UUID commandId = UUID.randomUUID();

		for (int submission = 0; submission < 2; submission++) {
			this.mockMvc
				.perform(post("/staff/command-filter-test").with(user(this.staff))
					.with(csrf())
					.param("commandId", commandId.toString())
					.param("expectedVersion", "7")
					.param("value", "same"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/staff/queue"));
		}

		assertThat(this.executions).hasValue(1);
		assertThat(this.auditService.findEventsForTarget("OwnerAdministration", "/staff/command-filter-test"))
			.filteredOn(event -> commandId.equals(event.getCommandId()))
			.singleElement()
			.extracting(AuditEvent::getCommandId)
			.isEqualTo(commandId);
	}

	@Test
	void renderedMutationFormContainsIssuedCommandId() throws Exception {
		this.mockMvc.perform(get("/staff/clinic-policy").with(user(this.staff)))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"commandId\"")));
	}

	@Test
	void rejectsReuseByAnotherActor() throws Exception {
		UUID commandId = UUID.randomUUID();
		PetClinicPrincipal otherStaff = new PetClinicPrincipal(2L, "staff2", "password", Role.STAFF, null, true, null,
				false, 0);

		this.mockMvc
			.perform(post("/staff/command-filter-test").with(user(this.staff))
				.with(csrf())
				.param("commandId", commandId.toString())
				.param("expectedVersion", "7")
				.param("value", "same"))
			.andExpect(status().isFound());

		this.mockMvc
			.perform(post("/staff/command-filter-test").with(user(otherStaff))
				.with(csrf())
				.param("commandId", commandId.toString())
				.param("expectedVersion", "7")
				.param("value", "same"))
			.andExpect(status().is(HttpStatus.CONFLICT.value()));

		assertThat(this.executions).hasValue(1);
	}

	@TestConfiguration
	static class CommandTestConfiguration {

		@Bean
		AtomicInteger commandExecutions() {
			return new AtomicInteger();
		}

		@Bean
		CommandFilterTestController commandFilterTestController(AtomicInteger executions) {
			return new CommandFilterTestController(executions);
		}

	}

	@Controller
	static class CommandFilterTestController {

		private final AtomicInteger executions;

		CommandFilterTestController(AtomicInteger executions) {
			this.executions = executions;
		}

		@PostMapping("/staff/command-filter-test")
		String mutate() {
			this.executions.incrementAndGet();
			return "redirect:/staff/queue";
		}

	}

}
