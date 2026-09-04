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

package org.springframework.samples.petclinic.scheduling.request;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.solver.SlotRanker;
import org.springframework.stereotype.Service;

/**
 * Owns the timerless, request-row hold boundary and its overlap view (RULE-8).
 */
@Service
public class HoldService {

	private final SchedulingRequestRepository requestRepository;

	private final AppointmentRepository appointmentRepository;

	private final RequestLifecycleService requestLifecycleService;

	public HoldService(SchedulingRequestRepository requestRepository, AppointmentRepository appointmentRepository,
			RequestLifecycleService requestLifecycleService) {
		this.requestRepository = requestRepository;
		this.appointmentRepository = appointmentRepository;
		this.requestLifecycleService = requestLifecycleService;
	}

	public SchedulingRequest offer(SchedulingRequest request, String actor, SlotRanker.RankedSlot slot) {
		Objects.requireNonNull(request, "request must not be null");
		Objects.requireNonNull(slot, "slot must not be null");
		if (request.getState() == RequestState.INTERPRETED) {
			return this.requestLifecycleService.confirmFeasible(request, actor, slot.vet(), slot.startTime(),
					slot.duration());
		}
		if (request.getState() == RequestState.SUGGESTION_OFFERED) {
			return this.requestLifecycleService.askForAnotherOptionSlotsRemain(request, actor, slot.vet(),
					slot.startTime(), slot.duration());
		}
		throw new IllegalRequestTransitionException(request.getState(), "suggest");
	}

	public SchedulingRequest exhausted(SchedulingRequest request, String actor, String reason) {
		Objects.requireNonNull(request, "request must not be null");
		if (request.getState() == RequestState.INTERPRETED) {
			return this.requestLifecycleService.confirmNoFeasibleSlots(request, actor, reason);
		}
		if (request.getState() == RequestState.SUGGESTION_OFFERED) {
			return this.requestLifecycleService.askForAnotherOptionExhausted(request, actor, reason);
		}
		throw new IllegalRequestTransitionException(request.getState(), "suggest");
	}

	public boolean isAvailable(Integer vetId, ZonedDateTime start, int duration, Integer currentRequestId) {
		ZonedDateTime end = start.plusMinutes(duration);
		List<Appointment> appointments = this.appointmentRepository.findConfirmedByVetIdAndDateRange(vetId,
				start.minusMinutes(120), end.plusMinutes(120));
		boolean appointmentConflict = appointments.stream()
			.anyMatch(existing -> existing.getStartTime().isBefore(end) && existing.getEndTime().isAfter(start));
		if (appointmentConflict) {
			return false;
		}

		return this.requestRepository.findActiveHoldsByVetId(vetId)
			.stream()
			.filter(hold -> !Objects.equals(hold.getId(), currentRequestId))
			.filter(SchedulingRequest::hasHold)
			.noneMatch(hold -> hold.getHeldStart().isBefore(end)
					&& hold.getHeldStart().plusMinutes(hold.getHeldDuration()).isAfter(start));
	}

}
