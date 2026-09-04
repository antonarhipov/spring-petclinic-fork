/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 */

package org.springframework.samples.petclinic.scheduling.interpretation;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.net.SocketTimeoutException;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Spring AI 2.x structured-output adapter; the RULE-17 default provider. */
@Service
@ConditionalOnProperty(name = "scheduling.ai.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaRequestInterpreter implements RequestInterpreter {

	private static final String PROMPT_VERSION = "1.0";

	private final ModelCall modelCall;

	private final String modelTag;

	@Autowired
	public OllamaRequestInterpreter(ChatClient schedulingOllamaChatClient,
			@Value("${spring.ai.ollama.chat.model}") String modelTag, InterpretationPromptFactory promptFactory) {
		this((reason, availability) -> callModel(schedulingOllamaChatClient,
				promptFactory.create(reason, availability)), modelTag);
	}

	OllamaRequestInterpreter(ModelCall modelCall, String modelTag) {
		this.modelCall = modelCall;
		this.modelTag = modelTag;
	}

	@Override
	public InterpretationResult interpret(String reasonText, String availabilityText) {
		try {
			return interpretOnce(reasonText, availabilityText);
		}
		catch (RuntimeException firstFailure) {
			if (isTransportFailure(firstFailure)) {
				throw unavailable(firstFailure);
			}
			try {
				return interpretOnce(reasonText, availabilityText);
			}
			catch (RuntimeException secondFailure) {
				throw unavailable(secondFailure);
			}
		}
	}

	private InterpretationResult interpretOnce(String reasonText, String availabilityText) {
		ModelExchange exchange = this.modelCall.call(reasonText, availabilityText);
		ModelOutput output = exchange.output();
		return new InterpretationResult(output.reasonSummary(), output.estimatedMinutes(), output.careType(),
				output.specialty(), output.preferredVetId(), output.cannotInterpret(), output.windows(),
				exchange.rawResponse(), this.modelTag, PROMPT_VERSION);
	}

	private static ModelExchange callModel(ChatClient chatClient, String prompt) {
		ResponseEntity<ChatResponse, ModelOutput> exchange = chatClient.prompt()
			.system("Extract a veterinary scheduling request. Use only GENERAL or SPECIALTY care types and preserve all availability windows.")
			.user(prompt)
			.call()
			.responseEntity(ModelOutput.class, spec -> spec.useProviderStructuredOutput().validateSchema());
		return new ModelExchange(exchange.entity(), exchange.response().getResult().getOutput().getText());
	}

	private static boolean isTransportFailure(Throwable failure) {
		return findCause(failure, IOException.class) != null;
	}

	private static ModelUnavailableException unavailable(RuntimeException failure) {
		String reason;
		if (findCause(failure, HttpTimeoutException.class) != null
				|| findCause(failure, SocketTimeoutException.class) != null) {
			reason = "model timeout";
		}
		else if (isTransportFailure(failure)) {
			reason = "model transport failure";
		}
		else {
			reason = "malformed model response after retry";
		}
		return new ModelUnavailableException(reason, failure);
	}

	private static <T extends Throwable> T findCause(Throwable failure, Class<T> type) {
		for (Throwable current = failure; current != null; current = current.getCause()) {
			if (type.isInstance(current)) {
				return type.cast(current);
			}
		}
		return null;
	}

	@FunctionalInterface
	interface ModelCall {

		ModelExchange call(String reasonText, String availabilityText);

	}

	record ModelExchange(ModelOutput output, String rawResponse) {
	}

	public record ModelOutput(String reasonSummary, Integer estimatedMinutes, CareType careType, String specialty,
			Integer preferredVetId, boolean cannotInterpret, List<AvailabilityWindow> windows) {
	}

}
