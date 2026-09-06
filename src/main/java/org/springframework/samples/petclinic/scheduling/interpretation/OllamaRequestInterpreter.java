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

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.net.SocketTimeoutException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Spring AI 2.x structured-output adapter; the RULE-17 default provider. */
@Service
@ConditionalOnProperty(name = "scheduling.ai.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaRequestInterpreter implements RequestInterpreter {

	private static final String PROMPT_VERSION = "1.0";

	private static final String SYSTEM_PROMPT = "Extract a veterinary scheduling request. Use only GENERAL or SPECIALTY care types and preserve all availability windows. For each window choose exactly one date selector: dayOfWeek for any weekday or day of the week (e.g. Thursday, next Friday, on weekdays); dateVal for an explicit calendar date (e.g. 2026-09-15 or September 15); or startDate and endDate for a date range; omit the other date selectors. Do not convert weekdays to dateVal; always use dayOfWeek when a weekday or day of the week is requested. Omit optional fields that do not apply, and do not choose preferredVetId unless the request names a veterinarian. Express startTime and endTime as clinic-local wall-clock times in HH:mm:ss format without a timezone or UTC offset.";

	private static final Logger logger = LoggerFactory.getLogger(OllamaRequestInterpreter.class);

	private final ModelCall modelCall;

	private final String modelTag;

	@Autowired
	public OllamaRequestInterpreter(ChatClient schedulingOllamaChatClient,
			@Value("${spring.ai.ollama.chat.model}") String modelTag, InterpretationPromptFactory promptFactory) {
		this((reason, availability) -> callModel(schedulingOllamaChatClient, promptFactory.create(reason, availability),
				modelTag), modelTag);
	}

	OllamaRequestInterpreter(ModelCall modelCall, String modelTag) {
		this.modelCall = modelCall;
		this.modelTag = modelTag;
	}

	@Override
	public InterpretationResult interpret(String reasonText, String availabilityText) {
		try {
			logger.debug("Calling scheduling LLM requestId={} model={} promptVersion={} attempt=1", requestId(),
					this.modelTag, PROMPT_VERSION);
			return interpretOnce(reasonText, availabilityText);
		}
		catch (RuntimeException firstFailure) {
			if (isTransportFailure(firstFailure)) {
				logger.warn("Scheduling LLM transport failure requestId={} model={}; no retry", requestId(),
						this.modelTag, firstFailure);
				throw unavailable(firstFailure);
			}
			logger.warn("Scheduling LLM response failed requestId={} model={}; retrying once", requestId(),
					this.modelTag, firstFailure);
			try {
				logger.debug("Calling scheduling LLM requestId={} model={} promptVersion={} attempt=2", requestId(),
						this.modelTag, PROMPT_VERSION);
				return interpretOnce(reasonText, availabilityText);
			}
			catch (RuntimeException secondFailure) {
				logger.error("Scheduling LLM retry failed requestId={} model={}", requestId(), this.modelTag,
						secondFailure);
				throw unavailable(secondFailure);
			}
		}
	}

	private InterpretationResult interpretOnce(String reasonText, String availabilityText) {
		ModelExchange exchange = this.modelCall.call(reasonText, availabilityText);
		ModelOutput output = exchange.output();
		InterpretationResult result = new InterpretationResult(output.reasonSummary(), output.estimatedMinutes(),
				output.careType(), output.specialty(), output.preferredVetId(), output.cannotInterpret(),
				mapWindows(output.windows(), availabilityText), exchange.rawResponse(), this.modelTag, PROMPT_VERSION);
		logger.debug("Mapped scheduling LLM output requestId={} interpretationResult={}", requestId(), result);
		return result;
	}

	private static ModelExchange callModel(ChatClient chatClient, String prompt, String modelTag) {
		logger.debug("Scheduling LLM request requestId={} model={}\nsystem:\n{}\nuser:\n{}", requestId(), modelTag,
				SYSTEM_PROMPT, prompt);
		ResponseEntity<ChatResponse, ModelOutput> exchange = chatClient.prompt()
			.system(SYSTEM_PROMPT)
			.user(prompt)
			.call()
			.responseEntity(ModelOutput.class, spec -> spec.useProviderStructuredOutput().validateSchema());
		String rawResponse = exchange.response().getResult().getOutput().getText();
		logger.debug("Scheduling LLM response requestId={} model={}\nrawResponse:\n{}\nmappedOutput={}", requestId(),
				modelTag, rawResponse, exchange.entity());
		return new ModelExchange(exchange.entity(), rawResponse);
	}

	private static String requestId() {
		String requestId = MDC.get("schedulingRequestId");
		return requestId != null ? requestId : "unscoped";
	}

	private static List<AvailabilityWindow> mapWindows(List<ModelAvailabilityWindow> windows, String availabilityText) {
		return windows == null ? null : windows.stream().map(window -> window.toDomain(availabilityText)).toList();
	}

	private static LocalTime parseClinicLocalTime(String value) {
		if (value == null) {
			return null;
		}
		try {
			return LocalTime.parse(value, DateTimeFormatter.ISO_LOCAL_TIME);
		}
		catch (DateTimeParseException localTimeFailure) {
			try {
				return OffsetTime.parse(value, DateTimeFormatter.ISO_OFFSET_TIME).toLocalTime();
			}
			catch (DateTimeParseException offsetTimeFailure) {
				offsetTimeFailure.addSuppressed(localTimeFailure);
				throw offsetTimeFailure;
			}
		}
	}

	private static boolean isTransportFailure(Throwable failure) {
		return findCause(failure, IOException.class) != null;
	}

	private static ModelUnavailableException unavailable(RuntimeException failure) {
		String reason;
		if (findCause(failure, HttpTimeoutException.class) != null
				|| findCause(failure, SocketTimeoutException.class) != null) {
			reason = "model timeout";
		}
		else if (isTransportFailure(failure)) {
			reason = "model transport failure";
		}
		else {
			reason = "malformed model response after retry";
		}
		return new ModelUnavailableException(reason, failure);
	}

	private static <T extends Throwable> T findCause(Throwable failure, Class<T> type) {
		for (Throwable current = failure; current != null; current = current.getCause()) {
			if (type.isInstance(current)) {
				return type.cast(current);
			}
		}
		return null;
	}

	@FunctionalInterface
	interface ModelCall {

		ModelExchange call(String reasonText, String availabilityText);

	}

	record ModelExchange(ModelOutput output, String rawResponse) {
	}

	public record ModelOutput(String reasonSummary,
			@JsonProperty(isRequired = OptBoolean.FALSE) Integer estimatedMinutes, CareType careType,
			@JsonProperty(isRequired = OptBoolean.FALSE) String specialty,
			@JsonProperty(isRequired = OptBoolean.FALSE) Integer preferredVetId, boolean cannotInterpret,
			List<ModelAvailabilityWindow> windows) {
	}

	private static final Pattern MONDAY_PATTERN = Pattern.compile("\\b(monday|mon)\\b", Pattern.CASE_INSENSITIVE);

	private static final Pattern TUESDAY_PATTERN = Pattern.compile("\\b(tuesday|tue|tues)\\b",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern WEDNESDAY_PATTERN = Pattern.compile("\\b(wednesday|wed)\\b", Pattern.CASE_INSENSITIVE);

	private static final Pattern THURSDAY_PATTERN = Pattern.compile("\\b(thursday|thu|thur|thurs)\\b",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern FRIDAY_PATTERN = Pattern.compile("\\b(friday|fri)\\b", Pattern.CASE_INSENSITIVE);

	private static final Pattern SATURDAY_PATTERN = Pattern.compile("\\b(saturday|sat)\\b", Pattern.CASE_INSENSITIVE);

	private static final Pattern SUNDAY_PATTERN = Pattern.compile("\\b(sunday|sun)\\b", Pattern.CASE_INSENSITIVE);

	private static DayOfWeek detectDayOfWeek(String text) {
		if (text == null) {
			return null;
		}
		DayOfWeek matched = null;
		if (MONDAY_PATTERN.matcher(text).find()) {
			matched = DayOfWeek.MONDAY;
		}
		if (TUESDAY_PATTERN.matcher(text).find()) {
			if (matched != null) {
				return null;
			}
			matched = DayOfWeek.TUESDAY;
		}
		if (WEDNESDAY_PATTERN.matcher(text).find()) {
			if (matched != null) {
				return null;
			}
			matched = DayOfWeek.WEDNESDAY;
		}
		if (THURSDAY_PATTERN.matcher(text).find()) {
			if (matched != null) {
				return null;
			}
			matched = DayOfWeek.THURSDAY;
		}
		if (FRIDAY_PATTERN.matcher(text).find()) {
			if (matched != null) {
				return null;
			}
			matched = DayOfWeek.FRIDAY;
		}
		if (SATURDAY_PATTERN.matcher(text).find()) {
			if (matched != null) {
				return null;
			}
			matched = DayOfWeek.SATURDAY;
		}
		if (SUNDAY_PATTERN.matcher(text).find()) {
			if (matched != null) {
				return null;
			}
			matched = DayOfWeek.SUNDAY;
		}
		return matched;
	}

	public record ModelAvailabilityWindow(WindowKind kind,
			@JsonProperty(isRequired = OptBoolean.FALSE) LocalDate dateVal,
			@JsonProperty(isRequired = OptBoolean.FALSE) LocalDate startDate,
			@JsonProperty(isRequired = OptBoolean.FALSE) LocalDate endDate,
			@JsonProperty(isRequired = OptBoolean.FALSE) DayOfWeek dayOfWeek,
			@JsonProperty(isRequired = OptBoolean.FALSE) String startTime,
			@JsonProperty(isRequired = OptBoolean.FALSE) String endTime,
			@JsonProperty(isRequired = OptBoolean.FALSE) String tokens) {

		AvailabilityWindow toDomain() {
			return toDomain(null);
		}

		AvailabilityWindow toDomain(String availabilityText) {
			LocalDate mappedDate = null;
			LocalDate mappedStartDate = null;
			LocalDate mappedEndDate = null;
			DayOfWeek mappedDayOfWeek = null;
			DayOfWeek detectedDayOfWeek = detectDayOfWeek(availabilityText);
			if (this.dateVal != null && (this.dayOfWeek == null || this.dateVal.getDayOfWeek() == this.dayOfWeek)) {
				if (this.dayOfWeek == null && detectedDayOfWeek != null
						&& this.dateVal.getDayOfWeek() != detectedDayOfWeek) {
					mappedDayOfWeek = detectedDayOfWeek;
				}
				else {
					mappedDate = this.dateVal;
				}
			}
			else if (this.dayOfWeek != null) {
				mappedDayOfWeek = this.dayOfWeek;
			}
			else if (detectedDayOfWeek != null && this.startDate == null && this.endDate == null) {
				mappedDayOfWeek = detectedDayOfWeek;
			}
			else {
				mappedStartDate = this.startDate;
				mappedEndDate = this.endDate;
			}
			return new AvailabilityWindow(this.kind, mappedDate, mappedStartDate, mappedEndDate, mappedDayOfWeek,
					parseClinicLocalTime(this.startTime), parseClinicLocalTime(this.endTime), this.tokens);
		}

	}

}
