/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 */

package org.springframework.samples.petclinic.scheduling.web;

import java.util.List;

import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service-layer owner scope guard used independently of URL authorization. */
@Service
public class OwnerSchedulingAccessService {

	private final OwnerRepository ownerRepository;

	private final SchedulingRequestRepository requestRepository;

	private final AppointmentRepository appointmentRepository;

	public OwnerSchedulingAccessService(OwnerRepository ownerRepository, SchedulingRequestRepository requestRepository,
			AppointmentRepository appointmentRepository) {
		this.ownerRepository = ownerRepository;
		this.requestRepository = requestRepository;
		this.appointmentRepository = appointmentRepository;
	}

	@Transactional(readOnly = true)
	public Owner requireOwner(Integer ownerId) {
		return this.ownerRepository.findById(ownerId).orElseThrow(OwnerResourceNotFoundException::new);
	}

	@Transactional(readOnly = true)
	public Pet requirePet(Integer ownerId, Integer petId) {
		Pet pet = requireOwner(ownerId).getPet(petId);
		if (pet == null) {
			throw new OwnerResourceNotFoundException();
		}
		return pet;
	}

	@Transactional(readOnly = true)
	public SchedulingRequest requireRequest(Integer ownerId, Integer requestId) {
		return this.requestRepository.findByIdAndOwnerId(requestId, ownerId)
			.orElseThrow(OwnerResourceNotFoundException::new);
	}

	@Transactional(readOnly = true)
	public Appointment requireAppointment(Integer ownerId, Integer appointmentId) {
		return this.appointmentRepository.findOwnedById(ownerId, appointmentId)
			.orElseThrow(OwnerResourceNotFoundException::new);
	}

	@Transactional(readOnly = true)
	public List<Appointment> findAppointments(Integer ownerId) {
		requireOwner(ownerId);
		return this.appointmentRepository.findAllOwnedBy(ownerId);
	}

	@Transactional(readOnly = true)
	public List<Pet> findPetsWithoutActiveRequest(Integer ownerId) {
		return requireOwner(ownerId).getPets()
			.stream()
			.filter(pet -> this.requestRepository.findByActivePetId(pet.getId()).isEmpty())
			.toList();
	}

}
