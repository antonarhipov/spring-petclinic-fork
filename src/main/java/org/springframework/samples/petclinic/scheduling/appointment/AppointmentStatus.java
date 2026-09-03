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

package org.springframework.samples.petclinic.scheduling.appointment;

/**
 * Status of an appointment (RULE-16).
 */
public enum AppointmentStatus {

	CONFIRMED("Confirmed"), CANCELLED_BY_OWNER("Cancelled by owner"), CANCELLED_BY_STAFF("Cancelled by staff"),
	COMPLETED("Completed"), NO_SHOW("No-show");

	private final String displayName;

	AppointmentStatus(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public boolean isTerminal() {
		return this != CONFIRMED;
	}

}
