package org.springframework.samples.petclinic.scheduling.interpretation;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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

	static final String SYSTEM_PROMPT = """
			Interpret the scheduling request using only the supplied clinic data. Resolve relative dates against today and
			return concrete dates as absolute clinic-local dates. Convert named parts of day using partsOfDay. Expand broad
			availability and exclusions, such as any day except Wednesday, into explicit windows. Use only the enumerated
			clinic specialties or OTHER and only enumerated veterinarian ids. Leave care type and duration absent when the
			owner gives no visit reason so application defaults apply. Do not infer urgency.
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
			StructuredOutputValidationAdvisor validation = StructuredOutputValidationAdvisor.builder()
				.outputType(InterpretationResult.ModelOutput.class)
				.maxRepeatAttempts(0)
				.build();
			ResponseEntity<ChatResponse, InterpretationResult.ModelOutput> response = this.chatClient.prompt()
				.options(OllamaChatOptions.builder().model(this.modelTag).temperature(0.0))
				.system(SYSTEM_PROMPT)
				.user(prompt)
				.advisors(validation)
				.call()
				.responseEntity(InterpretationResult.ModelOutput.class, entity -> entity.useProviderStructuredOutput());
			String rawJson = response.response().getResult().getOutput().getText();
			return CompletableFuture.completedFuture(new InterpretationResult(response.entity(), rawJson));
		}
		catch (RestClientException exception) {
			return CompletableFuture.failedFuture(new InterpretationException(FailureKind.TRANSPORT, null, exception));
		}
		catch (RuntimeException exception) {
			return CompletableFuture
				.failedFuture(new InterpretationException(FailureKind.UNPARSEABLE, null, exception));
		}
	}

}
