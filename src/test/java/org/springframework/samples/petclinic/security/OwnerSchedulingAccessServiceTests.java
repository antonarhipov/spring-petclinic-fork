/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 */

package org.springframework.samples.petclinic.security;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.web.OwnerResourceNotFoundException;
import org.springframework.samples.petclinic.scheduling.web.OwnerSchedulingAccessService;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Proves owner scoping remains enforced when MVC authorization is bypassed. */
@ExtendWith(MockitoExtension.class)
class OwnerSchedulingAccessServiceTests {

	@Mock
	private OwnerRepository ownerRepository;

	@Mock
	private SchedulingRequestRepository requestRepository;

	@Mock
	private AppointmentRepository appointmentRepository;

	private OwnerSchedulingAccessService service;

	@BeforeEach
	void setUp() {
		this.service = new OwnerSchedulingAccessService(this.ownerRepository, this.requestRepository,
				this.appointmentRepository);
	}

	@Test
	void otherOwnersPetAndMissingPetHaveTheSameOutcomeWithoutMutation() {
		Owner george = new Owner();
		george.setId(1);
		Pet leo = new Pet();
		leo.setName("Leo");
		george.addPet(leo);
		leo.setId(1);
		when(this.ownerRepository.findById(1)).thenReturn(Optional.of(george));

		assertThatThrownBy(() -> this.service.requirePet(1, 3))
			.isExactlyInstanceOf(OwnerResourceNotFoundException.class)
			.hasMessage("Resource not found");
		assertThatThrownBy(() -> this.service.requirePet(1, 9999))
			.isExactlyInstanceOf(OwnerResourceNotFoundException.class)
			.hasMessage("Resource not found");

		verify(this.ownerRepository, never()).save(org.mockito.ArgumentMatchers.any());
		verify(this.requestRepository, never()).save(org.mockito.ArgumentMatchers.any());
		verify(this.appointmentRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void requestAndAppointmentQueriesAlwaysIncludeAuthenticatedOwnerId() {
		when(this.requestRepository.findByIdAndOwnerId(77, 1)).thenReturn(Optional.empty());
		when(this.appointmentRepository.findOwnedById(1, 88)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> this.service.requireRequest(1, 77))
			.isExactlyInstanceOf(OwnerResourceNotFoundException.class);
		assertThatThrownBy(() -> this.service.requireAppointment(1, 88))
			.isExactlyInstanceOf(OwnerResourceNotFoundException.class);

		verify(this.requestRepository).findByIdAndOwnerId(77, 1);
		verify(this.appointmentRepository).findOwnedById(1, 88);
		verify(this.requestRepository, never()).findById(77);
		verify(this.appointmentRepository, never()).findById(88);
	}

}
