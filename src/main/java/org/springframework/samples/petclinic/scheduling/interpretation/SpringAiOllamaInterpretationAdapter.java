package org.springframework.samples.petclinic.scheduling.interpretation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class SpringAiOllamaInterpretationAdapter implements InterpretationPort {

	private final StructuredChatGateway gateway;

	private final String outputSchema;

	public SpringAiOllamaInterpretationAdapter(StructuredChatGateway gateway) {
		this.gateway = gateway;
		try {
			this.outputSchema = new ClassPathResource("schemas/llm-interpretation-v1.schema.json")
				.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	@Override
	public InterpretationCallResult interpret(InterpretationCallRequest request) {
		if (Instant.now().isAfter(request.deadline())) {
			return new InterpretationCallResult(null, "gemma4:latest", null, true);
		}
		return this.gateway.complete(request.prompt(), this.outputSchema, request.deadline());
	}

	public String outputSchema() {
		return this.outputSchema;
	}

	public int maxFrameworkRetries() {
		return 1;
	}

}
