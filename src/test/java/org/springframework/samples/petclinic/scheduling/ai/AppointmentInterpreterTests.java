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

package org.springframework.samples.petclinic.scheduling.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

class AppointmentInterpreterTests {

	private ChatModel chatModel;

	private AppointmentInterpreter interpreter;

	@BeforeEach
	void setUp() {
		this.chatModel = mock(ChatModel.class);
		ChatOptions options = ChatOptions.builder().build();
		when(this.chatModel.getOptions()).thenReturn(options);
		ChatClient.Builder builder = ChatClient.builder(this.chatModel);
		this.interpreter = new AppointmentInterpreter(builder, this.chatModel);
	}

	@Test
	void shouldParseValidStructuredOutput() {
		String json = """
				{
				  "summary": "Dental cleaning for dog",
				  "visitDurationMinutes": 45,
				  "careType": "SPECIALTY",
				  "requiredSpecialty": "dentistry",
				  "urgency": "ROUTINE",
				  "preferredWindows": [
				    { "dayOfWeek": "MONDAY", "date": null, "partOfDay": "MORNING" }
				  ],
				  "allowedWindows": [],
				  "excludedWindows": [],
				  "preferredVetName": null,
				  "confidence": 0.95
				}
				""";

		AssistantMessage assistantMessage = new AssistantMessage(json);
		Generation generation = new Generation(assistantMessage);
		ChatResponse chatResponse = new ChatResponse(List.of(generation));
		when(this.chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

		Optional<Interpretation> result = this.interpreter
			.interpret("My dog needs a dental cleaning next Monday morning");

		assertThat(result).isPresent();
		Interpretation interpretation = result.get();
		assertThat(interpretation.summary()).isEqualTo("Dental cleaning for dog");
		assertThat(interpretation.visitDurationMinutes()).isEqualTo(45);
		assertThat(interpretation.careType()).isEqualTo(CareType.SPECIALTY);
		assertThat(interpretation.requiredSpecialty()).isEqualTo("dentistry");
		assertThat(interpretation.urgency()).isEqualTo(UrgencyLevel.ROUTINE);
		assertThat(interpretation.preferredWindows()).hasSize(1);
		assertThat(interpretation.preferredWindows().get(0).dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
		assertThat(interpretation.preferredWindows().get(0).partOfDay()).isEqualTo("MORNING");
		assertThat(interpretation.confidence()).isEqualTo(0.95);
		assertThat(interpretation.isLowConfidence()).isFalse();
	}

	@Test
	void shouldDetectLowConfidence() {
		Interpretation lowConf = new Interpretation("Vague checkup", 15, CareType.GENERAL, null, UrgencyLevel.ROUTINE,
				List.of(), List.of(), List.of(), null, 0.4);
		assertThat(lowConf.isLowConfidence()).isTrue();

		Interpretation blankSummary = new Interpretation("", 15, CareType.GENERAL, null, UrgencyLevel.ROUTINE,
				List.of(), List.of(), List.of(), null, 0.9);
		assertThat(blankSummary.isLowConfidence()).isTrue();
	}

	@Test
	void shouldToleratePlaceholderOrInvalidDateInsteadOfFailing() {
		// The model sometimes ignores the schema and returns a literal placeholder for
		// the
		// date field. This must not abort the whole interpretation (which previously
		// routed
		// the request to the staff queue); the bad date is dropped to null instead.
		String json = """
				{
				  "summary": "Vaccination for cat",
				  "visitDurationMinutes": 30,
				  "careType": "GENERAL",
				  "requiredSpecialty": null,
				  "urgency": "ROUTINE",
				  "preferredWindows": [
				    { "dayOfWeek": "TUESDAY", "date": "YYYY-MM-DD", "partOfDay": "MORNING" }
				  ],
				  "allowedWindows": [],
				  "excludedWindows": [],
				  "preferredVetName": null,
				  "confidence": 0.9
				}
				""";

		AssistantMessage assistantMessage = new AssistantMessage(json);
		Generation generation = new Generation(assistantMessage);
		ChatResponse chatResponse = new ChatResponse(List.of(generation));
		when(this.chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

		Optional<Interpretation> result = this.interpreter.interpret("Vaccination for my cat next Tuesday morning");

		assertThat(result).isPresent();
		Interpretation interpretation = result.get();
		assertThat(interpretation.summary()).isEqualTo("Vaccination for cat");
		assertThat(interpretation.preferredWindows()).hasSize(1);
		assertThat(interpretation.preferredWindows().get(0).dayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
		assertThat(interpretation.preferredWindows().get(0).date()).isNull();
		assertThat(interpretation.preferredWindows().get(0).partOfDay()).isEqualTo("MORNING");
	}

	@Test
	void shouldHandleEmergencyInterpretation() {
		String json = """
				{
				  "summary": "Dog collapsed and is unresponsive",
				  "visitDurationMinutes": 60,
				  "careType": "GENERAL",
				  "requiredSpecialty": null,
				  "urgency": "EMERGENCY",
				  "preferredWindows": [],
				  "allowedWindows": [],
				  "excludedWindows": [],
				  "preferredVetName": null,
				  "confidence": 0.99
				}
				""";

		AssistantMessage assistantMessage = new AssistantMessage(json);
		Generation generation = new Generation(assistantMessage);
		ChatResponse chatResponse = new ChatResponse(List.of(generation));
		when(this.chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

		Optional<Interpretation> result = this.interpreter.interpret("My dog collapsed and is unresponsive!");

		assertThat(result).isPresent();
		assertThat(result.get().urgency()).isEqualTo(UrgencyLevel.EMERGENCY);
	}

	@Test
	void shouldHandleUnavailableModelGracefully() {
		AppointmentInterpreter unavailable = new AppointmentInterpreter(null, null);
		assertThat(unavailable.isAvailable()).isFalse();

		Optional<Interpretation> result = unavailable.interpret("Need vaccination");
		assertThat(result).isEmpty();
	}

	@Test
	void shouldHandleEmptyOrNullInput() {
		Optional<Interpretation> nullResult = this.interpreter.interpret(null);
		assertThat(nullResult).isEmpty();

		Optional<Interpretation> blankResult = this.interpreter.interpret("   ");
		assertThat(blankResult).isEmpty();
	}

	@Test
	void shouldGracefullyDegradeOnException() {
		when(this.chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("Ollama connection refused"));

		Optional<Interpretation> result = this.interpreter.interpret("Need a checkup");
		assertThat(result).isEmpty();
	}

}
