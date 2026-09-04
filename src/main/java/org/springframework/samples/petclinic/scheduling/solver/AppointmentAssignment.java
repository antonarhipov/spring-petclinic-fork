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

package org.springframework.samples.petclinic.scheduling.solver;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.interpretation.AvailabilityWindow;
import org.springframework.samples.petclinic.scheduling.interpretation.WindowKind;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.vet.Specialty;
import org.springframework.samples.petclinic.vet.Vet;

/**
 * Timefold planning entity representing a schedulable appointment assignment (RULE-2,
 * RULE-6).
 */
@PlanningEntity
public class AppointmentAssignment {

	@PlanningId
	private String id;

	private Integer requestId;

	private Integer petId;

	private Integer ownerId;

	private String requiredSpecialty;

	private Integer preferredVetId;

	private Integer previousVetId;

	private int duration = 30;

	private List<AvailabilityWindow> preferredWindows = new ArrayList<>();

	@PlanningVariable
	private AppointmentSlot slot;

	public AppointmentAssignment() {
	}

	public AppointmentAssignment(String id) {
		this.id = id;
	}

	public AppointmentAssignment(String id, SchedulingRequest request, String requiredSpecialty, Integer previousVetId,
			int duration, List<AvailabilityWindow> preferredWindows) {
		this.id = id;
		if (request != null) {
			this.requestId = request.getId();
			if (request.getPet() != null) {
				this.petId = request.getPet().getId();
			}
			if (request.getOwner() != null) {
				this.ownerId = request.getOwner().getId();
			}
		}
		this.requiredSpecialty = requiredSpecialty;
		this.previousVetId = previousVetId;
		this.duration = duration > 0 ? duration : 30;
		if (preferredWindows != null) {
			this.preferredWindows = new ArrayList<>(preferredWindows);
		}
	}

