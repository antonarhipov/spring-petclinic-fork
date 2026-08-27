/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.appointment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.samples.petclinic.calendar.ClinicSettings;
import org.springframework.samples.petclinic.calendar.ClinicSettingsRepository;
import org.springframework.samples.petclinic.scheduling.solver.RankedSlot;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SlotHoldAcquisitionService {

	private final SlotHoldRepository slotHoldRepository;

	private final AppointmentRequestRepository requestRepository;

	private final AppointmentRepository appointmentRepository;

	private final ClinicSettingsRepository clinicSettingsRepository;

	private final VetRepository vetRepository;

	private final Clock clock;

	public SlotHoldAcquisitionService(SlotHoldRepository slotHoldRepository,
			AppointmentRequestRepository requestRepository, AppointmentRepository appointmentRepository,
			ClinicSettingsRepository clinicSettingsRepository, VetRepository vetRepository, Clock clock) {
		this.slotHoldRepository = slotHoldRepository;
		this.requestRepository = requestRepository;
		this.appointmentRepository = appointmentRepository;
		this.clinicSettingsRepository = clinicSettingsRepository;
		this.vetRepository = vetRepository;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public SlotHold acquire(Integer requestId, RankedSlot slot, int requestedDurationMin) {
		Instant now = this.clock.instant();
		Vet vet = this.vetRepository.findByIdForUpdate(slot.vetId())
			.orElseThrow(() -> new IllegalArgumentException("Vet not found with id: " + slot.vetId()));
		AppointmentRequest request = this.requestRepository.findById(requestId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment request not found with id: " + requestId));
		ClinicSettings settings = this.clinicSettingsRepository.getClinicSettings();
		int duration = settings.clampDuration(requestedDurationMin);

		if (this.appointmentRepository.findByVetIdAndStatusNot(vet.getId(), AppointmentStatus.CANCELLED)
			.stream()
			.anyMatch(appointment -> appointment.overlaps(slot.startInstant(), duration))
				|| this.slotHoldRepository.findActiveByVetId(vet.getId(), now)
					.stream()
					.anyMatch(hold -> overlaps(slot.startInstant(), duration, hold))) {
			throw new SlotUnavailableException();
		}

		SlotHold hold = new SlotHold();
		hold.setVet(vet);
		hold.setStartInstant(slot.startInstant());
		hold.setDurationMin(duration);
		hold.setRequest(request);
		hold.setExpiresAt(now.plus(Duration.ofMinutes(settings.getHoldDurationMin())));
		try {
			return this.slotHoldRepository.saveAndFlush(hold);
		}
		catch (DataIntegrityViolationException ex) {
			throw new SlotUnavailableException(ex);
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void release(Integer holdId) {
		this.slotHoldRepository.deleteById(holdId);
	}

	private static boolean overlaps(Instant start, int duration, SlotHold hold) {
		Instant end = start.plus(Duration.ofMinutes(duration));
		Instant holdEnd = hold.getStartInstant().plus(Duration.ofMinutes(hold.getDurationMin()));
		return start.isBefore(holdEnd) && end.isAfter(hold.getStartInstant());
	}

}
