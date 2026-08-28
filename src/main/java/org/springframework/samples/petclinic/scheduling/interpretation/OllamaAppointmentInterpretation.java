/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * LLM response boundary. String time fields keep provider formatting quirks out of the
 * application-owned interpretation model.
 */
record OllamaAppointmentInterpretation(String careType,
		@Schema(description = "Required veterinary specialty; omit when unknown",
				requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Nullable String requiredSpecialty,
		@Schema(description = "Estimated appointment duration in minutes; omit when unknown",
				requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Nullable Integer estimatedDurationMin,
		List<OllamaInterpretationWindow> preferred, List<OllamaInterpretationWindow> allowed,
		List<OllamaInterpretationWindow> excluded,
		@Schema(description = "Positive veterinarian id explicitly requested by the owner; omit when unknown",
				requiredMode = Schema.RequiredMode.NOT_REQUIRED, minimum = "1") @Nullable Integer preferredVetId,
		Urgency urgency) {

	OllamaAppointmentInterpretation {
		preferred = preferred == null ? List.of() : List.copyOf(preferred);
		allowed = allowed == null ? List.of() : List.copyOf(allowed);
		excluded = excluded == null ? List.of() : List.copyOf(excluded);
	}

	AppointmentInterpretation toAppointmentInterpretation() {
		return new AppointmentInterpretation(this.careType, blankToNull(this.requiredSpecialty),
				this.estimatedDurationMin, toInterpretationWindows(this.preferred),
				toInterpretationWindows(this.allowed), toInterpretationWindows(this.excluded),
				this.preferredVetId != null && this.preferredVetId == 0 ? null : this.preferredVetId, this.urgency);
	}

	private static List<InterpretationWindow> toInterpretationWindows(List<OllamaInterpretationWindow> windows) {
		return windows.stream().map(OllamaInterpretationWindow::toInterpretationWindow).toList();
	}

	private static @Nullable String blankToNull(@Nullable String value) {
		return value == null || value.isBlank() ? null : value;
	}

}

record OllamaInterpretationWindow(DayOfWeek dayOfWeek,
		@Schema(description = "Clinic day part: morning, afternoon, or evening; omit for an explicit time range",
				requiredMode = Schema.RequiredMode.NOT_REQUIRED, allowableValues = {
						"morning", "afternoon", "evening" }) @Nullable String dayPart,
		@Schema(description = "Local start time without a zone or UTC offset; omit when dayPart is present",
				requiredMode = Schema.RequiredMode.NOT_REQUIRED,
				pattern = OllamaInterpretationWindow.LOCAL_TIME_PATTERN) @Nullable String start,
		@Schema(description = "Local end time without a zone or UTC offset; omit when dayPart is present",
				requiredMode = Schema.RequiredMode.NOT_REQUIRED,
				pattern = OllamaInterpretationWindow.LOCAL_TIME_PATTERN) @Nullable String end){

	static final String LOCAL_TIME_PATTERN = "^(?:[01]\\d|2[0-3]):[0-5]\\d(?::[0-5]\\d(?:\\.\\d{1,9})?)?$";

	InterpretationWindow toInterpretationWindow() {
		if (this.dayPart != null && !this.dayPart.isBlank()) {
			return new InterpretationWindow(this.dayOfWeek, this.dayPart, null, null, null);
		}
		return new InterpretationWindow(this.dayOfWeek, null, parseLocalTime(this.start), parseLocalTime(this.end),
				null);
	}

	private static @Nullable LocalTime parseLocalTime(@Nullable String value) {
		return value == null || value.isBlank() ? null : LocalTime.parse(value.strip(), DateTimeFormatter.ISO_TIME);
	}

}
