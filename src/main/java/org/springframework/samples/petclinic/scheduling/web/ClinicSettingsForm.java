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

package org.springframework.samples.petclinic.scheduling.web;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Backing form object for clinic settings (AC-99, RULE-37).
 */
public class ClinicSettingsForm {

	private Map<String, String> openingHours = new LinkedHashMap<>();

	private Integer minDurationMinutes = 15;

	private Integer maxDurationMinutes = 60;

	private Integer defaultDurationMinutes = 30;

	private Integer horizonDays = 28;

	private String morningStart = "09:00";

	private String morningEnd = "12:00";

	private String afternoonStart = "12:00";

	private String afternoonEnd = "17:00";

	private String eveningStart = "17:00";

	private String eveningEnd = "20:00";

	private String emergencyPhone = "555-0199";

	private String timeZone = "America/New_York";

	public Map<String, String> getOpeningHours() {
		return this.openingHours;
	}

	public void setOpeningHours(Map<String, String> openingHours) {
		this.openingHours = openingHours;
	}

	public Integer getMinDurationMinutes() {
		return this.minDurationMinutes;
	}

	public void setMinDurationMinutes(Integer minDurationMinutes) {
		this.minDurationMinutes = minDurationMinutes;
	}

	public Integer getMaxDurationMinutes() {
		return this.maxDurationMinutes;
	}

	public void setMaxDurationMinutes(Integer maxDurationMinutes) {
		this.maxDurationMinutes = maxDurationMinutes;
	}

	public Integer getDefaultDurationMinutes() {
		return this.defaultDurationMinutes;
	}

	public void setDefaultDurationMinutes(Integer defaultDurationMinutes) {
		this.defaultDurationMinutes = defaultDurationMinutes;
	}

	public Integer getHorizonDays() {
		return this.horizonDays;
	}

	public void setHorizonDays(Integer horizonDays) {
		this.horizonDays = horizonDays;
	}

	public String getMorningStart() {
		return this.morningStart;
	}

	public void setMorningStart(String morningStart) {
		this.morningStart = morningStart;
	}

	public String getMorningEnd() {
		return this.morningEnd;
	}

	public void setMorningEnd(String morningEnd) {
		this.morningEnd = morningEnd;
	}

	public String getAfternoonStart() {
		return this.afternoonStart;
	}

	public void setAfternoonStart(String afternoonStart) {
		this.afternoonStart = afternoonStart;
	}

	public String getAfternoonEnd() {
		return this.afternoonEnd;
	}

	public void setAfternoonEnd(String afternoonEnd) {
		this.afternoonEnd = afternoonEnd;
	}

	public String getEveningStart() {
		return this.eveningStart;
	}

	public void setEveningStart(String eveningStart) {
		this.eveningStart = eveningStart;
	}

	public String getEveningEnd() {
		return this.eveningEnd;
	}

	public void setEveningEnd(String eveningEnd) {
		this.eveningEnd = eveningEnd;
	}

	public String getEmergencyPhone() {
		return this.emergencyPhone;
	}

	public void setEmergencyPhone(String emergencyPhone) {
		this.emergencyPhone = emergencyPhone;
	}

	public String getTimeZone() {
		return this.timeZone;
	}

	public void setTimeZone(String timeZone) {
		this.timeZone = timeZone;
	}

}
