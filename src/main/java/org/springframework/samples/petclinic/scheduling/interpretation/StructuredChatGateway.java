package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.Instant;

public interface StructuredChatGateway {

	InterpretationPort.InterpretationCallResult complete(String prompt, String outputSchema, Instant deadline);

}
