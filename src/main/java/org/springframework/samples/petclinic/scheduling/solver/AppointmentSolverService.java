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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import ai.timefold.solver.core.api.solver.SolverJob;
import ai.timefold.solver.core.api.solver.SolverManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.samples.petclinic.clinic.AvailabilityService;
import org.springframework.samples.petclinic.clinic.ClinicSettings;
import org.springframework.samples.petclinic.scheduling.ai.Interpretation;
import org.springframework.samples.petclinic.scheduling.model.RequestExclusion;
import org.springframework.samples.petclinic.scheduling.model.RequestExclusionRepository;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.model.SlotOccupancy;
import org.springframework.samples.petclinic.scheduling.model.SlotOccupancyRepository;
import org.springframework.samples.petclinic.scheduling.ai.UrgencyLevel;
import org.springframework.samples.petclinic.vet.Specialty;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentSolverService {

	private static final Logger log = LoggerFactory.getLogger(AppointmentSolverService.class);

	private final AvailabilityService availabilityService;

	private final SlotOccupancyRepository slotOccupancyRepository;

	private final VetRepository vetRepository;

	private final RequestExclusionRepository requestExclusionRepository;

	private final SolverManager<AppointmentScheduleSolution, UUID> solverManager;

	public AppointmentSolverService(AvailabilityService availabilityService,
			SlotOccupancyRepository slotOccupancyRepository, VetRepository vetRepository,
			RequestExclusionRepository requestExclusionRepository,
			SolverManager<AppointmentScheduleSolution, UUID> solverManager) {
		this.availabilityService = availabilityService;
		this.slotOccupancyRepository = slotOccupancyRepository;
		this.vetRepository = vetRepository;
		this.requestExclusionRepository = requestExclusionRepository;
		this.solverManager = solverManager;
	}

	@Transactional(readOnly = true)
	public Optional<CandidateSlot> findBestSlot(SchedulingRequest request, Interpretation interpretation) {
		ClinicSettings settings = this.availabilityService.getClinicSettings();
		int duration = settings.clampDuration(interpretation != null ? interpretation.visitDurationMinutes() : null);
		int gridMinutes = (settings.getGridMinutes() > 0) ? settings.getGridMinutes() : 15;

		List<Vet> allVets = new ArrayList<>(this.vetRepository.findAll());
		List<Vet> targetVets = filterVetsBySpecialty(allVets, interpretation);

		if (targetVets.isEmpty()) {
			log.info("No matching vets for request {} with required specialty: {}", request.getId(),
					interpretation != null ? interpretation.requiredSpecialty() : null);
			return Optional.empty();
		}

		List<Integer> targetVetIds = targetVets.stream().map(Vet::getId).collect(Collectors.toList());
		List<VetAvailability> availabilities = this.availabilityService.getHorizonAvailability(targetVetIds);

		if (availabilities.isEmpty()) {
			log.info("No vet availability found in horizon for request {}", request.getId());
			return Optional.empty();
		}

		LocalDateTime now = this.availabilityService.getNow().toLocalDateTime();
		List<CandidateSlot> candidateSlots = generateCandidateSlots(availabilities, duration, gridMinutes, now);

		if (candidateSlots.isEmpty()) {
			log.info("No candidate slots generated for request {}", request.getId());
			return Optional.empty();
		}

		LocalDateTime horizonStart = this.availabilityService.getToday().atStartOfDay();
		LocalDateTime horizonEnd = this.availabilityService.getHorizonEndDate().atTime(23, 59, 59);
		List<SlotOccupancy> occupancies = this.slotOccupancyRepository.findByStartTimeBetween(horizonStart, horizonEnd);
		List<BookedSlot> bookedSlots = occupancies.stream()
			.map(o -> new BookedSlot(o.getVet().getId(), o.getStartTime(), o.getEndTime()))
			.collect(Collectors.toList());

		List<RequestExclusion> exclusions = this.requestExclusionRepository.findByRequestId(request.getId());
		List<ExcludedSlot> excludedSlots = exclusions.stream()
			.map(e -> new ExcludedSlot(e.getVet().getId(), e.getStartTime()))
			.collect(Collectors.toList());

		UrgencyLevel urgency = (interpretation != null && interpretation.urgency() != null) ? interpretation.urgency()
				: UrgencyLevel.ROUTINE;
		ProposedBooking proposedBooking = new ProposedBooking(request.getId(), duration, urgency);
		AppointmentScheduleSolution problem = new AppointmentScheduleSolution(bookedSlots, availabilities,
				excludedSlots, candidateSlots, proposedBooking);

		try {
			UUID problemId = UUID.randomUUID();
			SolverJob<AppointmentScheduleSolution, UUID> solverJob = this.solverManager.solve(problemId, problem);
			AppointmentScheduleSolution solution = solverJob.getFinalBestSolution();

			if (solution.getScore() != null && solution.getScore().isFeasible()) {
				CandidateSlot selected = solution.getProposedBooking().getSelectedSlot();
				if (selected != null) {
					log.info("Solver selected slot {} for request {}", selected, request.getId());
					return Optional.of(selected);
				}
			}
			log.info("Solver produced infeasible or null slot for request {}", request.getId());
			return Optional.empty();
		}
		catch (Exception e) {
			log.error("Error solving scheduling for request {}: {}", request.getId(), e.getMessage(), e);
			throw new IllegalStateException("Solver execution failed", e);
		}
	}

	private List<Vet> filterVetsBySpecialty(List<Vet> vets, Interpretation interpretation) {
		if (interpretation == null || interpretation.requiredSpecialty() == null
				|| interpretation.requiredSpecialty().isBlank()) {
			return vets;
		}

		String required = interpretation.requiredSpecialty().trim().toLowerCase();
		return vets.stream().filter(vet -> {
			for (Specialty s : vet.getSpecialties()) {
				if (s.getName() != null && s.getName().toLowerCase().contains(required)) {
					return true;
				}
			}
			return false;
		}).collect(Collectors.toList());
	}

	private List<CandidateSlot> generateCandidateSlots(List<VetAvailability> availabilities, int durationMinutes,
			int gridMinutes, LocalDateTime now) {
		List<CandidateSlot> slots = new ArrayList<>();
		for (VetAvailability avail : availabilities) {
			LocalDateTime current = avail.startTime();
			LocalDateTime end = avail.endTime();

			while (!current.plusMinutes(durationMinutes).isAfter(end)) {
				if (current.isAfter(now)) {
					slots.add(new CandidateSlot(avail.vetId(), current, durationMinutes));
				}
				current = current.plusMinutes(gridMinutes);
			}
		}
		return slots;
	}

}
