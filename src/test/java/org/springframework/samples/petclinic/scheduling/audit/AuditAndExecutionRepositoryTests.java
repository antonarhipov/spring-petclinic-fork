package org.springframework.samples.petclinic.scheduling.audit;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.scheduling.config.ClockConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({ ClockConfiguration.class, AuditService.class })
class AuditAndExecutionRepositoryTests {

	@Autowired
	AuditEventRepository auditEvents;

	@Autowired
	AuditService auditService;

	@Autowired
	IntegrationExecutionRepository executions;

	@Autowired
	SchedulingRequestRepository requests;

	@Test
	void auditIsAppendOnlyWithPayload() {
		AuditEvent event = this.auditService.record("SYSTEM", null, "TEST", "Request", "1", 1L, "{}", "{\"ok\":true}");
		assertThat(event.getId()).isNotNull();
		assertThat(event.getAfterJson()).contains("ok");
		assertThat(this.auditEvents.count()).isEqualTo(1);
	}

	@Test
	void triggerKeyIsUniqueAndClaimIsOptimistic() {
		SchedulingRequest request = new SchedulingRequest();
		request.setOwnerId(1);
		request.setPetId(1);
		request.setState(RequestState.AWAITING_CONSENT);
		request.setOwnerStatusCode("awaiting");
		request.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
		request.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
		this.requests.saveAndFlush(request);

		IntegrationExecution execution = newExecution(request.getId(), "key-unique-" + request.getId());
		this.executions.saveAndFlush(execution);
		int claimed = this.executions.claimPending(execution.getId(), Instant.parse("2026-01-01T00:00:01Z"));
		assertThat(claimed).isEqualTo(1);
		int claimedAgain = this.executions.claimPending(execution.getId(), Instant.parse("2026-01-01T00:00:02Z"));
		assertThat(claimedAgain).isEqualTo(0);
	}

	@Test
	void triggerKeyIsUnique() {
		SchedulingRequest request = new SchedulingRequest();
		request.setOwnerId(1);
		request.setPetId(1);
		request.setState(RequestState.AWAITING_CONSENT);
		request.setOwnerStatusCode("awaiting");
		request.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
		request.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
		this.requests.saveAndFlush(request);
		IntegrationExecution execution = newExecution(request.getId(), "dup-key-" + request.getId());
		this.executions.saveAndFlush(execution);
		IntegrationExecution duplicate = newExecution(request.getId(), execution.getTriggerKey());
		duplicate.setId(UUID.randomUUID());
		assertThatThrownBy(() -> this.executions.saveAndFlush(duplicate))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private static IntegrationExecution newExecution(Long requestId, String key) {
		IntegrationExecution execution = new IntegrationExecution();
		execution.setId(UUID.randomUUID());
		execution.setKind("LLM_INTERPRETATION");
		execution.setRequestId(requestId);
		execution.setTriggerKey(key);
		execution.setState("PENDING");
		execution.setTriggeredAt(Instant.parse("2026-01-01T00:00:00Z"));
		execution.setDeadlineAt(Instant.parse("2026-01-01T00:00:10Z"));
		execution.setSchemaVersion("v1");
		execution.setInputJson("{\"text\":\"need checkup\"}");
		return execution;
	}

}
