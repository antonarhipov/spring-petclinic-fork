package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Placeholder gateway. No live LLM client is wired into this application: nothing in
 * {@code src/main/java} calls Spring AI, so this is the only
 * {@link StructuredChatGateway} implementation. Every interpretation attempt therefore
 * reports a transient failure and the scheduling request is routed to staff. The warning
 * below is the only signal that the pipeline is stubbed rather than broken.
 */
@Component
public class NoOpStructuredChatGateway implements StructuredChatGateway {

	private static final Logger logger = LoggerFactory.getLogger(NoOpStructuredChatGateway.class);

	static final String STUB_MODEL = "gemma4:latest";

	@Override
	public InterpretationPort.InterpretationCallResult complete(String prompt, String outputSchema, Instant deadline) {
		logger.warn(
				"LLM interpretation is STUBBED: no StructuredChatGateway implementation calls a model, so this "
						+ "attempt returns a transient failure and the request will be routed to staff. "
						+ "promptChars={}, schemaChars={}, deadline={}",
				prompt == null ? 0 : prompt.length(), outputSchema == null ? 0 : outputSchema.length(), deadline);
		logger.debug("LLM prompt that would have been sent:\n{}", prompt);
		return new InterpretationPort.InterpretationCallResult(null, STUB_MODEL, null, true);
	}

}
