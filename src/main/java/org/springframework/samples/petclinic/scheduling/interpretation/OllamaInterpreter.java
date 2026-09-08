package org.springframework.samples.petclinic.scheduling.interpretation;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
public class OllamaInterpreter implements Interpreter {

	private static final Logger logger = LoggerFactory.getLogger(OllamaInterpreter.class);

	static final String SYSTEM_PROMPT = """
			Interpret the scheduling request using only the supplied clinic data. Resolve relative dates against today.
			For one upcoming named weekday ("Thursday", "this Thursday", or "next Thursday"), copy the exact date from
			upcomingWeekdayDates; do not calculate it. Return concrete and relative dates as absolute clinic-local dates.
			Convert named parts of day using partsOfDay. Expand broad availability and exclusions, such as any day except
			Wednesday, into explicit windows. Use only the enumerated clinic specialties or OTHER and only enumerated
			veterinarian ids. Leave care type and duration absent when the owner gives no visit reason so application defaults
			apply. In each window, set day to YYYY-MM-DD for a concrete or relative date, or to an uppercase weekday only for
			recurring weekly availability such as "every Thursday". Return start and end as clinic-local HH:mm without seconds
			or a UTC offset. Do not infer urgency.
			""";

	private final ChatClient chatClient;

	private final String modelTag;

	public OllamaInterpreter(ChatClient chatClient,
			@Value("${spring.ai.ollama.chat.model:ministral-3:14b}") String modelTag) {
		this.chatClient = chatClient;
		this.modelTag = modelTag;
	}

	@Override
	public CompletionStage<InterpretationResult> interpret(String prompt) {
		try {
			logger.info(
					"LLM request payload: model={}, temperature={}, systemPrompt={}, userPrompt={}, responseType={}",
					this.modelTag, 0.0, SYSTEM_PROMPT, prompt, OllamaInterpretationResponse.class.getName());
			StructuredOutputValidationAdvisor validation = StructuredOutputValidationAdvisor.builder()
				.outputType(OllamaInterpretationResponse.class)
				.maxRepeatAttempts(0)
				.build();
			ResponseEntity<ChatResponse, OllamaInterpretationResponse> response = this.chatClient.prompt()
				.options(OllamaChatOptions.builder().model(this.modelTag).temperature(0.0))
				.system(SYSTEM_PROMPT)
				.user(prompt)
				.advisors(validation)
				.call()
				.responseEntity(OllamaInterpretationResponse.class, entity -> entity.useProviderStructuredOutput());
			InterpretationResult.ModelOutput output = response.entity().toModelOutput();
			logger.info("LLM structured response: model={}, response={}", this.modelTag, output);
			String rawJson = response.response().getResult().getOutput().getText();
			return CompletableFuture.completedFuture(new InterpretationResult(output, rawJson));
		}
		catch (RestClientException exception) {
			logger.warn("LLM interaction failed: model={}, category=TRANSPORT, exception={}", this.modelTag,
					exception.getClass().getSimpleName());
			return CompletableFuture.failedFuture(new InterpretationException(FailureKind.TRANSPORT, null, exception));
		}
		catch (RuntimeException exception) {
			logger.warn("LLM interaction failed: model={}, category=UNPARSEABLE, exception={}", this.modelTag,
					exception.getClass().getSimpleName());
			return CompletableFuture
				.failedFuture(new InterpretationException(FailureKind.UNPARSEABLE, null, exception));
		}
	}

}
