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

package org.springframework.samples.petclinic.scheduling;

import java.time.LocalDateTime;

/**
 * View model that normalizes a confirmed {@code Appointment} and an in-progress or
 * recently-closed {@code SchedulingRequest} into a single row shape for the owner-facing
 * "My Appointments" list.
 * <p>
 * Building this DTO inside a transactional service method resolves the lazy
 * {@code owner}/{@code pet}/{@code vet} associations while the persistence session is
 * still open, so the template only ever reads plain, already-materialized values.
 *
 * @param kind whether the row represents a scheduling request or a confirmed appointment
 * @param id identifier of the underlying request or appointment
 * @param petName the pet's name
 * @param vetOrSummary the veterinarian's name for appointments, or the request summary
 * (raw text) otherwise
 * @param when the appointment start time, or {@code null} for requests that have no slot
 * yet
 * @param statusLabel owner-friendly status label
 * @param badgeClass Bootstrap badge CSS class used to color-code the status
 * @param actionable {@code true} when the row needs the owner to act (a request awaiting
 * confirmation or an offered slot)
 * @param detailUrl link to the request status page or the appointment details page
 * @param cancelUrl link to the cancel action, or {@code null} when cancellation is not
 * available
 */
public record OwnerScheduleItem(Kind kind, Integer id, String petName, String vetOrSummary, LocalDateTime when,
		String statusLabel, String badgeClass, boolean actionable, String detailUrl, String cancelUrl) {

	public enum Kind {

		REQUEST, APPOINTMENT

	}

}
