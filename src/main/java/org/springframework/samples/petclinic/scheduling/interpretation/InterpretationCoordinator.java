package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class InterpretationCoordinator {

	private static final Logger logger = LoggerFactory.getLogger(InterpretationCoordinator.class);

	private static final int MAX_ATTEMPTS = 2;

	private final InterpretationPort port;

	private final InterpretationSchemaValidator validator;

	private final Clock clock;

	public InterpretationCoordinator(InterpretationPort port, InterpretationSchemaValidator validator, Clock clock) {
		this.port = port;
		this.validator = validator;
		this.clock = clock;
	}

	public InterpretationExecutionEvidence interpret(InterpretationPort.InterpretationCallRequest request) {
		InterpretationExecutionEvidence evidence = new InterpretationExecutionEvidence();
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			if (!Instant.now(this.clock).isBefore(request.deadline())) {
				logger.warn("Interpretation attempt {}/{} abandoned: deadline {} reached", attempt, MAX_ATTEMPTS,
						request.deadline());
				evidence.setLastValidation(new InterpretationValidationResult(InterpretationClassification.TIMEOUT,
						null, java.util.Map.of(), java.util.List.of("TIMEOUT"), null));
				return evidence;
			}
			logger.debug("Interpretation attempt {}/{}: sourceTextChars={}, promptChars={}, deadline={}", attempt,
					MAX_ATTEMPTS, request.sourceText() == null ? 0 : request.sourceText().length(),
					request.prompt() == null ? 0 : request.prompt().length(), request.deadline());
			long startedAt = System.currentTimeMillis();
			InterpretationPort.InterpretationCallResult result = this.port.interpret(request);
			long elapsedMs = System.currentTimeMillis() - startedAt;
			evidence.addAttempt(result);
			logger.info(
					"Interpretation attempt {}/{} returned in {}ms: requestedModel={}, resolvedModel={}, "
							+ "transientFailure={}, responseChars={}",
					attempt, MAX_ATTEMPTS, elapsedMs, result.requestedModel(), result.resolvedModel(),
					result.transientFailure(), result.rawResponse() == null ? 0 : result.rawResponse().length());
			logger.debug("Interpretation attempt {}/{} raw LLM response:\n{}", attempt, MAX_ATTEMPTS,
					result.rawResponse());
			if (result.transientFailure() || result.rawResponse() == null) {
				logger.warn(
						"Interpretation attempt {}/{} treated as TRANSIENT_FAILURE (transientFailure={}, "
								+ "rawResponse={})",
						attempt, MAX_ATTEMPTS, result.transientFailure(),
						result.rawResponse() == null ? "null" : "present");
				evidence.setLastValidation(
						new InterpretationValidationResult(InterpretationClassification.TRANSIENT_FAILURE, null,
								java.util.Map.of(), java.util.List.of("TRANSIENT_FAILURE"), null));
				continue;
			}
			InterpretationValidationResult validation = this.validator.validate(result.rawResponse(),
					request.vocabulary());
			evidence.setLastValidation(validation);
			logger.info("Interpretation attempt {}/{} validated as {}: issueCodes={}, unknownFields={}", attempt,
					MAX_ATTEMPTS, validation.classification(), validation.issueCodes(),
					validation.unknownFields().keySet());
			if (validation.classification() == InterpretationClassification.VALID_REVIEWABLE
					|| validation.classification() == InterpretationClassification.VALID_NEEDS_STAFF) {
				return evidence;
			}
		}
		logger.warn("Interpretation exhausted {} attempts without a usable result; last classification={}",
				MAX_ATTEMPTS, evidence.lastValidation() == null ? "none" : evidence.lastValidation().classification());
		return evidence;
	}

}
