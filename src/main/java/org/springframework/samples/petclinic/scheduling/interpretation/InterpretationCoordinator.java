package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;

@Service
public class InterpretationCoordinator {

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
		for (int attempt = 1; attempt <= 2; attempt++) {
			if (!Instant.now(this.clock).isBefore(request.deadline())) {
				evidence.setLastValidation(new InterpretationValidationResult(InterpretationClassification.TIMEOUT,
						null, java.util.Map.of(), java.util.List.of("TIMEOUT"), null));
				return evidence;
			}
			InterpretationPort.InterpretationCallResult result = this.port.interpret(request);
			evidence.addAttempt(result);
			if (result.transientFailure() || result.rawResponse() == null) {
				evidence.setLastValidation(
						new InterpretationValidationResult(InterpretationClassification.TRANSIENT_FAILURE, null,
								java.util.Map.of(), java.util.List.of("TRANSIENT_FAILURE"), null));
				continue;
			}
			InterpretationValidationResult validation = this.validator.validate(result.rawResponse(),
					request.vocabulary());
			evidence.setLastValidation(validation);
			if (validation.classification() == InterpretationClassification.VALID_REVIEWABLE
					|| validation.classification() == InterpretationClassification.VALID_NEEDS_STAFF) {
				return evidence;
			}
		}
		return evidence;
	}

}
