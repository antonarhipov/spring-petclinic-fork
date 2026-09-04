/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 */

package org.springframework.samples.petclinic.scheduling.solver;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import ai.timefold.solver.core.api.solver.SolverManager;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicConfig;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicConfigRepository;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicOpeningHour;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicOpeningHourRepository;
import org.springframework.samples.petclinic.scheduling.clinic.VetException;
import org.springframework.samples.petclinic.scheduling.clinic.VetExceptionRepository;
import org.springframework.samples.petclinic.scheduling.clinic.VetWeeklyBlock;
import org.springframework.samples.petclinic.scheduling.clinic.VetWeeklyBlockRepository;
import org.springframework.samples.petclinic.scheduling.interpretation.Interpretation;
import org.springframework.samples.petclinic.scheduling.interpretation.AvailabilityWindow;
import org.springframework.samples.petclinic.scheduling.interpretation.WindowMatcher;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enumerates only migrated clinic/veterinarian availability and delegates the choice
 * among those candidates to Timefold.
 */
@Service
public class DefaultSlotRanker implements SlotRanker {

	private static final WindowMatcher WINDOW_MATCHER = new WindowMatcher();

	private final VetRepository vetRepository;

	private final ClinicConfigRepository configRepository;

	private final ClinicOpeningHourRepository openingHourRepository;

	private final VetWeeklyBlockRepository weeklyBlockRepository;

	private final VetExceptionRepository exceptionRepository;

	private final AppointmentRepository appointmentRepository;

	private final SchedulingRequestRepository requestRepository;

	private final SolverManager<ScheduleSolution> solverManager;

	private final Clock clock;

