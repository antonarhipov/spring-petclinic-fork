package org.springframework.samples.petclinic.scheduling.interpretation;

public interface InterpretationClient {

	InterpretationCandidate interpret(InterpretationPrompt prompt);

}
