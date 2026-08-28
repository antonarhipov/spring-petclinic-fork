package org.springframework.samples.petclinic.scheduling.ai;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OllamaInterpretationService implements InterpretationPort {

	private static final Logger logger = LoggerFactory.getLogger(OllamaInterpretationService.class);

	private final ChatClient chatClient;

	private final InterpretationValidator validator;

	private final String model;

	public OllamaInterpretationService(ChatClient schedulingChatClient, InterpretationValidator validator,
			@Value("${spring.ai.ollama.chat.model}") String model) {
		this.chatClient = schedulingChatClient;
		this.validator = validator;
		this.model = model;
	}

	@Override
	public InterpretationResult interpret(String sourceText, String correlationId) {
		Instant started = Instant.now();
		try {
			ResponseEntity<ChatResponse, InterpretationResponse> exchange = this.chatClient.mutate()
				.build()
				.prompt()
				.system("""
						Return only a structured appointment interpretation. Extract a concise visit reason,
						durationMinutes (15, 30, 45, or 60), careType, requiredSpecialty when applicable,
						urgency, and preferredVeterinarian when stated. Use UNCERTAIN rather than guessing.
						""")
				.user(sourceText)
				.call()
				.responseEntity(InterpretationResponse.class,
						specification -> specification.useProviderStructuredOutput().validateSchema());
			InterpretationResponse response = exchange.entity();
			logger.debug("AI interpretation mapped correlationId={} model={} response={} metadata={}", correlationId,
					this.model, response, exchange.response().getMetadata());
			this.validator.validate(response);
			logger.debug("AI interpretation completed correlationId={} model={} elapsedMs={} validation=passed",
					correlationId, this.model, Duration.between(started, Instant.now()).toMillis());
			return InterpretationResult.success(response);
		}
		catch (IllegalArgumentException ex) {
			logger.debug(
					"AI interpretation rejected correlationId={} model={} elapsedMs={} validation=failed reason={}",
					correlationId, this.model, Duration.between(started, Instant.now()).toMillis(), ex.getMessage(),
					ex);
			return InterpretationResult.failed(InterpretationFailure.SEMANTIC_VALIDATION);
		}
		catch (Exception ex) {
			logger.debug("AI interpretation unavailable correlationId={} model={} elapsedMs={} reason={}",
					correlationId, this.model, Duration.between(started, Instant.now()).toMillis(), ex.getMessage(),
					ex);
			return InterpretationResult.failed(InterpretationFailure.UNAVAILABLE);
		}
	}

}
