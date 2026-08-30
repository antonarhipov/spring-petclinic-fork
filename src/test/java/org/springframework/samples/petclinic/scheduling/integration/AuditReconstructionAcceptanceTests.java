package org.springframework.samples.petclinic.scheduling.integration;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.samples.petclinic.scheduling.audit.AuditEvent;
import org.springframework.samples.petclinic.scheduling.audit.AuditEventRepository;
import org.springframework.samples.petclinic.scheduling.audit.AuditService;
import org.springframework.samples.petclinic.scheduling.audit.IntegrationAttempt;
import org.springframework.samples.petclinic.scheduling.audit.IntegrationExecution;
import org.springframework.samples.petclinic.scheduling.audit.IntegrationExecutionRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuditReconstructionAcceptanceTests {

	private static final List<String> FR_083_ACTIONS = List.of("STAFF_CHANGE", "QUEUE_CLAIM", "REQUEST_REVISION",
			"CONSENT_AGREE", "INTERPRETATION_OUTCOME", "MATCHING_OUTCOME", "OFFER_HELD", "HOLD_ACQUIRED",
			"OFFER_ACCEPTED", "OFFER_REJECTED", "APPOINTMENT_COMPLETED");

	@Autowired
	AuditService audit;

	@Autowired
	AuditEventRepository auditEvents;

	@Autowired
	IntegrationExecutionRepository executions;

	@Autowired
	SchedulingRequestRepository requests;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void fr083EventsAndBothIntegrationAttemptTypesReconstructFromDurableRowsWithoutLogs() {
		SchedulingRequest request = new SchedulingRequest();
		request.setOwnerId(1);
		request.setPetId(1);
		request.setState(RequestState.AWAITING_CONSENT);
		request.setOwnerStatusCode("AWAITING_CONSENT");
		request.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
		request.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
		request = this.requests.saveAndFlush(request);
		Long requestId = request.getId();
		for (String action : FR_083_ACTIONS) {
			this.audit.record("STAFF", null, action, "SchedulingRequest", String.valueOf(requestId), requestId,
					"{\"before\":\"" + action + "\"}", "{\"after\":\"" + action + "\"}");
		}

		IntegrationExecution llm = newExecution(requestId, "LLM_INTERPRETATION", "llm-" + requestId);
		addAttempt(llm, 1, "{\"text\":\"need checkup\"}", "{\"schemaVersion\":\"1.0\"}", "COMPLETED");
		this.executions.saveAndFlush(llm);
		IntegrationExecution timefold = newExecution(requestId, "TIMEFOLD_MATCH", "match-" + requestId);
		addAttempt(timefold, 1, "{\"mode\":\"PREFERRED_ONLY\"}", "{\"selected\":\"1@2026-09-01T14:00:00Z\"}",
				"SELECTED");
		this.executions.saveAndFlush(timefold);

		List<AuditEvent> stored = this.auditEvents.findAll()
			.stream()
			.filter(event -> requestId.equals(event.getRequestId()))
			.toList();
		assertThat(stored).extracting(AuditEvent::getAction).containsAll(FR_083_ACTIONS);
		assertThat(stored).allSatisfy(event -> {
			assertThat(event.getActorType()).isNotBlank();
			assertThat(event.getOccurredAt()).isNotNull();
			assertThat(event.getAction()).isNotBlank();
			assertThat(event.getTargetType()).isNotBlank();
			assertThat(event.getTargetId()).isNotBlank();
			assertThat(event.getAfterJson()).isNotBlank();
		});

		List<String> reconstructed = this.jdbc.query(
				"select actor_type, occurred_at, action, target_type, target_id, before_json, after_json from audit_events where request_id = ?",
				(rs, row) -> rs.getString("action") + ":" + rs.getString("actor_type") + ":"
						+ rs.getTimestamp("occurred_at") + ":" + rs.getString("target_type") + ":"
						+ rs.getString("target_id"),
				requestId);
		assertThat(reconstructed).hasSize(FR_083_ACTIONS.size());

		IntegrationExecution storedLlm = this.executions.findById(llm.getId()).orElseThrow();
		IntegrationExecution storedMatch = this.executions.findById(timefold.getId()).orElseThrow();
		assertThat(Set.of(storedLlm.getKind(), storedMatch.getKind())).containsExactlyInAnyOrder("LLM_INTERPRETATION",
				"TIMEFOLD_MATCH");
		assertThat(List.of(storedLlm, storedMatch)).allSatisfy(execution -> {
			assertThat(execution.getSchemaVersion()).isEqualTo("1.0");
			assertThat(execution.getInputJson()).isNotBlank();
			assertThat(execution.getTriggeredAt()).isNotNull();
			assertThat(execution.getDeadlineAt()).isNotNull();
			assertThat(execution.getFinishedAt()).isNotNull();
			assertThat(execution.getOutcome()).isNotBlank();
		});
		assertThat(this.jdbc.queryForList(
				"select input_json, raw_output, started_at, finished_at, outcome from integration_attempts where execution_id in (?, ?)",
				llm.getId(), timefold.getId()))
			.hasSize(2)
			.allSatisfy(row -> {
				assertThat(row.get("input_json")).isNotNull();
				assertThat(row.get("raw_output")).isNotNull();
				assertThat(row.get("started_at")).isNotNull();
				assertThat(row.get("finished_at")).isNotNull();
				assertThat(row.get("outcome")).isNotNull();
			});
		Integer attemptRows = this.jdbc.queryForObject(
				"select count(*) from integration_attempts where execution_id in (?, ?)", Integer.class, llm.getId(),
				timefold.getId());
		assertThat(attemptRows).isEqualTo(2);
	}

	private IntegrationExecution newExecution(Long requestId, String kind, String triggerKey) {
		IntegrationExecution execution = new IntegrationExecution();
		execution.setId(UUID.randomUUID());
		execution.setKind(kind);
		execution.setRequestId(requestId);
		execution.setTriggerKey(triggerKey);
		execution.setState("COMPLETE");
		execution.setTriggeredAt(Instant.parse("2026-01-01T00:00:00Z"));
		execution.setDeadlineAt(Instant.parse("2026-01-01T00:00:10Z"));
		execution.setStartedAt(Instant.parse("2026-01-01T00:00:01Z"));
		execution.setFinishedAt(Instant.parse("2026-01-01T00:00:02Z"));
		execution.setSchemaVersion("1.0");
		execution.setInputJson("{\"kind\":\"" + kind + "\"}");
		execution.setAttemptCount(1);
		execution.setOutcome("COMPLETE");
		return execution;
	}

	private void addAttempt(IntegrationExecution execution, int sequence, String input, String output, String outcome) {
		IntegrationAttempt attempt = new IntegrationAttempt();
		attempt.setExecution(execution);
		attempt.setSequence(sequence);
		attempt.setStartedAt(Instant.parse("2026-01-01T00:00:01Z"));
		attempt.setFinishedAt(Instant.parse("2026-01-01T00:00:02Z"));
		attempt.setInputJson(input);
		attempt.setRawOutput(output);
		attempt.setOutcome(outcome);
		execution.getAttempts().add(attempt);
	}

}
