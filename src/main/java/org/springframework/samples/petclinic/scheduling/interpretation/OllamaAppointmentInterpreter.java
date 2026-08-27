/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

@Component
public class OllamaAppointmentInterpreter implements AppointmentInterpreter {

	private static final String SYSTEM_PROMPT = """
			Interpret an English veterinary appointment request. Return only the requested structured data.
			A window must contain a dayOfWeek and either a clinic dayPart (morning, afternoon, evening)
			or an explicit local start/end range. Do not invent a veterinarian id or specialty.
			Urgency is advisory only and must be ROUTINE, SOON, URGENT, or SUSPECTED_EMERGENCY.
			""";

	private final OllamaChatModel chatModel;

	private final Duration timeout;

	private final BeanOutputConverter<AppointmentInterpretation> outputConverter = new BeanOutputConverter<>(
			AppointmentInterpretation.class);

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
			String response = this.chatModel.call(prompt).getResult().getOutput().getText();
			if (response == null || response.isBlank()) {
				throw new IllegalStateException("The AI interpreter returned an empty response");
			}
			return this.outputConverter.convert(response);
		});
		try {
			return call.get(this.timeout.toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException ex) {
			call.cancel(true);
			throw new IllegalStateException("The AI interpreter timed out", ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("The AI interpreter was interrupted", ex);
		}
		catch (ExecutionException ex) {
			throw new IllegalStateException("The AI interpreter failed", ex.getCause());
		}
	}

	@PreDestroy
	void closeExecutor() {
		this.executor.shutdownNow();
	}

}
