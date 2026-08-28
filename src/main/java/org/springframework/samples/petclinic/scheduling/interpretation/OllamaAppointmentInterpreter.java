/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.util.JsonHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class OllamaAppointmentInterpreter implements AppointmentInterpreter {

	private static final Logger logger = LoggerFactory.getLogger(OllamaAppointmentInterpreter.class);

	private static final String SYSTEM_PROMPT = """
			Interpret an English veterinary appointment request. Return only the requested structured data.
			A window must contain a dayOfWeek and either a clinic dayPart (morning, afternoon, evening)
			or an explicit local start/end range. For explicit ranges, use local times in HH:mm or HH:mm:ss
			format without Z, a UTC offset, or a time-zone id. When using dayPart, omit start and end.
			The clinic applies its own time zone. Do not return a zoneId.
			Omit unknown optional values. Do not invent a veterinarian id or specialty, and never use 0
			or an empty string as a placeholder for an unknown value.
			Urgency is advisory only and must be ROUTINE, SOON, URGENT, or SUSPECTED_EMERGENCY.
			""";

	private final OllamaChatModel chatModel;

	private final Duration timeout;

	private final BeanOutputConverter<OllamaAppointmentInterpretation> outputConverter = new BeanOutputConverter<>(
			OllamaAppointmentInterpretation.class);

	private final JsonHelper jsonHelper = new JsonHelper();

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	public OllamaAppointmentInterpreter(OllamaChatModel chatModel,
			@Value("${petclinic.ai.interpretation.timeout:30s}") Duration timeout) {
		this.chatModel = chatModel;
		this.timeout = timeout;
	}

	@Override
	public AppointmentInterpretation interpret(String freeText) {
		OllamaChatOptions defaults = this.chatModel.getOptions();
		OllamaChatOptions options = defaults.mutate()
			.temperature(0.0)
			.outputSchema(this.outputConverter.getJsonSchema())
			.build();
		Prompt prompt = new Prompt(List.of(new SystemMessage(SYSTEM_PROMPT + "\n" + this.outputConverter.getFormat()),
				new UserMessage(freeText)), options);

		Future<AppointmentInterpretation> call = this.executor.submit(() -> {
			logger.info("LLM request started. payload={}", requestPayload(prompt, options));
			ChatResponse chatResponse = this.chatModel.call(prompt);
			String response = chatResponse.getResult().getOutput().getText();
			logger.info("LLM response received. payload={}", response);
			logger.debug("LLM response metadata={}", chatResponse.getMetadata());
			if (response == null || response.isBlank()) {
				throw new IllegalStateException("The AI interpreter returned an empty response");
			}
			return this.outputConverter.convert(response).toAppointmentInterpretation();
		});
		try {
			return call.get(this.timeout.toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException ex) {
			call.cancel(true);
			logger.warn("LLM request timed out after {}", this.timeout);
			throw new IllegalStateException("The AI interpreter timed out", ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			logger.warn("LLM request was interrupted", ex);
			throw new IllegalStateException("The AI interpreter was interrupted", ex);
		}
		catch (ExecutionException ex) {
			logger.error("LLM request failed", ex.getCause());
			throw new IllegalStateException("The AI interpreter failed", ex.getCause());
		}
	}

	private String requestPayload(Prompt prompt, OllamaChatOptions options) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("model", options.getModel());
		payload.put("messages", prompt.getInstructions().stream().map(message -> {
			Map<String, Object> outgoingMessage = new LinkedHashMap<>();
			outgoingMessage.put("role", message.getMessageType().getValue());
			if (message.getText() != null) {
				outgoingMessage.put("content", message.getText());
			}
			return outgoingMessage;
		}).toList());
		payload.put("stream", false);
		if (options.getFormat() != null) {
			payload.put("format", options.getFormat());
		}
		if (options.getKeepAlive() != null) {
			payload.put("keep_alive", options.getKeepAlive());
		}
		payload.put("tools", List.of());
		payload.put("options", OllamaChatOptions.filterNonSupportedFields(options.toMap()));
		if (options.getThinkOption() != null) {
			payload.put("think", options.getThinkOption());
		}
		return this.jsonHelper.toJson(payload);
	}

	@PreDestroy
	void closeExecutor() {
		this.executor.shutdownNow();
	}

}
