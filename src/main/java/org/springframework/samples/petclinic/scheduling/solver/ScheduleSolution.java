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

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.vet.Vet;

/**
 * Timefold planning solution for smart appointment scheduling (RULE-2, RULE-6).
 */
@PlanningSolution
public class ScheduleSolution {

	@PlanningScore
	private HardMediumSoftScore score;

	private List<Vet> vetList = new ArrayList<>();

	private List<ZonedDateTime> timeslotList = new ArrayList<>();

	@ProblemFactCollectionProperty
	@ValueRangeProvider
	private List<AppointmentSlot> slotList = new ArrayList<>();

	@PlanningEntityCollectionProperty
	private List<AppointmentAssignment> assignmentList = new ArrayList<>();

	@ProblemFactCollectionProperty
	private List<Appointment> existingAppointments = new ArrayList<>();

	@ProblemFactCollectionProperty
	private List<SchedulingRequest> activeHolds = new ArrayList<>();

	public ScheduleSolution() {
	}

	public ScheduleSolution(List<Vet> vetList, List<ZonedDateTime> timeslotList,
			List<AppointmentAssignment> assignmentList, List<Appointment> existingAppointments,
			List<SchedulingRequest> activeHolds) {
		this.vetList = vetList != null ? vetList : new ArrayList<>();
		this.timeslotList = timeslotList != null ? timeslotList : new ArrayList<>();
		for (Vet vet : this.vetList) {
			for (ZonedDateTime timeslot : this.timeslotList) {
				this.slotList.add(new AppointmentSlot(vet, timeslot));
			}
		}
		this.assignmentList = assignmentList != null ? assignmentList : new ArrayList<>();
		this.existingAppointments = existingAppointments != null ? existingAppointments : new ArrayList<>();
		this.activeHolds = activeHolds != null ? activeHolds : new ArrayList<>();
	}

	/**
	 * Builds the single-request problem directly from already-enumerated feasible slots.
	 */
	public ScheduleSolution(List<AppointmentSlot> feasibleSlots, AppointmentAssignment assignment,
			List<Appointment> existingAppointments, List<SchedulingRequest> activeHolds) {
		this.slotList = feasibleSlots != null ? feasibleSlots : new ArrayList<>();
		this.assignmentList = assignment != null ? List.of(assignment) : new ArrayList<>();
		this.existingAppointments = existingAppointments != null ? existingAppointments : new ArrayList<>();
		this.activeHolds = activeHolds != null ? activeHolds : new ArrayList<>();
	}

	public HardMediumSoftScore getScore() {
		return this.score;
	}

	public void setScore(HardMediumSoftScore score) {
		this.score = score;
	}

	public List<Vet> getVetList() {
		return this.vetList;
	}

	public void setVetList(List<Vet> vetList) {
		this.vetList = vetList;
	}

	public List<ZonedDateTime> getTimeslotList() {
		return this.timeslotList;
	}

	public void setTimeslotList(List<ZonedDateTime> timeslotList) {
		this.timeslotList = timeslotList;
	}

	public List<AppointmentSlot> getSlotList() {
		return this.slotList;
	}

	public void setSlotList(List<AppointmentSlot> slotList) {
		this.slotList = slotList;
	}

	public List<AppointmentAssignment> getAssignmentList() {
		return this.assignmentList;
	}

	public void setAssignmentList(List<AppointmentAssignment> assignmentList) {
		this.assignmentList = assignmentList;
	}

	public List<Appointment> getExistingAppointments() {
		return this.existingAppointments;
	}

	public void setExistingAppointments(List<Appointment> existingAppointments) {
		this.existingAppointments = existingAppointments;
	}

	public List<SchedulingRequest> getActiveHolds() {
		return this.activeHolds;
	}

	public void setActiveHolds(List<SchedulingRequest> activeHolds) {
		this.activeHolds = activeHolds;
	}

}
