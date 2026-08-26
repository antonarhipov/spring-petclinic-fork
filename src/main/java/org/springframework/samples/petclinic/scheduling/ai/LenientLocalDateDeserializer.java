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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Lenient {@link LocalDate} deserializer for AI-produced JSON.
 *
 * <p>
 * A language model does not reliably honour the response schema: for a date field it
 * occasionally emits a literal placeholder ({@code "YYYY-MM-DD"}), a natural-language
 * value ({@code "next Monday"}), or some other non-ISO string. The default strict
 * {@code LocalDate} binding throws a {@code DateTimeParseException} on such input and,
 * because the date lives deep inside the {@link Interpretation} object graph
 * ({@code preferredWindows[i].date}), that single bad value aborts binding of the
 * <em>entire</em> interpretation - which then routes an otherwise usable request to the
 * staff queue.
 *
 * <p>
 * Treating an unparseable (or blank) value as "no date given" ({@code null}) keeps the
 * rest of the still-useful interpretation intact. Only strict ISO-8601 local dates
 * ({@code yyyy-MM-dd}) are accepted; anything else is discarded rather than guessed.
 */
public class LenientLocalDateDeserializer extends ValueDeserializer<LocalDate> {

	private static final Logger log = LoggerFactory.getLogger(LenientLocalDateDeserializer.class);

	@Override
	public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
		String value = p.getValueAsString();
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		try {
			return LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE);
		}
		catch (Exception ex) {
			log.warn("Ignoring unparseable date value '{}' from AI interpretation; treating as no date", trimmed);
			return null;
		}
	}

}
