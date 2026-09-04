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

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Spring AI 2.x structured-output adapter; the RULE-17 default provider. */
@Service
@ConditionalOnProperty(name = "scheduling.ai.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaRequestInterpreter implements RequestInterpreter {

	private static final String PROMPT_VERSION = "1.0";

	private final ChatClient chatClient;

	private final String modelTag;

	public OllamaRequestInterpreter(ChatClient schedulingOllamaChatClient,
			@Value("${spring.ai.ollama.chat.model}") String modelTag) {
		this.chatClient = schedulingOllamaChatClient;
		this.modelTag = modelTag;
	}

	@Override
	public InterpretationResult interpret(String reasonText, String availabilityText) {
		ResponseEntity<ChatResponse, ModelOutput> exchange = this.chatClient.prompt()
			.system("Extract a veterinary scheduling request. Use only GENERAL or SPECIALTY care types and preserve all availability windows.")
			.user("Reason: " + reasonText + "\nAvailability: " + availabilityText)
			.call()
			.responseEntity(ModelOutput.class, spec -> spec.useProviderStructuredOutput().validateSchema());
		ModelOutput output = exchange.entity();
		return new InterpretationResult(output.reasonSummary(), output.estimatedMinutes(), output.careType(),
				output.specialty(), output.preferredVetId(), output.cannotInterpret(), output.windows(),
				exchange.response().getResult().getOutput().getText(), this.modelTag, PROMPT_VERSION);
	}

	public record ModelOutput(String reasonSummary, Integer estimatedMinutes, CareType careType, String specialty,
			Integer preferredVetId, boolean cannotInterpret, List<AvailabilityWindow> windows) {
	}

}
