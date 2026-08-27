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
import java.util.List;

import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerAppointmentService {

	private static final Duration CANCELLATION_CUTOFF = Duration.ofHours(24);

	private final AppointmentRepository appointmentRepository;

	private final AppointmentRequestRepository requestRepository;

	private final OwnerRepository ownerRepository;

	private final Clock clock;

	public OwnerAppointmentService(AppointmentRepository appointmentRepository,
			AppointmentRequestRepository requestRepository, OwnerRepository ownerRepository, Clock clock) {
		this.appointmentRepository = appointmentRepository;
		this.requestRepository = requestRepository;
		this.ownerRepository = ownerRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<Appointment> getUpcoming(Integer ownerId) {
		Owner owner = getOwner(ownerId);
		List<Integer> petIds = owner.getPets().stream().map(pet -> pet.getId()).toList();
		if (petIds.isEmpty()) {
			return List.of();
		}
		Instant now = this.clock.instant();
		return this.appointmentRepository.findByPetIds(petIds)
			.stream()
			.filter(appointment -> appointment.getStatus() == AppointmentStatus.SCHEDULED
					&& appointment.getStartInstant().isAfter(now))
			.toList();
	}

	@Transactional(readOnly = true)
	public List<AppointmentRequest> getActiveRequests(Integer ownerId) {
		getOwner(ownerId);
		return this.requestRepository.findByOwnerId(ownerId)
			.stream()
			.filter(request -> request.getStatus() != AppointmentRequestStatus.SCHEDULED
					&& request.getStatus() != AppointmentRequestStatus.CANCELLED
					&& request.getStatus() != AppointmentRequestStatus.EXPIRED)
			.toList();
	}

	@Transactional
	public void cancel(Integer ownerId, Integer appointmentId) {
		Owner owner = getOwner(ownerId);
		Appointment appointment = this.appointmentRepository.findByIdForUpdate(appointmentId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found with id: " + appointmentId));
		if (owner.getPet(appointment.getPet().getId()) == null) {
			throw new IllegalArgumentException("Appointment does not belong to this owner");
		}
		if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {
			throw new IllegalStateException("Only scheduled appointments can be cancelled");
		}
		if (!appointment.getStartInstant().isAfter(this.clock.instant().plus(CANCELLATION_CUTOFF))) {
			throw new IllegalStateException(
					"Appointments within 24 hours cannot be cancelled online; please contact clinic staff");
		}
		appointment.setReason("Cancelled by owner");
		appointment.setStatus(AppointmentStatus.CANCELLED);
	}

	private Owner getOwner(Integer ownerId) {
		return this.ownerRepository.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found with id: " + ownerId));
	}

}
