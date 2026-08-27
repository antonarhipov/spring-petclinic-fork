/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OllamaAppointmentInterpreterTests {

	@Mock
	private OllamaChatModel chatModel;

	@Mock
	private ChatResponse chatResponse;

	@Mock
	private Generation generation;

	@Mock
	private AssistantMessage assistantMessage;

	@Test
	void mutatesDefaultsSoTheConfiguredModelSurvivesStructuredOutputOptions() {
		given(this.chatModel.getOptions()).willReturn(OllamaChatOptions.builder().model("gemma4:latest").build());
		given(this.chatModel.call(any(Prompt.class))).willReturn(this.chatResponse);
		given(this.chatResponse.getResult()).willReturn(this.generation);
		given(this.generation.getOutput()).willReturn(this.assistantMessage);
		given(this.assistantMessage.getText()).willReturn("""
				{"careType":"check-up","requiredSpecialty":null,"estimatedDurationMin":30,
				 "preferred":[],"allowed":[],"excluded":[],"preferredVetId":null,"urgency":"ROUTINE"}
				""");
		OllamaAppointmentInterpreter interpreter = new OllamaAppointmentInterpreter(this.chatModel,
				Duration.ofSeconds(1));

		AppointmentInterpretation result = interpreter.interpret("check-up please");

		assertThat(result.careType()).isEqualTo("check-up");
		ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
		verify(this.chatModel).call(prompt.capture());
		OllamaChatOptions options = (OllamaChatOptions) prompt.getValue().getOptions();
		assertThat(options.getModel()).isEqualTo("gemma4:latest");
		assertThat(options.getTemperature()).isZero();
		assertThat(options.getFormat()).isInstanceOf(java.util.Map.class);
		interpreter.closeExecutor();
	}

}
