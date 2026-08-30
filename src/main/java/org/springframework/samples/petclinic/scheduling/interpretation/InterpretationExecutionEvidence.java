package org.springframework.samples.petclinic.scheduling.interpretation;

import java.util.ArrayList;
import java.util.List;

public class InterpretationExecutionEvidence {

	private final List<InterpretationPort.InterpretationCallResult> attempts = new ArrayList<>();

	private InterpretationValidationResult lastValidation;

	public void addAttempt(InterpretationPort.InterpretationCallResult result) {
		this.attempts.add(result);
	}

	public List<InterpretationPort.InterpretationCallResult> attempts() {
		return this.attempts;
	}

	public InterpretationValidationResult lastValidation() {
		return this.lastValidation;
	}

	public void setLastValidation(InterpretationValidationResult lastValidation) {
		this.lastValidation = lastValidation;
	}

}
