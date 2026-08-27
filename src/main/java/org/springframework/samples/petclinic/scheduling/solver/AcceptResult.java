/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.scheduling.solver;

import org.springframework.samples.petclinic.appointment.Appointment;

public record AcceptResult(Status status, Appointment appointment, SuggestionResult nextSuggestion, String message) {

	public enum Status {

		SCHEDULED, REPLACED, QUEUED_FOR_STAFF

	}

}
