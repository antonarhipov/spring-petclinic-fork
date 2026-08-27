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
package org.springframework.samples.petclinic.appointment;

import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.interpretation.AppointmentInterpretation;
import org.springframework.samples.petclinic.scheduling.interpretation.AppointmentInterpretationService;
import org.springframework.samples.petclinic.scheduling.solver.AcceptResult;
import org.springframework.samples.petclinic.scheduling.solver.MatchingCoordinator;
import org.springframework.samples.petclinic.scheduling.solver.SchedulingCriteria;
import org.springframework.samples.petclinic.scheduling.solver.SuggestionResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

/**
 * Owner-facing orchestration over the existing guided scheduling flow. It lets an owner
 * initiate an {@link AppointmentRequest} for one of their own pets and drive the consent
 * -> interpret -> suggest -> accept/reject/ask-again loop, delegating entirely to the
 * already-implemented workflow, interpretation, and matching services. No new scheduling
 * behavior is introduced here.
 */
@Service
public class OwnerSchedulingService {

	private final OwnerRepository ownerRepository;

	private final AppointmentRequestRepository requestRepository;

	private final AppointmentRequestWorkflowService workflowService;

	private final AppointmentInterpretationService interpretationService;

	private final MatchingCoordinator matchingCoordinator;

	private final OwnerSchedulingResumeService resumeService;

	private final JsonMapper jsonMapper;

	public OwnerSchedulingService(OwnerRepository ownerRepository, AppointmentRequestRepository requestRepository,
			AppointmentRequestWorkflowService workflowService, AppointmentInterpretationService interpretationService,
			MatchingCoordinator matchingCoordinator, OwnerSchedulingResumeService resumeService,
			JsonMapper jsonMapper) {
		this.ownerRepository = ownerRepository;
		this.requestRepository = requestRepository;
		this.workflowService = workflowService;
		this.interpretationService = interpretationService;
		this.matchingCoordinator = matchingCoordinator;
		this.resumeService = resumeService;
		this.jsonMapper = jsonMapper;
	}

	/**
	 * Creates a new {@link AppointmentRequest} in {@code DRAFT} for one of the owner's
	 * own pets and submits the free text, entering the guided flow at
	 * {@code AWAITING_CONSENT}.
	 */
	@Transactional
	public AppointmentRequest startRequest(Integer ownerId, Integer petId, String freeText) {
		Owner owner = this.ownerRepository.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found with id: " + ownerId));
		Pet pet = owner.getPet(petId);
		if (pet == null) {
			throw new IllegalArgumentException("Pet with id " + petId + " not found for owner with id " + ownerId);
		}
		AppointmentRequest request = new AppointmentRequest();
		request.setOwner(owner);
		request.setPet(pet);
		request.setStatus(AppointmentRequestStatus.DRAFT);
		this.requestRepository.saveAndFlush(request);
		this.workflowService.submitFreeText(request.getId(), freeText);
		return request;
	}

	@Transactional(readOnly = true)
	public AppointmentRequest getRequest(Integer ownerId, Integer requestId) {
		AppointmentRequest request = requireOwned(ownerId, requestId);
		// Initialize the lazy associations the view needs, since open-session-in-view is
		// disabled and the entity is rendered after this transaction closes.
		if (request.getPet() != null) {
			request.getPet().getName();
		}
		if (request.getSuggestedVet() != null) {
			request.getSuggestedVet().getFirstName();
			request.getSuggestedVet().getLastName();
		}
		return request;
	}

	public void grantConsent(Integer ownerId, Integer requestId) {
		requireOwned(ownerId, requestId);
		this.interpretationService.grantConsentAndInterpret(requestId);
	}

	public void declineConsent(Integer ownerId, Integer requestId) {
		requireOwned(ownerId, requestId);
		this.interpretationService.declineConsent(requestId);
	}

	public SuggestionResult confirmAndSuggest(Integer ownerId, Integer requestId) {
		AppointmentRequest request = requireOwned(ownerId, requestId);
		this.interpretationService.confirm(requestId);
		return this.matchingCoordinator.suggest(requestId, criteriaFor(request));
	}

	public AcceptResult accept(Integer ownerId, Integer requestId) {
		AppointmentRequest request = requireOwned(ownerId, requestId);
		return this.matchingCoordinator.accept(requestId, criteriaFor(request));
	}

	public SuggestionResult reject(Integer ownerId, Integer requestId) {
		AppointmentRequest request = requireOwned(ownerId, requestId);
		return this.matchingCoordinator.rejectAndSuggestAgain(requestId, criteriaFor(request));
	}

	public SuggestionResult askAgain(Integer ownerId, Integer requestId) {
		return this.resumeService.resume(ownerId, requestId);
	}

	/**
	 * Parses the persisted interpretation for display, or {@code null} when the request
	 * has not been interpreted yet.
	 */
	@Transactional(readOnly = true)
	public AppointmentInterpretation interpretationFor(AppointmentRequest request) {
		if (request.getInterpretationJson() == null || request.getInterpretationJson().isBlank()) {
			return null;
		}
		return this.jsonMapper.readValue(request.getInterpretationJson(), AppointmentInterpretation.class);
	}

	private SchedulingCriteria criteriaFor(AppointmentRequest request) {
		AppointmentInterpretation interpretation = this.jsonMapper.readValue(request.getInterpretationJson(),
				AppointmentInterpretation.class);
		return this.resumeService.toCriteria(interpretation);
	}

	private AppointmentRequest requireOwned(Integer ownerId, Integer requestId) {
		AppointmentRequest request = this.requestRepository.findById(requestId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment request not found with id: " + requestId));
		if (request.getOwner() == null || !request.getOwner().getId().equals(ownerId)) {
			throw new IllegalArgumentException("Appointment request does not belong to this owner");
		}
		return request;
	}

}
