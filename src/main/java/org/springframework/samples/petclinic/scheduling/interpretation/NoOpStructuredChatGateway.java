package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.Instant;

import org.springframework.stereotype.Component;

@Component
public class NoOpStructuredChatGateway implements StructuredChatGateway {

	@Override
	public InterpretationPort.InterpretationCallResult complete(String prompt, String outputSchema, Instant deadline) {
		return new InterpretationPort.InterpretationCallResult(null, "gemma4:latest", null, true);
	}

}
