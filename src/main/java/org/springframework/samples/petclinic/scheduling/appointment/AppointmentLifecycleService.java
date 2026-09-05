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

import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.Visit;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service enforcing the appointment lifecycle state machine and timing guards (RULE-16,
 * AC-124).
 */
@Service
@Transactional
public class AppointmentLifecycleService {

	private final AppointmentRepository appointmentRepository;

	private final AppointmentChangeRepository appointmentChangeRepository;

	private final Clock clock;

	public AppointmentLifecycleService(AppointmentRepository appointmentRepository,
			AppointmentChangeRepository appointmentChangeRepository, Clock clock) {
		this.appointmentRepository = appointmentRepository;
		this.appointmentChangeRepository = appointmentChangeRepository;
		this.clock = clock;
	}

	public Appointment bookAppointment(Pet pet, Vet vet, ZonedDateTime startTime, int duration, String reason,
			SchedulingRequest request, String actor) {
		Objects.requireNonNull(pet, "pet must not be null");
		Objects.requireNonNull(vet, "vet must not be null");
		Objects.requireNonNull(startTime, "startTime must not be null");
		requireStaffReason(reason);

		ZonedDateTime now = ZonedDateTime.now(this.clock);
		Appointment appointment = new Appointment();
		appointment.setPet(pet);
		appointment.setVet(vet);
		appointment.setStartTime(startTime);
		appointment.setDuration(duration);
		appointment.setStatus(AppointmentStatus.CONFIRMED);
		appointment.setReason(reason);
		appointment.setRequest(request);

		Appointment saved = this.appointmentRepository.save(appointment);

		AppointmentChange change = new AppointmentChange();
		change.setAppointment(saved);
		change.setActor(actor);
		change.setAction("BOOK");
		change.setReason(reason.trim());
		change.setTimestamp(now);
		this.appointmentChangeRepository.save(change);

		return saved;
	}

	public Appointment ownerCancel(Appointment appointment, String actor) {
		validateConfirmed(appointment, "owner cancel");
		validateBeforeStart(appointment, "owner cancel");

		return applyStatusChange(appointment, AppointmentStatus.CANCELLED_BY_OWNER, actor, "CANCEL_BY_OWNER", null,
				null);
	}

	public Appointment staffCancel(Appointment appointment, String actor, String reason) {
		validateConfirmed(appointment, "staff cancel");
		validateBeforeStart(appointment, "staff cancel");
		requireStaffReason(reason);

		return applyStatusChange(appointment, AppointmentStatus.CANCELLED_BY_STAFF, actor, "CANCEL_BY_STAFF",
				reason.trim(), appointment.getStartTime());
	}

	public Appointment staffReschedule(Appointment appointment, String actor, String reason, ZonedDateTime newStartTime,
			int newDuration, Vet newVet) {
		validateConfirmed(appointment, "staff reschedule");
		validateBeforeStart(appointment, "staff reschedule");
		requireStaffReason(reason);

		Objects.requireNonNull(newStartTime, "newStartTime must not be null");
		ZonedDateTime now = ZonedDateTime.now(this.clock);
		ZonedDateTime originalStartTime = appointment.getStartTime();

		appointment.setStartTime(newStartTime);
		if (newDuration > 0) {
			appointment.setDuration(newDuration);
		}
		if (newVet != null) {
			appointment.setVet(newVet);
		}

		Appointment saved = this.appointmentRepository.save(appointment);

		AppointmentChange change = new AppointmentChange();
		change.setAppointment(saved);
		change.setActor(actor);
		change.setAction("RESCHEDULE");
		change.setReason(reason.trim());
		change.setTimestamp(now);
		change.setOriginalStartTime(originalStartTime);
		this.appointmentChangeRepository.save(change);

		return saved;
	}

	public Appointment markCompleted(Appointment appointment, String actor) {
		validateConfirmed(appointment, "mark completed");
		validateAtOrAfterStart(appointment, "mark completed");

		Appointment saved = applyStatusChange(appointment, AppointmentStatus.COMPLETED, actor, "MARK_COMPLETED", null,
				null);

		Visit visit = new Visit();
		visit.setDate(saved.getStartTime().toLocalDate());
		visit.setDescription(saved.getReason() != null ? saved.getReason() : "Completed appointment");
		visit.setAppointmentId(saved.getId());
		if (saved.getPet() != null) {
			saved.getPet().addVisit(visit);
		}

		return saved;
	}

	public Appointment markNoShow(Appointment appointment, String actor, String reason) {
		validateConfirmed(appointment, "mark no-show");
		validateAtOrAfterStart(appointment, "mark no-show");

		return applyStatusChange(appointment, AppointmentStatus.NO_SHOW, actor, "MARK_NO_SHOW", reason, null);
	}

	private void validateConfirmed(Appointment appointment, String action) {
		if (appointment == null || appointment.getStatus() != AppointmentStatus.CONFIRMED) {
			AppointmentStatus status = appointment != null ? appointment.getStatus() : null;
			throw new IllegalAppointmentTransitionException(status, action);
		}
	}

	private void validateBeforeStart(Appointment appointment, String action) {
		ZonedDateTime now = ZonedDateTime.now(this.clock);
		if (!now.isBefore(appointment.getStartTime())) {
			throw new IllegalAppointmentTransitionException(
					"Cannot execute '" + action + "': appointment has already started or passed (current time: " + now
							+ ", appointment start: " + appointment.getStartTime() + ")");
		}
	}

	private void validateAtOrAfterStart(Appointment appointment, String action) {
		ZonedDateTime now = ZonedDateTime.now(this.clock);
		if (now.isBefore(appointment.getStartTime())) {
			throw new IllegalAppointmentTransitionException(
					"Cannot execute '" + action + "': appointment start time has not arrived yet (current time: " + now
							+ ", appointment start: " + appointment.getStartTime() + ")");
		}
	}

	private Appointment applyStatusChange(Appointment appointment, AppointmentStatus targetStatus, String actor,
			String action, String reason, ZonedDateTime originalStartTime) {
		ZonedDateTime now = ZonedDateTime.now(this.clock);
		appointment.setStatus(targetStatus);

		Appointment saved = this.appointmentRepository.save(appointment);

		AppointmentChange change = new AppointmentChange();
		change.setAppointment(saved);
		change.setActor(actor);
		change.setAction(action);
		change.setReason(reason);
		change.setTimestamp(now);
		change.setOriginalStartTime(originalStartTime);
		this.appointmentChangeRepository.save(change);

		return saved;
	}

	private void requireStaffReason(String reason) {
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("Staff action reason is required");
		}
	}

}
