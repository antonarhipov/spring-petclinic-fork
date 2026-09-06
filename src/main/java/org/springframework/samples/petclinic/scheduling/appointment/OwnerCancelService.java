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

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Objects;

import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.web.OwnerSchedulingAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Domain service enforcing owner appointment cancellation rules and request integrity
 * (RULE-16, AC-116, AC-117).
 */
@Service
@Transactional
public class OwnerCancelService {

	private final OwnerSchedulingAccessService accessService;

	private final AppointmentLifecycleService appointmentLifecycleService;

	private final Clock clock;

	public OwnerCancelService(OwnerSchedulingAccessService accessService,
			AppointmentLifecycleService appointmentLifecycleService, Clock clock) {
		this.accessService = accessService;
		this.appointmentLifecycleService = appointmentLifecycleService;
		this.clock = clock;
	}

	public Appointment cancel(Integer ownerId, Integer appointmentId, String actor) {
		Objects.requireNonNull(ownerId, "ownerId must not be null");
		Objects.requireNonNull(appointmentId, "appointmentId must not be null");
		Appointment appointment = this.accessService.requireAppointment(ownerId, appointmentId);
		return cancel(appointment, actor);
	}

	public Appointment cancel(Appointment appointment, String actor) {
		Objects.requireNonNull(appointment, "appointment must not be null");
		if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
			throw new IllegalAppointmentTransitionException(
					"Cannot cancel appointment with status " + appointment.getStatus() + " (owner cancel)");
		}
		ZonedDateTime now = ZonedDateTime.now(this.clock);
		if (!now.isBefore(appointment.getStartTime())) {
			throw new IllegalAppointmentTransitionException(
					"Cannot cancel appointment after start time " + appointment.getStartTime() + " (owner cancel)");
		}

		Appointment cancelled = this.appointmentLifecycleService.ownerCancel(appointment, actor);

		SchedulingRequest request = cancelled.getRequest();
		if (request != null && request.getState() != RequestState.ACCEPTED) {
			throw new IllegalStateException("Associated scheduling request must remain closed in ACCEPTED state");
		}

		return cancelled;
	}

}
