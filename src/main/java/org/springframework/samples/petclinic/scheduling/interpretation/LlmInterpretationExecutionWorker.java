package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.samples.petclinic.scheduling.audit.IntegrationAttempt;
import org.springframework.samples.petclinic.scheduling.audit.IntegrationExecution;
import org.springframework.samples.petclinic.scheduling.audit.IntegrationExecutionRepository;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityRepository;
import org.springframework.samples.petclinic.scheduling.job.IntegrationExecutionWorker;
import org.springframework.samples.petclinic.scheduling.request.RequestTextRevision;
import org.springframework.samples.petclinic.scheduling.request.RequestTextRevisionRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestWorkflowService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LlmInterpretationExecutionWorker implements IntegrationExecutionWorker {

	private final IntegrationExecutionRepository executions;

	private final RequestTextRevisionRepository texts;

	private final InterpretationCoordinator coordinator;

	private final PromptFactory prompts;

	private final ClinicVocabularyFactory vocabularies;

	private final AvailabilityRepository policies;

	private final RequestWorkflowService workflow;

	private final Clock clock;

	public LlmInterpretationExecutionWorker(IntegrationExecutionRepository executions,
			RequestTextRevisionRepository texts, InterpretationCoordinator coordinator, PromptFactory prompts,
			ClinicVocabularyFactory vocabularies, AvailabilityRepository policies,
			@Lazy RequestWorkflowService workflow, Clock clock) {
		this.executions = executions;
		this.texts = texts;
		this.coordinator = coordinator;
		this.prompts = prompts;
		this.vocabularies = vocabularies;
		this.policies = policies;
		this.workflow = workflow;
		this.clock = clock;
	}

	@Override
	public String kind() {
		return "LLM_INTERPRETATION";
	}

	@Override
	@Transactional
	public void execute(UUID executionId) {
		IntegrationExecution execution = this.executions.findById(executionId).orElseThrow();
		RequestTextRevision text = this.texts.findById(execution.getTextRevisionId()).orElseThrow();
		ClinicVocabulary vocabulary = this.vocabularies.current();
		var policy = this.policies.currentPolicy();
		String prompt = this.prompts.render(text.getSourceText(), text.getSubmittedAt(), Instant.now(this.clock),
				vocabulary, policy.getBookingHorizonDays(), policy.getOwnerMinimumNoticeMinutes());
		InterpretationPort.InterpretationCallRequest request = new InterpretationPort.InterpretationCallRequest(
				text.getSourceText(), text.getSubmittedAt(), execution.getDeadlineAt(), prompt,
				"schemas/llm-interpretation-v1.schema.json", vocabulary);
		InterpretationExecutionEvidence evidence = this.coordinator.interpret(request);
		InterpretationValidationResult validation = evidence.lastValidation();
		execution.setPromptTemplateVersion(this.prompts.templateVersion());
		int seq = 1;
		int attemptTotal = evidence.attempts().size();
		for (InterpretationPort.InterpretationCallResult attempt : evidence.attempts()) {
			boolean isLast = seq == attemptTotal;
			IntegrationAttempt row = new IntegrationAttempt();
			row.setExecution(execution);
			row.setSequence(seq++);
			row.setStartedAt(Instant.now(this.clock));
			row.setFinishedAt(Instant.now(this.clock));
			row.setInputJson(execution.getInputJson());
			row.setRawOutput(attempt.rawResponse());
			row.setOutcome(attempt.transientFailure() ? "TRANSIENT_FAILURE" : "COMPLETED");
			if (attempt.transientFailure()) {
				row.setErrorClassification("TRANSIENT_FAILURE");
			}
			else if (isLast && validation != null && !validation.reviewable()) {
				row.setErrorClassification(validation.classification().name());
			}
			execution.getAttempts().add(row);
			execution.setRequestedModelId(attempt.requestedModel());
			execution.setResolvedModelId(attempt.resolvedModel());
		}
		execution.setAttemptCount(evidence.attempts().size());
		execution.setFinishedAt(Instant.now(this.clock));
		if (validation == null) {
			execution.setState("FAILED");
			execution.setOutcome("NO_RESULT");
			return;
		}
		execution.setOutcome(validation.classification().name());
		execution.setState("COMPLETE");
		this.workflow.applyInterpretation(execution.getRequestId(), execution.getTextRevisionId(), validation,
				vocabulary);
	}

}
