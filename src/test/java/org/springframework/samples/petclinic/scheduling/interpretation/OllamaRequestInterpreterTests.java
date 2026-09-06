/*
 * Copyright 2012-2026 the original author or authors.
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

package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class OllamaRequestInterpreterTests {

	@Test
	void mapsLocalWallClockTimes() {
		OllamaRequestInterpreter.ModelAvailabilityWindow modelWindow = new OllamaRequestInterpreter.ModelAvailabilityWindow(
				WindowKind.PREFERRED, null, null, null, DayOfWeek.THURSDAY, "12:00:00", "17:00:00", "AFTERNOON");

		InterpretationResult result = interpreterReturning(modelWindow).interpret("Runny nose", "Thursday after lunch");

		assertThat(result.windows()).containsExactly(new AvailabilityWindow(WindowKind.PREFERRED, null, null, null,
				DayOfWeek.THURSDAY, LocalTime.NOON, LocalTime.of(17, 0), "AFTERNOON"));
	}

	@Test
	void mapsOffsetTimesFromLiveResponseToClinicWallClockTimes() {
		String liveResponse = """
				{
				  "cannotInterpret": false,
				  "careType": "GENERAL",
				  "estimatedMinutes": 0,
				  "preferredVetId": 1,
				  "reasonSummary": "Leo has a runny nose",
				  "specialty": "",
				  "windows": [{
				    "dateVal": "2026-09-12",
				    "dayOfWeek": "THURSDAY",
				    "endDate": "2026-10-06",
				    "endTime": "17:00:00+02:00",
				    "kind": "ALLOWED",
				    "startDate": "2026-09-12",
				    "startTime": "12:00:00+02:00",
				    "tokens": "AFTERNOON"
				  }]
				}
				""";
		OllamaRequestInterpreter.ModelOutput output = new BeanOutputConverter<>(
				OllamaRequestInterpreter.ModelOutput.class)
			.convert(liveResponse);
		OllamaRequestInterpreter interpreter = new OllamaRequestInterpreter(
				(reason, availability) -> new OllamaRequestInterpreter.ModelExchange(output, liveResponse),
				"test-model");

		InterpretationResult result = interpreter.interpret("Leo has a runny nose", "Thursday after lunch");

		assertThat(result.estimatedMinutes()).isZero();
		assertThat(result.preferredVetId()).isEqualTo(1);
		assertThat(result.windows()).containsExactly(new AvailabilityWindow(WindowKind.ALLOWED, null, null, null,
				DayOfWeek.THURSDAY, LocalTime.NOON, LocalTime.of(17, 0), "AFTERNOON"));
	}

	@Test
	void schemaDoesNotForceTheModelToInventOptionalValues() {
		String schema = new BeanOutputConverter<>(OllamaRequestInterpreter.ModelOutput.class).getJsonSchema();

		assertThat(schema).contains("\"required\" : [ \"kind\" ]");
		assertThat(schema)
			.contains("\"required\" : [ \"cannotInterpret\", \"careType\", \"reasonSummary\", \"windows\" ]");
	}

	@Test
	void mapsHallucinatedDateValBackToDayOfWeekWhenWeekdayRequested() {
		OllamaRequestInterpreter.ModelAvailabilityWindow modelWindow = new OllamaRequestInterpreter.ModelAvailabilityWindow(
				WindowKind.ALLOWED, LocalDate.of(2026, 9, 11), null, null, null, "12:00:00", null, "AFTERNOON");

		InterpretationResult result = interpreterReturning(modelWindow).interpret("Leo has a runny nose",
				"Thursday afternoon");

		assertThat(result.windows()).containsExactly(new AvailabilityWindow(WindowKind.ALLOWED, null, null, null,
				DayOfWeek.THURSDAY, LocalTime.NOON, null, "AFTERNOON"));
	}

	@Test
	void invalidTimeStillRetriesThenReportsMalformedResponse() {
		AtomicInteger calls = new AtomicInteger();
		OllamaRequestInterpreter.ModelAvailabilityWindow modelWindow = new OllamaRequestInterpreter.ModelAvailabilityWindow(
				WindowKind.PREFERRED, null, null, null, DayOfWeek.THURSDAY, "after lunch", "17:00:00", "AFTERNOON");
		OllamaRequestInterpreter interpreter = new OllamaRequestInterpreter((reason, availability) -> {
			calls.incrementAndGet();
			return exchange(modelWindow);
		}, "test-model");

		ModelUnavailableException failure = catchThrowableOfType(ModelUnavailableException.class,
				() -> interpreter.interpret("Runny nose", "Thursday after lunch"));

		assertThat(calls).hasValue(2);
		assertThat(failure).hasMessage("malformed model response after retry");
	}

	private static OllamaRequestInterpreter interpreterReturning(
			OllamaRequestInterpreter.ModelAvailabilityWindow modelWindow) {
		return new OllamaRequestInterpreter((reason, availability) -> exchange(modelWindow), "test-model");
	}

	private static OllamaRequestInterpreter.ModelExchange exchange(
			OllamaRequestInterpreter.ModelAvailabilityWindow modelWindow) {
		OllamaRequestInterpreter.ModelOutput output = new OllamaRequestInterpreter.ModelOutput("Runny nose", 30,
				CareType.GENERAL, null, null, false, List.of(modelWindow));
		return new OllamaRequestInterpreter.ModelExchange(output, "{}");
	}

}
