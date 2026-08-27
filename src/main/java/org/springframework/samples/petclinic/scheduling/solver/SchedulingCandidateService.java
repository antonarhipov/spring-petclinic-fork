/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.scheduling.solver;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.samples.petclinic.appointment.Appointment;
import org.springframework.samples.petclinic.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.appointment.RejectedSuggestion;
import org.springframework.samples.petclinic.appointment.RejectedSuggestionRepository;
import org.springframework.samples.petclinic.appointment.SlotHold;
import org.springframework.samples.petclinic.appointment.SlotHoldRepository;
import org.springframework.samples.petclinic.calendar.ClinicSettings;
import org.springframework.samples.petclinic.calendar.ClinicSettingsRepository;
import org.springframework.samples.petclinic.calendar.GridGenerator;
import org.springframework.samples.petclinic.calendar.SlotCandidate;
import org.springframework.samples.petclinic.vet.Specialty;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchedulingCandidateService {

	private final ClinicSettingsRepository clinicSettingsRepository;

	private final GridGenerator gridGenerator;

	private final AppointmentRepository appointmentRepository;

	private final SlotHoldRepository slotHoldRepository;

	private final RejectedSuggestionRepository rejectedSuggestionRepository;

	private final VetRepository vetRepository;

	private final Clock clock;

	public SchedulingCandidateService(ClinicSettingsRepository clinicSettingsRepository, GridGenerator gridGenerator,
			AppointmentRepository appointmentRepository, SlotHoldRepository slotHoldRepository,
			RejectedSuggestionRepository rejectedSuggestionRepository, VetRepository vetRepository, Clock clock) {
		this.clinicSettingsRepository = clinicSettingsRepository;
		this.gridGenerator = gridGenerator;
		this.appointmentRepository = appointmentRepository;
		this.slotHoldRepository = slotHoldRepository;
		this.rejectedSuggestionRepository = rejectedSuggestionRepository;
		this.vetRepository = vetRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<RankedSlot> findFeasibleCandidates(Integer requestId, SchedulingCriteria criteria) {
		ClinicSettings settings = this.clinicSettingsRepository.getClinicSettings();
		int duration = settings.clampDuration(criteria.durationMin());
		Instant now = this.clock.instant();
		LocalDate today = LocalDate.now(this.clock.withZone(settings.getZone()));
		Set<CandidateKey> rejected = rejectedKeys(requestId);
		List<RankedSlot> feasible = new ArrayList<>();

		for (Vet vet : this.vetRepository.findAll()) {
			if (!hasRequiredSpecialty(vet, criteria.requiredSpecialty())) {
				continue;
			}
			List<Appointment> appointments = this.appointmentRepository.findByVetIdAndStatusNot(vet.getId(),
					AppointmentStatus.CANCELLED);
			List<SlotHold> holds = this.slotHoldRepository.findActiveByVetId(vet.getId(), now);
			for (SlotCandidate candidate : this.gridGenerator.generateCandidateSlots(vet.getId(), today, duration,
					settings)) {
				Instant start = candidate.startInstant();
				if (start.isBefore(now) || isExcluded(start, criteria.excludedWindows())
						|| rejected.contains(new CandidateKey(vet.getId(), start))
						|| overlapsAppointment(start, duration, appointments) || overlapsHold(start, duration, holds)) {
					continue;
				}
				int windowRank = contains(criteria.preferredWindows(), start) ? 2
						: contains(criteria.allowedWindows(), start) ? 1 : 0;
				feasible.add(new RankedSlot(vet.getId(), start, windowRank,
						criteria.preferredVetId() != null && criteria.preferredVetId().equals(vet.getId())));
			}
		}

		return List.copyOf(feasible);
	}

	@Transactional(readOnly = true)
	public boolean hasMatchingSpecialty(String requiredSpecialty) {
		return this.vetRepository.findAll().stream().anyMatch(vet -> hasRequiredSpecialty(vet, requiredSpecialty));
	}

	private Set<CandidateKey> rejectedKeys(Integer requestId) {
		Set<CandidateKey> rejected = new HashSet<>();
		for (RejectedSuggestion suggestion : this.rejectedSuggestionRepository.findByRequestId(requestId)) {
			rejected.add(new CandidateKey(suggestion.getVet().getId(), suggestion.getStartInstant()));
		}
		return rejected;
	}

	private static boolean hasRequiredSpecialty(Vet vet, String requiredSpecialty) {
		if (requiredSpecialty == null || requiredSpecialty.isBlank()) {
			return true;
		}
		return vet.getSpecialties().stream().map(Specialty::getName).anyMatch(requiredSpecialty::equalsIgnoreCase);
	}

	private static boolean isExcluded(Instant start, List<SchedulingWindow> excludedWindows) {
		return contains(excludedWindows, start);
	}

	private static boolean contains(List<SchedulingWindow> windows, Instant start) {
		return windows.stream().anyMatch(window -> window.contains(start));
	}

	private static boolean overlapsAppointment(Instant start, int duration, List<Appointment> appointments) {
		return appointments.stream().anyMatch(appointment -> appointment.overlaps(start, duration));
	}

	private static boolean overlapsHold(Instant start, int duration, List<SlotHold> holds) {
		Instant end = start.plus(Duration.ofMinutes(duration));
		return holds.stream().anyMatch(hold -> {
			Instant holdEnd = hold.getStartInstant().plus(Duration.ofMinutes(hold.getDurationMin()));
			return start.isBefore(holdEnd) && end.isAfter(hold.getStartInstant());
		});
	}

	private record CandidateKey(Integer vetId, Instant start) {
	}

}