	public String getId() {
		return this.id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public Integer getRequestId() {
		return this.requestId;
	}

	public void setRequestId(Integer requestId) {
		this.requestId = requestId;
	}

	public Integer getPetId() {
		return this.petId;
	}

	public void setPetId(Integer petId) {
		this.petId = petId;
	}

	public Integer getOwnerId() {
		return this.ownerId;
	}

	public void setOwnerId(Integer ownerId) {
		this.ownerId = ownerId;
	}

	public String getRequiredSpecialty() {
		return this.requiredSpecialty;
	}

	public void setRequiredSpecialty(String requiredSpecialty) {
		this.requiredSpecialty = requiredSpecialty;
	}

	public Integer getPreferredVetId() {
		return this.preferredVetId;
	}

	public void setPreferredVetId(Integer preferredVetId) {
		this.preferredVetId = preferredVetId;
	}

	public Integer getPreviousVetId() {
		return this.previousVetId;
	}

	public void setPreviousVetId(Integer previousVetId) {
		this.previousVetId = previousVetId;
	}

	public int getDuration() {
		return this.duration;
	}

	public void setDuration(int duration) {
		this.duration = duration;
	}

	public List<AvailabilityWindow> getPreferredWindows() {
		return this.preferredWindows;
	}

	public void setPreferredWindows(List<AvailabilityWindow> preferredWindows) {
		this.preferredWindows = preferredWindows;
	}

	public Vet getVet() {
		return this.slot == null ? null : this.slot.vet();
	}

	public void setVet(Vet vet) {
		this.slot = new AppointmentSlot(vet, getStartTime());
	}

	public ZonedDateTime getStartTime() {
		return this.slot == null ? null : this.slot.startTime();
	}

	public void setStartTime(ZonedDateTime startTime) {
		this.slot = new AppointmentSlot(getVet(), startTime);
	}

	public AppointmentSlot getSlot() {
		return this.slot;
	}

	public void setSlot(AppointmentSlot slot) {
		this.slot = slot;
	}

	public ZonedDateTime getEndTime() {
		return getStartTime() != null ? getStartTime().plusMinutes(this.duration) : null;
	}

	public boolean overlapsWith(AppointmentAssignment other) {
		if (getVet() == null || other.getVet() == null || getStartTime() == null || other.getStartTime() == null) {
			return false;
		}
		if (!Objects.equals(getVet().getId(), other.getVet().getId())) {
			return false;
		}
		return getStartTime().isBefore(other.getEndTime()) && this.getEndTime().isAfter(other.getStartTime());
	}

	public boolean overlapsWithExisting(Appointment existing) {
		if (getVet() == null || existing.getVet() == null || getStartTime() == null
				|| existing.getStartTime() == null) {
			return false;
		}
		if (!Objects.equals(getVet().getId(), existing.getVet().getId())) {
			return false;
		}
		return getStartTime().isBefore(existing.getEndTime()) && this.getEndTime().isAfter(existing.getStartTime());
	}

	public boolean overlapsWithActiveHold(SchedulingRequest activeHold) {
		if (getVet() == null || activeHold.getHeldVet() == null || getStartTime() == null
				|| activeHold.getHeldStart() == null || activeHold.getHeldDuration() == null) {
			return false;
		}
		if (!Objects.equals(getVet().getId(), activeHold.getHeldVet().getId())) {
			return false;
		}
		ZonedDateTime holdEnd = activeHold.getHeldStart().plusMinutes(activeHold.getHeldDuration());
		return getStartTime().isBefore(holdEnd) && this.getEndTime().isAfter(activeHold.getHeldStart());
	}

	public boolean isWithinClinicHours() {
		if (getStartTime() == null) {
			return false;
		}
		ZonedDateTime start = getStartTime();
		ZonedDateTime end = getEndTime();
		if (!start.toLocalDate().equals(end.toLocalDate())) {
			return false;
		}

		DayOfWeek dow = start.getDayOfWeek();
		LocalTime startT = start.toLocalTime();
		LocalTime endT = end.toLocalTime();

		if (dow == DayOfWeek.SUNDAY) {
			return false;
		}
		else if (dow == DayOfWeek.SATURDAY) {
			LocalTime open = LocalTime.of(9, 0);
			LocalTime close = LocalTime.of(13, 0);
			return !startT.isBefore(open) && !endT.isAfter(close);
		}
		else {
			// Monday to Friday: 08:30 to 17:30
			LocalTime open = LocalTime.of(8, 30);
			LocalTime close = LocalTime.of(17, 30);
			return !startT.isBefore(open) && !endT.isAfter(close);
		}
	}

	public boolean hasSpecialtyMismatch() {
		if (this.requiredSpecialty == null || this.requiredSpecialty.isBlank()) {
			return false;
		}
		if (getVet() == null) {
			return false;
		}
		for (Specialty s : getVet().getSpecialties()) {
			if (s.getName() != null && s.getName().equalsIgnoreCase(this.requiredSpecialty.trim())) {
				return false;
			}
		}
		return true;
	}

	public boolean matchesSpecialtyPreference() {
		if (this.requiredSpecialty == null || this.requiredSpecialty.isBlank()) {
			return false;
		}
		if (getVet() == null) {
			return false;
		}
		for (Specialty s : getVet().getSpecialties()) {
			if (s.getName() != null && s.getName().equalsIgnoreCase(this.requiredSpecialty.trim())) {
				return true;
			}
		}
		return false;
	}

	public boolean matchesPreviousVet() {
		if (this.previousVetId == null || getVet() == null) {
			return false;
		}
		return Objects.equals(getVet().getId(), this.previousVetId);
	}

	public boolean isWithinPreferredWindow() {
		if (getStartTime() == null || this.preferredWindows == null || this.preferredWindows.isEmpty()) {
			return false;
		}
		ZonedDateTime start = getStartTime();
		ZonedDateTime end = getEndTime();

		for (AvailabilityWindow window : this.preferredWindows) {
			if (window.kind() != WindowKind.PREFERRED && window.kind() != WindowKind.ALLOWED) {
				continue;
			}

			if (window.dateVal() != null && !start.toLocalDate().equals(window.dateVal())) {
				continue;
			}
			if (window.startDate() != null && start.toLocalDate().isBefore(window.startDate())) {
				continue;
			}
			if (window.endDate() != null && start.toLocalDate().isAfter(window.endDate())) {
				continue;
			}
			if (window.dayOfWeek() != null && start.getDayOfWeek() != window.dayOfWeek()) {
				continue;
			}
			if (window.startTime() != null && start.toLocalTime().isBefore(window.startTime())) {
				continue;
			}
			if (window.endTime() != null && end.toLocalTime().isAfter(window.endTime())) {
				continue;
			}

			return true;
		}
		return false;
	}

}
