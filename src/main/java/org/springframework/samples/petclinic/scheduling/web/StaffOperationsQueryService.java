/*
 * Copyright 2012-2026 the original author or authors.
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

package org.springframework.samples.petclinic.scheduling.web;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.interpretation.Interpretation;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only application boundary for the staff request and booking pages.
 */
@Service
public class StaffOperationsQueryService {

	private final SchedulingRequestRepository requestRepository;

	private final VetRepository vetRepository;

	private final OwnerRepository ownerRepository;

	private final InterpretationRepository interpretationRepository;

	public StaffOperationsQueryService(SchedulingRequestRepository requestRepository, VetRepository vetRepository,
			OwnerRepository ownerRepository, InterpretationRepository interpretationRepository) {
		this.requestRepository = requestRepository;
		this.vetRepository = vetRepository;
		this.ownerRepository = ownerRepository;
		this.interpretationRepository = interpretationRepository;
	}

	@Transactional(readOnly = true)
	public SchedulingRequest getRequest(Integer requestId) {
		return this.requestRepository.findById(requestId)
			.orElseThrow(() -> new IllegalArgumentException("SchedulingRequest not found: " + requestId));
	}

	@Transactional(readOnly = true)
	public SchedulingRequest findRequestForBooking(Integer requestId, Integer petId) {
		if (requestId != null) {
			return this.requestRepository.findById(requestId).orElse(null);
		}
		if (petId != null) {
			return this.requestRepository.findByActivePetId(petId).orElse(null);
		}
		return null;
	}

	@Transactional(readOnly = true)
	public Collection<Vet> findVets() {
		return this.vetRepository.findAll();
	}

	@Transactional(readOnly = true)
	public Vet getVet(Integer vetId) {
		return this.vetRepository.findById(vetId)
			.orElseThrow(() -> new IllegalArgumentException("Vet not found: " + vetId));
	}

	@Transactional(readOnly = true)
	public List<Pet> findPets() {
		return this.ownerRepository.findAll().stream().flatMap(owner -> owner.getPets().stream()).toList();
	}

	@Transactional(readOnly = true)
	public Optional<Interpretation> getLatestInterpretation(Integer requestId) {
		return this.interpretationRepository.findTopByRequestIdOrderByVersionDesc(requestId);
	}

}
