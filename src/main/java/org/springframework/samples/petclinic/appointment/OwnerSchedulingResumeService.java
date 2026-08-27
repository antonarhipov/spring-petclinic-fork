/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.appointment;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.samples.petclinic.calendar.ClinicSettings;
import org.springframework.samples.petclinic.calendar.ClinicSettingsRepository;
import org.springframework.samples.petclinic.calendar.EffectiveAvailabilityResolver;
import org.springframework.samples.petclinic.calendar.InstantInterval;
import org.springframework.samples.petclinic.scheduling.interpretation.AppointmentInterpretation;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationWindow;
import org.springframework.samples.petclinic.scheduling.solver.MatchingCoordinator;
import org.springframework.samples.petclinic.scheduling.solver.SchedulingCriteria;
import org.springframework.samples.petclinic.scheduling.solver.SchedulingWindow;
import org.springframework.samples.petclinic.scheduling.solver.SuggestionResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

@Service
public class OwnerSchedulingResumeService {

	private final AppointmentRequestRepository requestRepository;

	private final ClinicSettingsRepository settingsRepository;

	private final MatchingCoordinator matchingCoordinator;

	private final JsonMapper jsonMapper;

	private final Clock clock;

	public OwnerSchedulingResumeService(AppointmentRequestRepository requestRepository,
			ClinicSettingsRepository settingsRepository, MatchingCoordinator matchingCoordinator, JsonMapper jsonMapper,
			Clock clock) {
		this.requestRepository = requestRepository;
		this.settingsRepository = settingsRepository;
		this.matchingCoordinator = matchingCoordinator;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
	}

	@Transactional
	public SuggestionResult resume(Integer ownerId, Integer requestId) {
		AppointmentRequest request = this.requestRepository.findById(requestId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment request not found with id: " + requestId));
		if (!request.getOwner().getId().equals(ownerId)) {
			throw new IllegalArgumentException("Appointment request does not belong to this owner");
		}
		if (request.getStatus() != AppointmentRequestStatus.SUGGESTING) {
			throw new IllegalStateException("Appointment request is not ready to resume");
		}
		if (request.getInterpretationJson() == null || request.getInterpretationJson().isBlank()) {
			throw new IllegalStateException("Appointment request has no valid interpretation");
		}

		AppointmentInterpretation interpretation = this.jsonMapper.readValue(request.getInterpretationJson(),
				AppointmentInterpretation.class);
		return this.matchingCoordinator.suggest(requestId, toCriteria(interpretation));
	}

	SchedulingCriteria toCriteria(AppointmentInterpretation interpretation) {
		ClinicSettings settings = this.settingsRepository.getClinicSettings();
		return new SchedulingCriteria(settings.clampDuration(interpretation.estimatedDurationMin()),
				interpretation.requiredSpecialty(), interpretation.preferredVetId(),
				expand(interpretation.preferred(), settings), expand(interpretation.allowed(), settings),
				expand(interpretation.excluded(), settings));
	}

	private List<SchedulingWindow> expand(List<InterpretationWindow> windows, ClinicSettings settings) {
		LocalDate startDate = LocalDate.now(this.clock.withZone(settings.getZone()));
		LocalDate endDate = startDate.plusDays(settings.getBookingHorizonDays());
		List<SchedulingWindow> expanded = new ArrayList<>();
		for (LocalDate date = startDate; date.isBefore(endDate); date = date.plusDays(1)) {
			for (InterpretationWindow window : windows) {
				if (window.dayOfWeek() == date.getDayOfWeek()) {
					InstantInterval interval = EffectiveAvailabilityResolver.toInstantInterval(date, window.start(),
							window.end(), settings.getZone());
					expanded.add(new SchedulingWindow(interval.start(), interval.end()));
				}
			}
		}
		return List.copyOf(expanded);
	}

}