	public DefaultSlotRanker(VetRepository vetRepository, ClinicConfigRepository configRepository,
			ClinicOpeningHourRepository openingHourRepository, VetWeeklyBlockRepository weeklyBlockRepository,
			VetExceptionRepository exceptionRepository, AppointmentRepository appointmentRepository,
			SchedulingRequestRepository requestRepository, SolverManager<ScheduleSolution> solverManager, Clock clock) {
		this.vetRepository = vetRepository;
		this.configRepository = configRepository;
		this.openingHourRepository = openingHourRepository;
		this.weeklyBlockRepository = weeklyBlockRepository;
		this.exceptionRepository = exceptionRepository;
		this.appointmentRepository = appointmentRepository;
		this.requestRepository = requestRepository;
		this.solverManager = solverManager;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public List<RankedSlot> rankSlots(SchedulingRequest request, Interpretation interpretation) {
		ClinicConfig config = this.configRepository.findById(1).orElseThrow();
		ZonedDateTime now = ZonedDateTime.now(this.clock);
		ZonedDateTime horizonEnd = now.toLocalDate()
			.plusDays(config.getBookingHorizonDays())
			.atTime(LocalTime.MAX)
			.atZone(this.clock.getZone());
		int duration = interpretation != null && interpretation.getEstimatedMinutes() != null
				? interpretation.getEstimatedMinutes() : config.getDefaultDurationMinutes();
		duration = Math.max(config.getMinDurationMinutes(), Math.min(config.getMaxDurationMinutes(), duration));

		List<ClinicOpeningHour> clinicHours = this.openingHourRepository.findAll();
		List<Appointment> appointments = this.appointmentRepository.findConfirmedInDateRange(now, horizonEnd);
		List<SchedulingRequest> holds = this.requestRepository.findAllActiveHolds();
		List<AppointmentSlot> feasibleSlots = new ArrayList<>();
		List<VetWeeklyBlock> allBlocks = new ArrayList<>();
		Collection<Vet> vets = this.vetRepository.findAll();
		List<AvailabilityWindow> windows = interpretation == null ? List.of()
				: interpretation.getWindows().stream().map(DefaultSlotRanker::toValue).toList();

		for (Vet vet : vets) {
			if (!hasRequiredSpecialty(vet, interpretation)) {
				continue;
			}
			List<VetWeeklyBlock> blocks = this.weeklyBlockRepository.findByVetId(vet.getId());
			allBlocks.addAll(blocks);
			List<ZonedDateTime> candidates = enumerate(vet, clinicHours, blocks, config, now, duration, appointments,
					holds, interpretation == null ? null : interpretation.getSpecialty(), windows, now, horizonEnd,
					request.getOwner().getId(), request.getPet().getId());
			candidates.forEach(candidate -> feasibleSlots.add(new AppointmentSlot(vet, candidate)));
		}
		if (feasibleSlots.isEmpty()) {
			return List.of();
		}
		AppointmentAssignment assignment = new AppointmentAssignment("request-" + request.getId(), request,
				interpretation == null ? null : interpretation.getSpecialty(), null, duration, windows);
		if (interpretation != null && interpretation.getPreferredVet() != null) {
			assignment.setPreferredVetId(interpretation.getPreferredVet().getId());
		}
		assignment.setClinicOpeningHours(clinicHours);
		assignment.setVetWorkingBlocks(allBlocks);
		ScheduleSolution solved = solve(new ScheduleSolution(feasibleSlots, assignment, appointments, holds));
		AppointmentAssignment selected = solved.getAssignmentList().getFirst();
		if (selected.getVet() == null || selected.getStartTime() == null
				|| solved.getScore() != null && solved.getScore().hardScore() < 0) {
			return List.of();
		}
		return List.of(new RankedSlot(selected.getVet(), selected.getStartTime(), duration, "Timefold ranked",
				solved.getScore() == null ? "uninitialized" : solved.getScore().toString()));
	}

	private ScheduleSolution solve(ScheduleSolution problem) {
		try {
			return this.solverManager.solve(UUID.randomUUID().toString(), problem).getFinalBestSolution();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Timefold solve interrupted", ex);
		}
		catch (java.util.concurrent.ExecutionException ex) {
			throw new IllegalStateException("Timefold solve failed", ex.getCause());
		}
	}

	private List<ZonedDateTime> enumerate(Vet vet, List<ClinicOpeningHour> clinicHours, List<VetWeeklyBlock> blocks,
			ClinicConfig config, ZonedDateTime now, int duration, List<Appointment> appointments,
			List<SchedulingRequest> holds, String requiredSpecialty, List<AvailabilityWindow> windows,
			ZonedDateTime horizonStart, ZonedDateTime horizonEnd, Integer ownerId, Integer petId) {
		List<LocalDate> unavailable = this.exceptionRepository.findByVetId(vet.getId())
			.stream()
			.filter(VetException::isUnavailable)
			.map(VetException::getExceptionDate)
			.toList();
		List<ZonedDateTime> result = new ArrayList<>();
		for (int day = 0; day <= config.getBookingHorizonDays(); day++) {
			LocalDate date = now.toLocalDate().plusDays(day);
			if (unavailable.contains(date)) {
				continue;
			}
			ClinicOpeningHour clinic = clinicHours.stream()
				.filter(hours -> hours.getDayOfWeek() == date.getDayOfWeek())
				.findFirst()
				.orElse(null);
			if (clinic == null || clinic.isClosed()) {
				continue;
			}
			for (VetWeeklyBlock block : blocks) {
				if (block.getDayOfWeek() != date.getDayOfWeek()) {
					continue;
				}
				LocalTime start = block.getStartTime().isAfter(clinic.getOpenTime()) ? block.getStartTime()
						: clinic.getOpenTime();
				LocalTime end = block.getEndTime().isBefore(clinic.getCloseTime()) ? block.getEndTime()
						: clinic.getCloseTime();
				for (LocalTime cursor = start; !cursor.plusMinutes(duration).isAfter(end); cursor = cursor
					.plusMinutes(config.getGridIntervalMinutes())) {
					ZonedDateTime candidate = date.atTime(cursor).atZone(this.clock.getZone());
					if (candidate.isAfter(now)
							&& isCandidateFeasible(vet, candidate, duration, clinicHours, blocks, appointments, holds,
									requiredSpecialty, windows, horizonStart, horizonEnd, ownerId, petId)) {
						result.add(candidate);
					}
				}
			}
		}
		return result;
	}

	static boolean isCandidateFeasible(Vet vet, ZonedDateTime start, int duration, List<ClinicOpeningHour> clinicHours,
			List<VetWeeklyBlock> blocks, List<Appointment> appointments, List<SchedulingRequest> holds,
			String requiredSpecialty) {
		return isCandidateFeasible(vet, start, duration, clinicHours, blocks, appointments, holds, requiredSpecialty,
				List.of(), start, start.plusDays(1), null, null);
	}

	static boolean isCandidateFeasible(Vet vet, ZonedDateTime start, int duration, List<ClinicOpeningHour> clinicHours,
			List<VetWeeklyBlock> blocks, List<Appointment> appointments, List<SchedulingRequest> holds,
			String requiredSpecialty, List<AvailabilityWindow> windows, ZonedDateTime horizonStart,
			ZonedDateTime horizonEnd, Integer ownerId, Integer petId) {
		ZonedDateTime end = start.plusMinutes(duration);
		boolean withinOpening = start.toLocalDate().equals(end.toLocalDate()) && clinicHours.stream()
			.filter(hours -> hours.getDayOfWeek() == start.getDayOfWeek() && !hours.isClosed())
			.anyMatch(hours -> !start.toLocalTime().isBefore(hours.getOpenTime())
					&& !end.toLocalTime().isAfter(hours.getCloseTime()));
		boolean withinBlock = blocks.stream()
			.filter(block -> block.getVet() != null && block.getVet().getId().equals(vet.getId()))
			.filter(block -> block.getDayOfWeek() == start.getDayOfWeek())
			.anyMatch(block -> !start.toLocalTime().isBefore(block.getStartTime())
					&& !end.toLocalTime().isAfter(block.getEndTime()));
		boolean appointmentFree = appointments.stream()
			.filter(existing -> existing.getVet() != null && existing.getVet().getId().equals(vet.getId()))
			.noneMatch(existing -> start.isBefore(existing.getEndTime()) && end.isAfter(existing.getStartTime()));
		boolean holdFree = holds.stream()
			.filter(hold -> hold.getHeldVet() != null && hold.getHeldVet().getId().equals(vet.getId()))
			.filter(hold -> hold.getHeldStart() != null && hold.getHeldDuration() != null)
			.noneMatch(hold -> start.isBefore(hold.getHeldStart().plusMinutes(hold.getHeldDuration()))
					&& end.isAfter(hold.getHeldStart()));
		boolean withinWindows = WINDOW_MATCHER.isAllowed(start, windows);
		boolean withinHorizon = !start.isBefore(horizonStart) && !start.isAfter(horizonEnd);
		boolean ownerAppointmentFree = appointments.stream()
			.filter(existing -> existing.getPet() != null && existing.getRequest() != null
					&& existing.getRequest().getOwner() != null)
			.filter(existing -> Objects.equals(ownerId, existing.getRequest().getOwner().getId())
					&& !Objects.equals(petId, existing.getPet().getId()))
			.noneMatch(existing -> start.isBefore(existing.getEndTime()) && end.isAfter(existing.getStartTime()));
		boolean ownerHoldFree = holds.stream()
			.filter(hold -> hold.getOwner() != null && hold.getPet() != null)
			.filter(hold -> Objects.equals(ownerId, hold.getOwner().getId())
					&& !Objects.equals(petId, hold.getPet().getId()))
			.filter(hold -> hold.getHeldStart() != null && hold.getHeldDuration() != null)
			.noneMatch(hold -> start.isBefore(hold.getHeldStart().plusMinutes(hold.getHeldDuration()))
					&& end.isAfter(hold.getHeldStart()));
		return withinOpening && withinBlock && appointmentFree && holdFree && withinWindows && withinHorizon
				&& ownerAppointmentFree && ownerHoldFree && hasRequiredSpecialty(vet, requiredSpecialty);
	}

	private static boolean hasRequiredSpecialty(Vet vet, Interpretation interpretation) {
		return hasRequiredSpecialty(vet, interpretation == null ? null : interpretation.getSpecialty());
	}

	private static boolean hasRequiredSpecialty(Vet vet, String requiredSpecialty) {
		if (requiredSpecialty == null || requiredSpecialty.isBlank()) {
			return true;
		}
		return vet.getSpecialties()
			.stream()
			.anyMatch(specialty -> specialty.getName().equalsIgnoreCase(requiredSpecialty.trim()));
	}

	private static org.springframework.samples.petclinic.scheduling.interpretation.AvailabilityWindow toValue(
			org.springframework.samples.petclinic.scheduling.interpretation.InterpretationWindow window) {
		return new org.springframework.samples.petclinic.scheduling.interpretation.AvailabilityWindow(window.getKind(),
				window.getDateVal(), window.getStartDate(), window.getEndDate(), window.getDayOfWeek(),
				window.getStartTime(), window.getEndTime(), window.getTokens());
	}

}
