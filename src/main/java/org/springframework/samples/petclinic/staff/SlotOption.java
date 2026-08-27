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
package org.springframework.samples.petclinic.staff;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * View helper DTO representing a bookable slot option formatted for display.
 */
public class SlotOption {

	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

	private final Instant startInstant;

	private final String startInstantIso;

	private final String startTimeFormatted;

	private final String endTimeFormatted;

	private final String label;

	public SlotOption(Instant startInstant, int durationMin, ZoneId zoneId) {
		this.startInstant = startInstant;
		this.startInstantIso = startInstant.toString();
		ZonedDateTime startZoned = startInstant.atZone(zoneId);
		ZonedDateTime endZoned = startInstant.plus(Duration.ofMinutes(durationMin)).atZone(zoneId);
		this.startTimeFormatted = startZoned.format(TIME_FORMATTER);
		this.endTimeFormatted = endZoned.format(TIME_FORMATTER);
		this.label = this.startTimeFormatted + " - " + this.endTimeFormatted;
	}

	public Instant getStartInstant() {
		return this.startInstant;
	}

	public String getStartInstantIso() {
		return this.startInstantIso;
	}

	public String getStartTimeFormatted() {
		return this.startTimeFormatted;
	}

	public String getEndTimeFormatted() {
		return this.endTimeFormatted;
	}

	public String getLabel() {
		return this.label;
	}

}
