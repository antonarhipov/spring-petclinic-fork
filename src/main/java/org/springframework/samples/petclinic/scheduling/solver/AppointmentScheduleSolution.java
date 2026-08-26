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

package org.springframework.samples.petclinic.scheduling.solver;

import java.util.List;
import ai.timefold.solver.core.api.domain.solution.PlanningEntityProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;

@PlanningSolution
public class AppointmentScheduleSolution {

	@PlanningScore
	private HardMediumSoftScore score;

	@ProblemFactCollectionProperty
	private List<BookedSlot> bookedSlots;

	@ProblemFactCollectionProperty
	private List<VetAvailability> vetAvailabilities;

	@ProblemFactCollectionProperty
	private List<ExcludedSlot> excludedSlots = List.of();

	@ValueRangeProvider(id = "slotRange")
	@ProblemFactCollectionProperty
	private List<CandidateSlot> candidateSlots;

	@PlanningEntityProperty
	private ProposedBooking proposedBooking;

	public AppointmentScheduleSolution() {
	}

	public AppointmentScheduleSolution(List<BookedSlot> bookedSlots, List<VetAvailability> vetAvailabilities,
			List<CandidateSlot> candidateSlots, ProposedBooking proposedBooking) {
		this(bookedSlots, vetAvailabilities, List.of(), candidateSlots, proposedBooking);
	}

	public AppointmentScheduleSolution(List<BookedSlot> bookedSlots, List<VetAvailability> vetAvailabilities,
			List<ExcludedSlot> excludedSlots, List<CandidateSlot> candidateSlots, ProposedBooking proposedBooking) {
		this.bookedSlots = bookedSlots;
		this.vetAvailabilities = vetAvailabilities;
		this.excludedSlots = excludedSlots != null ? excludedSlots : List.of();
		this.candidateSlots = candidateSlots;
		this.proposedBooking = proposedBooking;
	}

	public HardMediumSoftScore getScore() {
		return this.score;
	}

	public void setScore(HardMediumSoftScore score) {
		this.score = score;
	}

	public List<BookedSlot> getBookedSlots() {
		return this.bookedSlots;
	}

	public void setBookedSlots(List<BookedSlot> bookedSlots) {
		this.bookedSlots = bookedSlots;
	}

	public List<VetAvailability> getVetAvailabilities() {
		return this.vetAvailabilities;
	}

	public void setVetAvailabilities(List<VetAvailability> vetAvailabilities) {
		this.vetAvailabilities = vetAvailabilities;
	}

	public List<ExcludedSlot> getExcludedSlots() {
		return this.excludedSlots;
	}

	public void setExcludedSlots(List<ExcludedSlot> excludedSlots) {
		this.excludedSlots = excludedSlots;
	}

	public List<CandidateSlot> getCandidateSlots() {
		return this.candidateSlots;
	}

	public void setCandidateSlots(List<CandidateSlot> candidateSlots) {
		this.candidateSlots = candidateSlots;
	}

	public ProposedBooking getProposedBooking() {
		return this.proposedBooking;
	}

	public void setProposedBooking(ProposedBooking proposedBooking) {
		this.proposedBooking = proposedBooking;
	}

}
