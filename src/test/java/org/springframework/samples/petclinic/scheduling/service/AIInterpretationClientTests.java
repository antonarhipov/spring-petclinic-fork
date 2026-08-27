/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.scheduling.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.samples.petclinic.scheduling.dto.AIInterpretationResponseDto;
import org.springframework.samples.petclinic.scheduling.model.ClinicSettings;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class AIInterpretationClientTests {

	private AIInterpretationClient client;

	private ClinicSettings settings;

	private SchedulingTimeService timeService;

	@BeforeEach
	void setUp() {
		timeService = new SchedulingTimeService(Clock.fixed(Instant.parse("2026-08-27T10:00:00Z"), ZoneId.of("UTC")),
				ZoneId.of("UTC"));
		client = new AIInterpretationClient(null, new ObjectMapper(), timeService);
		settings = new ClinicSettings();
	}

	@Test
	void testParseResponseValidJson() {
		String json = """
				{
				  "summary": "Annual dog wellness exam",
				  "careType": "GENERAL",
				  "specialtyName": null,
				  "preferredVetName": "Helen Leary",
				  "durationMinutes": 45,
				  "urgent": false,
				  "windows": [
				    {
				      "kind": "PREFERRED",
				      "dateExpression": "ANY_DATE",
				      "dayOfWeek": "MONDAY",
				      "startTime": null,
				      "endTime": null,
				      "namedPeriod": "MORNING"
				    }
				  ],
				  "issues": ["INCOMPLETE_AVAILABILITY"]
				}
				""";

		AIInterpretationResponseDto dto = client.parseResponse(json, settings, List.of("Helen Leary"),
				List.of("surgery"));

		assertThat(dto.summary()).isEqualTo("Annual dog wellness exam");
		assertThat(dto.careType()).isEqualTo("GENERAL");
		assertThat(dto.preferredVetName()).isEqualTo("Helen Leary");
		assertThat(dto.durationMinutes()).isEqualTo(45);
		assertThat(dto.urgent()).isFalse();
		assertThat(dto.windows()).hasSize(1);
		assertThat(dto.windows().get(0).kind()).isEqualTo("PREFERRED");
		assertThat(dto.windows().get(0).dayOfWeek()).isEqualTo("MONDAY");
		assertThat(dto.issues()).containsExactly("INCOMPLETE_AVAILABILITY");
	}

	@Test
	void testParseResponseInMarkdownBlock() {
		String response = """
				```json
				{
				  "summary": "Dental cleaning for cat",
				  "careType": "SPECIALTY",
				  "specialtyName": "dentistry",
				  "preferredVetName": null,
				  "durationMinutes": 60,
				  "urgent": false,
				  "windows": [],
				  "issues": []
				}
				```
				""";

		AIInterpretationResponseDto dto = client.parseResponse(response, settings, List.of(), List.of("dentistry"));

		assertThat(dto.summary()).isEqualTo("Dental cleaning for cat");
		assertThat(dto.careType()).isEqualTo("SPECIALTY");
		assertThat(dto.specialtyName()).isEqualTo("dentistry");
		assertThat(dto.durationMinutes()).isEqualTo(60);
		assertThat(dto.urgent()).isFalse();
	}

	@Test
	void testParseResponseInvalidJsonReturnsInvalidAiOutputIssue() {
		String invalidJson = "Not valid json at all";

		AIInterpretationResponseDto dto = client.parseResponse(invalidJson, settings, List.of(), List.of());

		assertThat(dto.issues()).contains("INVALID_AI_OUTPUT");
	}

	@Test
	void testParseResponseUnknownIssueCodeReturnsInvalidAiOutput() {
		String json = """
				{
				  "summary": "Checkup",
				  "careType": "GENERAL",
				  "durationMinutes": 30,
				  "urgent": false,
				  "issues": ["SOME_UNKNOWN_CODE"]
				}
				""";

		AIInterpretationResponseDto dto = client.parseResponse(json, settings, List.of(), List.of());

		assertThat(dto.issues()).contains("INVALID_AI_OUTPUT");
	}

	@Test
	void testParseResponseRejectsUnknownProperties() {
		String json = """
				{
				  "summary": "Checkup",
				  "careType": "GENERAL",
				  "durationMinutes": 30,
				  "urgent": false,
				  "windows": [],
				  "issues": [],
				  "diagnosis": "conjunctivitis"
				}
				""";

		AIInterpretationResponseDto dto = client.parseResponse(json, settings, List.of(), List.of());

		assertThat(dto.issues()).containsExactly("INVALID_AI_OUTPUT");
	}

	@Test
	void parsesBoundedMonthAndOrdinalWeekExpressions() {
		String json = """
				{"summary":"Calendar ranges","careType":"GENERAL","urgent":false,
				 "windows":[
				  {"kind":"ALLOWED","dateExpression":"RELATIVE_MONTH","monthOffset":1,"ordinalWeek":"SECOND"},
				  {"kind":"PREFERRED","dateExpression":"NAMED_MONTH","month":"DECEMBER","ordinalWeek":"LAST"},
				  {"kind":"EXCLUDED","dateExpression":"END_OF_WEEK","weekOffset":0},
				  {"kind":"ALLOWED","dateExpression":"EXPLICIT_DATE","explicitDate":"2026-09-03"}
				 ],"issues":[]}
				""";

		AIInterpretationResponseDto dto = client.parseResponse(json, settings, List.of(), List.of());

		assertThat(dto.issues()).isEmpty();
		assertThat(dto.windows()).extracting(AIInterpretationResponseDto.TimeWindowDto::dateExpression)
			.containsExactly("RELATIVE_MONTH", "NAMED_MONTH", "END_OF_WEEK", "EXPLICIT_DATE");
		assertThat(dto.windows().get(0).monthOffset()).isEqualTo(1);
		assertThat(dto.windows().get(0).ordinalWeek()).isEqualTo("SECOND");
	}

	@Test
	void rejectsLegacyOrIncompleteWindowShapes() {
		String legacy = """
				{"summary":"Legacy","careType":"GENERAL","urgent":false,
				 "windows":[{"dayOfWeek":"THURSDAY","namedPeriod":"AFTERNOON","isExcluded":false}],
				 "issues":[]}
				""";
		String missingOffset = """
				{"summary":"Incomplete","careType":"GENERAL","urgent":false,
				 "windows":[{"kind":"ALLOWED","dateExpression":"RELATIVE_WEEK","dayOfWeek":"THURSDAY"}],
				 "issues":[]}
				""";

		assertThat(client.parseResponse(legacy, settings, List.of(), List.of()).issues())
			.containsExactly("INVALID_AI_OUTPUT");
		assertThat(client.parseResponse(missingOffset, settings, List.of(), List.of()).issues())
			.containsExactly("INVALID_AI_OUTPUT");
	}

	@Test
	void interpretRequestsNativeStructuredOutputFromOllama(CapturedOutput output) {
		String response = """
				{"summary":"Watery eyes","careType":"GENERAL","specialtyName":null,
				 "preferredVetName":null,"durationMinutes":30,"urgent":false,
				 "windows":[{"kind":"ALLOWED","dateExpression":"RELATIVE_WEEK","weekOffset":1,
				 "dayOfWeek":"THURSDAY","startTime":null,"endTime":null,
				 "namedPeriod":"AFTERNOON"}],"issues":[]}
				""";
		AtomicReference<Prompt> submittedPrompt = new AtomicReference<>();
		ChatModel chatModel = new ChatModel() {
			@Override
			public ChatResponse call(Prompt prompt) {
				submittedPrompt.set(prompt);
				return new ChatResponse(List.of(new Generation(new AssistantMessage(response))));
			}

			@Override
			public OllamaChatOptions getOptions() {
				return OllamaChatOptions.builder().model("gemma4:latest").temperature(0.8).build();
			}
		};
		AIInterpretationClient structuredClient = new AIInterpretationClient(chatModel, new ObjectMapper(),
				timeService);

		AIInterpretationResponseDto result = structuredClient.interpret(
				"Leo has running eyes. Next Thursday, after lunch, would be a good time to come for a visit", "cat",
				settings, List.of("Helen Leary"), List.of("surgery"));

		assertThat(submittedPrompt.get().getOptions()).isInstanceOf(OllamaChatOptions.class);
		OllamaChatOptions options = (OllamaChatOptions) submittedPrompt.get().getOptions();
		assertThat(options.getModel()).isEqualTo("gemma4:latest");
		assertThat(options.getTemperature()).isZero();
		assertThat(options.getFormat()).isInstanceOf(java.util.Map.class);
		assertThat(new ObjectMapper().valueToTree(options.getFormat()).path("additionalProperties").asBoolean())
			.isFalse();
		assertThat(result.issues()).isEmpty();
		assertThat(result.windows()).singleElement().satisfies(window -> {
			assertThat(window.kind()).isEqualTo("ALLOWED");
			assertThat(window.dateExpression()).isEqualTo("RELATIVE_WEEK");
			assertThat(window.weekOffset()).isEqualTo(1);
			assertThat(window.dayOfWeek()).isEqualTo("THURSDAY");
			assertThat(window.namedPeriod()).isEqualTo("AFTERNOON");
		});
		assertThat(output).contains("Current clinic date: 2026-08-27")
			.contains("Clinic time zone: UTC")
			.contains("Pet owner text: \"Leo has running eyes.")
			.contains("Received AI interpretation response from model gemma4:latest:")
			.contains("{\"summary\":\"Watery eyes\"");
	}

	@Test
	void ordinarySymptomDoesNotCreateContradictoryRoutingFallback() {
		String response = """
				{"summary":"Running nose","careType":"GENERAL","specialtyName":null,
				 "preferredVetName":null,"durationMinutes":30,"urgent":false,
				 "windows":[{"kind":"ALLOWED","dateExpression":"RELATIVE_WEEK","weekOffset":1,
				 "dayOfWeek":"THURSDAY","startTime":null,"endTime":null,"namedPeriod":null}],
				 "issues":["CONTRADICTORY_CLINICAL_ROUTING"]}
				""";
		ChatModel chatModel = new ChatModel() {
			@Override
			public ChatResponse call(Prompt prompt) {
				return new ChatResponse(List.of(new Generation(new AssistantMessage(response))));
			}

			@Override
			public OllamaChatOptions getOptions() {
				return OllamaChatOptions.builder().model("gemma4:latest").build();
			}
		};
		AIInterpretationClient structuredClient = new AIInterpretationClient(chatModel, new ObjectMapper(),
				timeService);

		AIInterpretationResponseDto result = structuredClient.interpret(
				"Leo has running nose\nNext Thursday for the visit", "cat", settings, List.of("Helen Leary"),
				List.of("radiology", "surgery", "dentistry"));

		assertThat(result.careType()).isEqualTo("GENERAL");
		assertThat(result.issues()).isEmpty();
		assertThat(result.windows()).singleElement().satisfies(window -> {
			assertThat(window.dayOfWeek()).isEqualTo("THURSDAY");
			assertThat(window.namedPeriod()).isNull();
		});
	}

	@Test
	void testFallbackInterpretationExtractsUrgencyAndVets() {
		String text = "My cat is bleeding and needs urgent care with Dr. Helen Leary!";

		AIInterpretationResponseDto dto = client.fallbackInterpretation(text, settings, List.of("Helen Leary"),
				List.of("surgery"));

		assertThat(dto.urgent()).isTrue();
		assertThat(dto.preferredVetName()).isEqualTo("Helen Leary");
		assertThat(dto.careType()).isEqualTo("GENERAL");
	}

}
