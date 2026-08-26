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

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AppointmentInterpreter {

	private static final Logger log = LoggerFactory.getLogger(AppointmentInterpreter.class);

	private static final String SYSTEM_PROMPT = """
			You are an expert veterinary assistant for Spring PetClinic.
			Your job is to analyze the owner's free-text request and extract structured scheduling details.

			Extract:
			1. summary: A concise summary of why the pet needs an appointment.
			2. visitDurationMinutes: Estimated duration in minutes if mentioned or implied (e.g. 15, 30, 45, 60), or null if unspecified.
			3. careType: GENERAL or SPECIALTY.
			4. requiredSpecialty: Name of specialty if careType is SPECIALTY (e.g. "radiology", "surgery", "dentistry"), otherwise null.
			5. urgency: ROUTINE, URGENT, or EMERGENCY. Mark EMERGENCY only if life-threatening symptoms are described (e.g. collapse, severe bleeding, difficulty breathing).
			6. preferredWindows: List of preferred time windows (dayOfWeek, date, partOfDay like MORNING, AFTERNOON, EVENING).
			7. allowedWindows: List of acceptable alternative windows.
			8. excludedWindows: List of times/days the owner explicitly cannot make.
			9. preferredVetName: Name of veterinarian if specifically requested, or null.
			10. confidence: A score from 0.0 to 1.0 representing your confidence in this extraction.

			Output rules (follow strictly):
			- Return ONLY the fields defined by the provided schema. Do not invent fields or wrap the result.
			- Use null for any value you are unsure about. Never emit placeholder text.
			- dayOfWeek must be one of the uppercase names MONDAY..SUNDAY, or null.
			- date must be an actual calendar date in strict ISO-8601 format yyyy-MM-dd (e.g. 2026-08-31).
			  Resolve relative expressions ("next Monday", "tomorrow") against the CURRENT DATE given below.
			  If no concrete date is stated or can be resolved, set date to null.
			  Never output a literal template such as "YYYY-MM-DD" and never guess a date.
			- partOfDay must be one of MORNING, AFTERNOON, EVENING, or null.
			""";

	private final ChatClient.Builder chatClientBuilder;

	private final ChatModel chatModel;

	public AppointmentInterpreter(@Autowired(required = false) ChatClient.Builder chatClientBuilder,
			@Autowired(required = false) ChatModel chatModel) {
		this.chatClientBuilder = chatClientBuilder;
		this.chatModel = chatModel;
	}

	public Optional<Interpretation> interpret(String rawText) {
		if (rawText == null || rawText.isBlank()) {
			return Optional.empty();
		}

		if (this.chatClientBuilder == null && this.chatModel == null) {
			log.info("No ChatModel or ChatClient configured; skipping AI interpretation.");
			return Optional.empty();
		}

		try {
			ChatClient client = (this.chatClientBuilder != null) ? this.chatClientBuilder.build()
					: ChatClient.builder(this.chatModel).build();

			Interpretation interpretation = client.prompt()
				.system(SYSTEM_PROMPT)
				.user(u -> u
					.text("CURRENT DATE: {today} (use this to resolve any relative dates as strict yyyy-MM-dd).\n"
							+ "Please extract appointment scheduling details from this owner request: {text}")
					.param("today", java.time.LocalDate.now().toString())
					.param("text", rawText))
				.call()
				.entity(Interpretation.class);

			// Log the full structured interpretation returned by the LLM so it is
			// possible
			// to tell whether a wrong slot originates from a bad LLM extraction or from
			// later processing. Enable via:
			// logging.level.org.springframework.samples.petclinic.scheduling.ai.AppointmentInterpreter=DEBUG
			if (interpretation != null) {
				log.debug("LLM interpretation for request text [{}]: {}", rawText, interpretation);
				log.debug("LLM interpreted time windows - preferred={}, allowed={}, excluded={}",
						interpretation.preferredWindows(), interpretation.allowedWindows(),
						interpretation.excludedWindows());
			}

			return Optional.ofNullable(interpretation);
		}
		catch (Exception ex) {
			log.warn("AI interpretation failed for input: {}", ex.getMessage());
			return Optional.empty();
		}
	}

	public boolean isAvailable() {
		return this.chatClientBuilder != null || this.chatModel != null;
	}

}
