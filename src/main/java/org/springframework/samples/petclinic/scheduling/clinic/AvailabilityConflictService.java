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

package org.springframework.samples.petclinic.scheduling.clinic;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEvent;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEventRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomic boundary for availability edits that can remove confirmed capacity (RULE-34).
 */
@Service
public class AvailabilityConflictService {

	private final AppointmentRepository appointmentRepository;

	private final SchedulingRequestRepository requestRepository;

	private final SchedulingRequestEventRepository eventRepository;

	private final Clock clock;

	public AvailabilityConflictService(AppointmentRepository appointmentRepository,
			SchedulingRequestRepository requestRepository, SchedulingRequestEventRepository eventRepository,
			Clock clock) {
		this.appointmentRepository = appointmentRepository;
		this.requestRepository = requestRepository;
		this.eventRepository = eventRepository;
		this.clock = clock;
	}

	/**
	 * Checks the complete edit before mutation. Confirmed conflicts refuse the whole
	 * operation; otherwise every conflicting hold is invalidated in the same transaction
	 * as the edit.
	 */
	@Transactional
	public <T> EditResult<T> apply(List<? extends AvailabilityEdit> edits, String actor, Supplier<T> mutation) {
		List<? extends AvailabilityEdit> effectiveEdits = edits == null ? List.of() : List.copyOf(edits);
		List<AppointmentConflict> confirmedConflicts = this.appointmentRepository
			.findByStatus(AppointmentStatus.CONFIRMED)
			.stream()
			.filter(appointment -> conflicts(effectiveEdits, appointment.getVet().getId(), appointment.getStartTime(),
					appointment.getDuration()))
			.map(AppointmentConflict::from)
			.sorted(Comparator.comparing(AppointmentConflict::startTime).thenComparing(AppointmentConflict::id))
			.toList();
		if (!confirmedConflicts.isEmpty()) {
			return new EditResult<>(false, confirmedConflicts, List.of(), null);
		}

		String effectiveActor = actor == null || actor.isBlank() ? "staff" : actor;
		List<SchedulingRequest> conflictingHolds = this.requestRepository.findAllActiveHolds()
			.stream()
			.filter(request -> request.hasHold())
			.filter(request -> conflicts(effectiveEdits, request.getHeldVet().getId(), request.getHeldStart(),
					request.getHeldDuration()))
			.sorted(Comparator.comparing(SchedulingRequest::getId))
			.toList();
		List<Integer> invalidatedIds = conflictingHolds.stream().map(SchedulingRequest::getId).toList();
		for (SchedulingRequest request : conflictingHolds) {
			invalidateHold(request, effectiveActor);
		}

		T value = mutation.get();
		return new EditResult<>(true, List.of(), invalidatedIds, value);
	}

	private boolean conflicts(List<? extends AvailabilityEdit> edits, Integer vetId, ZonedDateTime start,
			int duration) {
		return edits.stream()
			.anyMatch(edit -> edit.conflicts(vetId, start.toLocalDate(), start.toLocalTime(),
					start.toLocalTime().plusMinutes(duration)));
	}

	private void invalidateHold(SchedulingRequest request, String actor) {
		RequestState fromState = request.getState();
		ZonedDateTime now = ZonedDateTime.now(this.clock);
		request.clearHold();
		request.setState(RequestState.WITH_STAFF);
		request.setUpdatedAt(now);
		SchedulingRequest saved = this.requestRepository.save(request);

		SchedulingRequestEvent event = new SchedulingRequestEvent();
		event.setRequest(saved);
		event.setFromState(fromState);
		event.setToState(RequestState.WITH_STAFF);
		event.setActor(actor);
		event.setAction("HOLD_INVALIDATED");
		event.setReason("availability edit");
		event.setTimestamp(now);
		this.eventRepository.save(event);
	}

	public sealed interface AvailabilityEdit permits OpeningHoursEdit, ExceptionEdit, LeaveEdit, ClosureEdit {

		boolean conflicts(Integer vetId, LocalDate date, LocalTime start, LocalTime end);

	}

	public record OpeningHoursEdit(DayOfWeek dayOfWeek, boolean closed, LocalTime openTime,
			LocalTime closeTime) implements AvailabilityEdit {

		@Override
		public boolean conflicts(Integer vetId, LocalDate date, LocalTime start, LocalTime end) {
			return date.getDayOfWeek() == this.dayOfWeek && (this.closed
					|| !contains(List.of(new VetAvailabilityService.TimeInterval(this.openTime, this.closeTime)), start,
							end));
		}

	}

	public record ExceptionEdit(Integer vetId, LocalDate date,
			List<VetAvailabilityService.TimeInterval> replacementBlocks) implements AvailabilityEdit {

		public ExceptionEdit {
			replacementBlocks = replacementBlocks == null ? List.of() : List.copyOf(replacementBlocks);
		}

		@Override
		public boolean conflicts(Integer candidateVetId, LocalDate candidateDate, LocalTime start, LocalTime end) {
			return Objects.equals(this.vetId, candidateVetId) && this.date.equals(candidateDate)
					&& !contains(this.replacementBlocks, start, end);
		}

	}

	public record LeaveEdit(Integer vetId, LocalDate startDate, LocalDate endDate) implements AvailabilityEdit {

		@Override
		public boolean conflicts(Integer candidateVetId, LocalDate date, LocalTime start, LocalTime end) {
			return Objects.equals(this.vetId, candidateVetId) && !date.isBefore(this.startDate)
					&& !date.isAfter(this.endDate);
		}

	}

	public record ClosureEdit(LocalDate date) implements AvailabilityEdit {

		@Override
		public boolean conflicts(Integer vetId, LocalDate candidateDate, LocalTime start, LocalTime end) {
			return this.date.equals(candidateDate);
		}

	}

	private static boolean contains(List<VetAvailabilityService.TimeInterval> blocks, LocalTime start, LocalTime end) {
		return blocks.stream().anyMatch(block -> !start.isBefore(block.start()) && !end.isAfter(block.end()));
	}

	public record AppointmentConflict(Integer id, Integer vetId, ZonedDateTime startTime, int duration) {

		private static AppointmentConflict from(Appointment appointment) {
			return new AppointmentConflict(appointment.getId(), appointment.getVet().getId(),
					appointment.getStartTime(), appointment.getDuration());
		}

	}

	public record EditResult<T>(boolean applied, List<AppointmentConflict> conflicts, List<Integer> invalidatedHoldIds,
			T value) {

		public boolean holdsInvalidated() {
			return !this.invalidatedHoldIds.isEmpty();
		}

	}

}
