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

	static final String SYSTEM_PROMPT = """
			You interpret English veterinary appointment requests for scheduling. Do not diagnose the pet
			or provide medical advice. Return only the structured appointment interpretation.

			Use these classification rules:
			- visitReason: a concise symptom or requested service, using only facts from the request.
			- durationMinutes: exactly 15, 30, 45, or 60. Use 30 for a general symptom visit unless
			  the request clearly supports another duration.
			- careType: exactly GENERAL, SPECIALTY, or UNCERTAIN.
			  - GENERAL: wellness, preventive care, or an initial evaluation of a common symptom by a
			    general veterinarian. An unknown diagnosis does not make careType uncertain.
			  - SPECIALTY: the owner explicitly requests a specialist or names specialty care. Set
			    requiredSpecialty to that specialty.
			  - UNCERTAIN: only when the request is contradictory or too vague to choose GENERAL versus
			    SPECIALTY. Do not use UNCERTAIN merely because a pet has a symptom.
			- requiredSpecialty: omit unless careType is SPECIALTY and the specialty is stated.
			- urgency: exactly STANDARD, EMERGENCY, or UNCERTAIN.
			  - STANDARD: ordinary appointments with no stated immediate danger signs. This includes a
			    runny nose, mild cough, itching, or limping when no danger signs are stated.
			  - EMERGENCY: only when the request explicitly states an immediate danger sign, such as the
			    pet not breathing, being unconscious, having a seizure, uncontrolled bleeding, or known
			    poisoning.
			  - UNCERTAIN: when the request suggests possible immediate danger but omits the severity or
			    contains contradictory danger information. Do not use UNCERTAIN for a common mild symptom
			    merely because its severity was not stated; use STANDARD when no danger sign is stated.
			- preferredVeterinarian: omit unless the veterinarian's name is explicitly stated.

			Example:
			Request: "leo has running nose\nplease schedule the visit for next thursday after lunch"
			Interpretation: visitReason="Runny nose", durationMinutes=30, careType="GENERAL",
			urgency="STANDARD". Omit requiredSpecialty and preferredVeterinarian.
			""";

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
				.system(SYSTEM_PROMPT)
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
