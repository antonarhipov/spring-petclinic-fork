package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.scheduling.audit.IntegrationExecution;
import org.springframework.samples.petclinic.scheduling.audit.IntegrationExecutionRepository;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityRepository;
import org.springframework.samples.petclinic.scheduling.availability.ClinicSchedulingPolicy;
import org.springframework.samples.petclinic.scheduling.request.RequestTextRevision;
import org.springframework.samples.petclinic.scheduling.request.RequestTextRevisionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmInterpretationExecutionWorkerTests {

	private IntegrationExecutionRepository executions;

	private RequestTextRevisionRepository texts;

	private InterpretationCoordinator coordinator;

	private PromptFactory prompts;

	private ClinicVocabularyFactory vocabularies;

	private AvailabilityRepository policies;

	private org.springframework.samples.petclinic.scheduling.request.RequestWorkflowService workflow;

	private LlmInterpretationExecutionWorker worker;

	private final UUID executionId = UUID.fromString("11111111-1111-1111-1111-111111111111");

	private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

	@BeforeEach
	void setup() {
		this.executions = mock(IntegrationExecutionRepository.class);
		this.texts = mock(RequestTextRevisionRepository.class);
		this.coordinator = mock(InterpretationCoordinator.class);
		this.prompts = mock(PromptFactory.class);
		this.vocabularies = mock(ClinicVocabularyFactory.class);
		this.policies = mock(AvailabilityRepository.class);
		this.workflow = mock(org.springframework.samples.petclinic.scheduling.request.RequestWorkflowService.class);
		this.worker = new LlmInterpretationExecutionWorker(this.executions, this.texts, this.coordinator, this.prompts,
				this.vocabularies, this.policies, this.workflow, Clock.fixed(this.now, ZoneOffset.UTC));

		ClinicSchedulingPolicy policy = new ClinicSchedulingPolicy();
		policy.setZoneId("America/Chicago");
		policy.setBookingHorizonDays(30);
		when(this.policies.currentPolicy()).thenReturn(policy);
		when(this.vocabularies.current()).thenReturn(vocabulary());
	}

	@Test
	void successfulExecutionRecordsAttemptAndAppliesInterpretation() {
		IntegrationExecution execution = execution();
		RequestTextRevision text = text();
		when(this.executions.findById(this.executionId)).thenReturn(Optional.of(execution));
		when(this.texts.findById(4L)).thenReturn(Optional.of(text));
		InterpretationPort.InterpretationCallResult result = new InterpretationPort.InterpretationCallResult(
				"{\"visitReason\":\"exam\"}", "gemma4:latest", "gemma4:latest", false);
		InterpretationExecutionEvidence evidence = new InterpretationExecutionEvidence();
		evidence.addAttempt(result);
		evidence.setLastValidation(validation(InterpretationClassification.VALID_REVIEWABLE));
		when(this.coordinator.interpret(any())).thenReturn(evidence);

		this.worker.execute(this.executionId);

		assertThat(execution.getState()).isEqualTo("COMPLETE");
		assertThat(execution.getOutcome()).isEqualTo("VALID_REVIEWABLE");
		assertThat(execution.getAttemptCount()).isEqualTo(1);
		assertThat(execution.getFinishedAt()).isEqualTo(this.now);
		assertThat(execution.getAttempts()).singleElement().satisfies(attempt -> {
			assertThat(attempt.getSequence()).isEqualTo(1);
			assertThat(attempt.getRawOutput()).isEqualTo(result.rawResponse());
			assertThat(attempt.getOutcome()).isEqualTo("COMPLETED");
		});
		verify(this.workflow).applyInterpretation(7L, 4L, evidence.lastValidation(), vocabulary());
	}

	@Test
	void noResultMarksExecutionFailedAndDoesNotApplyInterpretation() {
		IntegrationExecution execution = execution();
		when(this.executions.findById(this.executionId)).thenReturn(Optional.of(execution));
		when(this.texts.findById(4L)).thenReturn(Optional.of(text()));
		InterpretationExecutionEvidence evidence = new InterpretationExecutionEvidence();
		InterpretationPort.InterpretationCallResult failed = new InterpretationPort.InterpretationCallResult(null,
				"gemma4:latest", "gemma4:latest", true);
		evidence.addAttempt(failed);
		when(this.coordinator.interpret(any())).thenReturn(evidence);

		this.worker.execute(this.executionId);

		assertThat(execution.getState()).isEqualTo("FAILED");
		assertThat(execution.getOutcome()).isEqualTo("NO_RESULT");
		assertThat(execution.getAttemptCount()).isEqualTo(1);
		assertThat(execution.getAttempts()).singleElement().satisfies(attempt -> {
			assertThat(attempt.getRawOutput()).isNull();
			assertThat(attempt.getOutcome()).isEqualTo("TRANSIENT_FAILURE");
		});
		verify(this.workflow, never()).applyInterpretation(any(), any(), any(), any());
	}

	@Test
	void completedNeedsStaffResultStillAppliesValidationForWorkflowRouting() {
		IntegrationExecution execution = execution();
		when(this.executions.findById(this.executionId)).thenReturn(Optional.of(execution));
		when(this.texts.findById(4L)).thenReturn(Optional.of(text()));
		InterpretationExecutionEvidence evidence = new InterpretationExecutionEvidence();
		evidence
			.addAttempt(new InterpretationPort.InterpretationCallResult("{}", "gemma4:latest", "gemma4:latest", false));
		evidence.setLastValidation(validation(InterpretationClassification.VALID_NEEDS_STAFF));
		when(this.coordinator.interpret(any())).thenReturn(evidence);

		this.worker.execute(this.executionId);

		assertThat(execution.getState()).isEqualTo("COMPLETE");
		assertThat(execution.getOutcome()).isEqualTo("VALID_NEEDS_STAFF");
		verify(this.workflow).applyInterpretation(7L, 4L, evidence.lastValidation(), vocabulary());
	}

	private IntegrationExecution execution() {
		IntegrationExecution execution = new IntegrationExecution();
		execution.setId(this.executionId);
		execution.setRequestId(7L);
		execution.setTextRevisionId(4L);
		execution.setDeadlineAt(this.now.plusSeconds(10));
		execution.setInputJson("{\"textRevisionId\":4}");
		return execution;
	}

	private RequestTextRevision text() {
		RequestTextRevision text = new RequestTextRevision();
		text.setRequestId(7L);
		text.setSourceText("Leo needs an examination");
		text.setSubmittedAt(this.now.minusSeconds(30));
		return text;
	}

	private ClinicVocabulary vocabulary() {
		return new ClinicVocabulary(java.util.Set.of(30), java.util.Set.of(), java.util.Set.of(), java.util.Map.of(),
				java.util.Map.of(), "America/Chicago");
	}

	private InterpretationValidationResult validation(InterpretationClassification classification) {
		AppointmentInterpretationV1 dto = new AppointmentInterpretationV1("1.0", "exam", 30, "WELLNESS", null,
				List.of(), List.of(), List.of(), null, "NONE", "ROUTINE", List.of(), List.of());
		return new InterpretationValidationResult(classification, dto, java.util.Map.of(), List.of(), "{}");
	}

}