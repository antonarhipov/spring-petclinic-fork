/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

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
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith({ MockitoExtension.class, OutputCaptureExtension.class })
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
	void logsTheEffectivePayloadAndResponseAndRequiresStructuredOutput(CapturedOutput output) {
		given(this.chatModel.getOptions()).willReturn(OllamaChatOptions.builder().model("gemma4:latest").build());
		givenResponse("""
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
		assertThat(options.getFormat()).isInstanceOfSatisfying(Map.class, format -> {
			assertThat(format).containsEntry("type", "object");
			assertThat(format).containsKey("properties");
		});
		Map<String, Object> format = asMap(options.getFormat());
		assertThat(asStringList(format.get("required"))).doesNotContain("requiredSpecialty", "estimatedDurationMin",
				"preferredVetId");
		Map<String, Object> windowSchema = asMap(asMap(format.get("$defs")).values().iterator().next());
		assertThat(asStringList(windowSchema.get("required"))).containsExactly("dayOfWeek");
		Map<String, Object> windowProperties = asMap(windowSchema.get("properties"));
		assertThat(windowProperties).doesNotContainKey("zoneId");
		assertThat(asMap(windowProperties.get("start"))).containsEntry("pattern",
				OllamaInterpretationWindow.LOCAL_TIME_PATTERN);
		assertThat(output).contains("LLM request started. payload=")
			.contains("\"model\":\"gemma4:latest\"")
			.contains("\"role\":\"system\"")
			.contains("\"role\":\"user\",\"content\":\"check-up please\"")
			.contains("\"format\":{")
			.contains("\"temperature\":0.0")
			.contains("LLM response received. payload=")
			.contains("\"careType\":\"check-up\"");
		interpreter.closeExecutor();
	}

	@Test
	void ignoresProviderTimeOffsetsWhenADayPartAlreadyDefinesTheWindow() {
		given(this.chatModel.getOptions()).willReturn(OllamaChatOptions.builder().model("ministral-3:14b").build());
		givenResponse("""
				{
				  "allowed": [],
				  "careType": "VETERINARY",
				  "estimatedDurationMin": 30,
				  "excluded": [],
				  "preferred": [{
				    "dayOfWeek": "FRIDAY",
				    "dayPart": "morning",
				    "end": "12:00:00Z",
				    "start": "09:00:00Z",
				    "zoneId": "UTC"
				  }],
				  "preferredVetId": 0,
				  "requiredSpecialty": "",
				  "urgency": "SOON"
				}
				""");
		OllamaAppointmentInterpreter interpreter = new OllamaAppointmentInterpreter(this.chatModel,
				Duration.ofSeconds(1));

		AppointmentInterpretation result = interpreter.interpret("Please book an appointment for next Friday");

		assertThat(result.requiredSpecialty()).isNull();
		assertThat(result.preferredVetId()).isNull();
		assertThat(result.preferred()).singleElement().satisfies(window -> {
			assertThat(window.dayPart()).isEqualTo("morning");
			assertThat(window.start()).isNull();
			assertThat(window.end()).isNull();
			assertThat(window.zoneId()).isNull();
		});
		interpreter.closeExecutor();
	}

	@Test
	void defensivelyAcceptsAnOffsetSuffixForAnExplicitLocalTimeRange() {
		given(this.chatModel.getOptions()).willReturn(OllamaChatOptions.builder().model("ministral-3:14b").build());
		givenResponse("""
				{
				  "allowed": [],
				  "careType": "VETERINARY",
				  "excluded": [],
				  "preferred": [{
				    "dayOfWeek": "FRIDAY",
				    "end": "12:00:00Z",
				    "start": "09:00:00Z"
				  }],
				  "urgency": "SOON"
				}
				""");
		OllamaAppointmentInterpreter interpreter = new OllamaAppointmentInterpreter(this.chatModel,
				Duration.ofSeconds(1));

		AppointmentInterpretation result = interpreter.interpret("Friday between 9 and 12");

		assertThat(result.preferred()).singleElement().satisfies(window -> {
			assertThat(window.start()).isEqualTo(LocalTime.of(9, 0));
			assertThat(window.end()).isEqualTo(LocalTime.of(12, 0));
		});
		interpreter.closeExecutor();
	}

	private void givenResponse(String response) {
		given(this.chatModel.call(any(Prompt.class))).willReturn(this.chatResponse);
		given(this.chatResponse.getResult()).willReturn(this.generation);
		given(this.generation.getOutput()).willReturn(this.assistantMessage);
		given(this.assistantMessage.getText()).willReturn(response);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> asMap(Object value) {
		return (Map<String, Object>) value;
	}

	@SuppressWarnings("unchecked")
	private static List<String> asStringList(Object value) {
		return (List<String>) value;
	}

}
