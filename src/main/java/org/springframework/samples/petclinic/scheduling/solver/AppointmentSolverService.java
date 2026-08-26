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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
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
import org.springframework.samples.petclinic.scheduling.ai.SymbolicWindow;
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
		List<PreferredWindow> preferredWindows = toPreferredWindows(interpretation);
		if (!preferredWindows.isEmpty()) {
			log.info("Applying {} preferred window(s) from interpretation for request {}: {}", preferredWindows.size(),
					request.getId(), preferredWindows);
		}
		ProposedBooking proposedBooking = new ProposedBooking(request.getId(), duration, urgency, preferredWindows);
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

	/**
	 * Translates the AI-extracted preferred {@link SymbolicWindow}s into solver-friendly
	 * {@link PreferredWindow}s, mapping the coarse part-of-day label onto concrete time
	 * ranges. Windows that carry no usable constraint are dropped.
	 */
	private List<PreferredWindow> toPreferredWindows(Interpretation interpretation) {
		if (interpretation == null || interpretation.preferredWindows() == null) {
			return List.of();
		}
		LocalDate today = this.availabilityService.getToday();
		List<PreferredWindow> windows = new ArrayList<>();
		for (SymbolicWindow window : interpretation.preferredWindows()) {
			if (window == null) {
				continue;
			}
			LocalDate resolvedDate = resolveDate(window.dayOfWeek(), window.date(), today);
			LocalTime[] range = partOfDayRange(window.partOfDay());
			PreferredWindow preferred = new PreferredWindow(window.dayOfWeek(), resolvedDate,
					range != null ? range[0] : null, range != null ? range[1] : null);
			if (!preferred.isEmpty()) {
				windows.add(preferred);
			}
		}
		return windows;
	}

	/**
	 * Resolves the concrete calendar date for a requested window. LLMs are unreliable at
	 * date arithmetic, so the explicitly named day of week is treated as the source of
	 * truth: when it is present, the concrete date is (re)computed as the next occurrence
	 * of that weekday on/after today, overriding any inconsistent date the LLM produced
	 * (e.g. it labelled the request THURSDAY but returned a date that falls on a
	 * Wednesday). When only a date is given it is trusted as-is; when neither is given
	 * the result is {@code null}.
	 */
	static LocalDate resolveDate(DayOfWeek dayOfWeek, LocalDate date, LocalDate today) {
		if (dayOfWeek == null) {
			return date;
		}
		// Trust the interpreted date only when it agrees with the named weekday and is
		// not in the past; otherwise recompute from the weekday.
		if (date != null && date.getDayOfWeek() == dayOfWeek && !date.isBefore(today)) {
			return date;
		}
		LocalDate resolved = today.with(TemporalAdjusters.nextOrSame(dayOfWeek));
		if (date != null && !date.equals(resolved)) {
			log.info("Interpreted date {} ({}) is inconsistent with requested day {}; using next occurrence {}", date,
					date.getDayOfWeek(), dayOfWeek, resolved);
		}
		return resolved;
	}

	/**
	 * Maps a part-of-day label onto a concrete {@code [startInclusive, endExclusive)}
	 * time range using common-sense boundaries. "Afternoon" (e.g. "after lunch") means
	 * midday onwards but before the evening. Returns {@code null} when the label is
	 * unknown.
	 */
	private LocalTime[] partOfDayRange(String partOfDay) {
		if (partOfDay == null) {
			return null;
		}
		return switch (partOfDay.trim().toUpperCase()) {
			case "MORNING" -> new LocalTime[] { LocalTime.MIN, LocalTime.NOON };
			case "AFTERNOON" -> new LocalTime[] { LocalTime.NOON, LocalTime.of(17, 0) };
			case "EVENING" -> new LocalTime[] { LocalTime.of(17, 0), LocalTime.MAX };
			default -> null;
		};
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
