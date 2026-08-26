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

package org.springframework.samples.petclinic.scheduling;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.samples.petclinic.clinic.AvailabilityService;
import org.springframework.samples.petclinic.scheduling.ai.AppointmentInterpreter;
import org.springframework.samples.petclinic.scheduling.ai.Interpretation;
import org.springframework.samples.petclinic.scheduling.ai.UrgencyLevel;
import org.springframework.samples.petclinic.scheduling.model.QueueReason;
import org.springframework.samples.petclinic.scheduling.model.RequestState;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.solver.AppointmentSolverService;
import org.springframework.samples.petclinic.scheduling.solver.CandidateSlot;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class SchedulingOrchestrator {

	private static final Logger log = LoggerFactory.getLogger(SchedulingOrchestrator.class);

	private final SchedulingRequestRepository schedulingRequests;

	private final AppointmentInterpreter interpreter;

	private final AppointmentSolverService solverService;

	private final HoldService holdService;

	private final VetRepository vetRepository;

	private final AvailabilityService availabilityService;

	private final ObjectMapper objectMapper;

	public SchedulingOrchestrator(SchedulingRequestRepository schedulingRequests, AppointmentInterpreter interpreter,
			AppointmentSolverService solverService, HoldService holdService, VetRepository vetRepository,
			AvailabilityService availabilityService, ObjectMapper objectMapper) {
		this.schedulingRequests = schedulingRequests;
		this.interpreter = interpreter;
		this.solverService = solverService;
		this.holdService = holdService;
		this.vetRepository = vetRepository;
		this.availabilityService = availabilityService;
		this.objectMapper = objectMapper;
	}

	@EventListener
	public void onHoldExpired(HoldExpiredEvent event) {
		if (event != null && event.requestId() != null) {
			processSolveAsync(event.requestId());
		}
	}

	/**
	 * Runs the AI interpretation for a scheduling request.
	 *
	 * <p>
	 * The (potentially slow) AI call is deliberately performed <em>outside</em> any
	 * database transaction. The state transitions before and after the call are each
	 * committed in their own transactional step ({@link #beginInterpretation} /
	 * {@link #finalizeInterpretation}), so the {@code INTERPRETING} state - and the final
	 * outcome - become visible to status pollers immediately. Previously the whole method
	 * was {@code @Transactional}, so a single long-lived transaction spanned the AI call
	 * and only committed when the method returned; until then pollers still observed the
	 * last committed state and the owner status page could not advance the spinner or
	 * swap in the final screen (e.g. {@code STAFF_QUEUED}) when the AI call failed.
	 */
	@Async("schedulingTaskExecutor")
	public void processInterpretationAsync(Integer requestId) {
		InterpretationStart start = beginInterpretation(requestId);
		if (!start.proceed()) {
			return;
		}

		Optional<Interpretation> interpretationOpt;
		try {
			interpretationOpt = this.interpreter.interpret(start.rawText());
		}
		catch (Exception ex) {
			log.warn("Exception during interpretation of request {}: {}", requestId, ex.getMessage());
			interpretationOpt = Optional.empty();
		}

		finalizeInterpretation(requestId, interpretationOpt);
	}

	/**
	 * Marks the request as {@code INTERPRETING} - committing that transition immediately
	 * - and returns the raw text to interpret. Short-circuits (returning a non-proceeding
	 * result) when the request no longer needs interpretation (missing, terminal, already
	 * awaiting confirmation) or when AI consent was declined, in which case it is routed
	 * straight to the staff queue.
	 */
	@Transactional
	public InterpretationStart beginInterpretation(Integer requestId) {
		Optional<SchedulingRequest> optionalRequest = this.schedulingRequests.findById(requestId);
		if (optionalRequest.isEmpty()) {
			log.warn("SchedulingRequest {} not found for interpretation", requestId);
			return InterpretationStart.stop();
		}

		SchedulingRequest request = optionalRequest.get();
		if (request.getState().isTerminal() || request.getState() == RequestState.AWAITING_CONFIRMATION) {
			return InterpretationStart.stop();
		}

		if (!request.isAiConsent()) {
			request.transitionTo(RequestState.STAFF_QUEUED, QueueReason.CONSENT_DECLINED);
			this.schedulingRequests.saveAndFlush(request);
			return InterpretationStart.stop();
		}

		request.transitionTo(RequestState.INTERPRETING, null);
		this.schedulingRequests.saveAndFlush(request);
		return InterpretationStart.proceed(request.getRawText());
	}

	/**
	 * Persists the interpretation outcome in its own transaction, transitioning the
	 * request to {@code AWAITING_CONFIRMATION} (or {@code STAFF_QUEUED} for emergencies /
	 * AI failures). Only requests still in the {@code INTERPRETING} state are finalized,
	 * so a concurrent cancellation is never clobbered.
	 */
	@Transactional
	public void finalizeInterpretation(Integer requestId, Optional<Interpretation> interpretationOpt) {
		Optional<SchedulingRequest> optionalRequest = this.schedulingRequests.findById(requestId);
		if (optionalRequest.isEmpty()) {
			log.warn("SchedulingRequest {} not found while finalizing interpretation", requestId);
			return;
		}

		SchedulingRequest request = optionalRequest.get();
		if (request.getState() != RequestState.INTERPRETING) {
			return;
		}

		if (interpretationOpt.isEmpty()) {
			request.transitionTo(RequestState.STAFF_QUEUED, QueueReason.AI_UNAVAILABLE);
		}
		else {
			Interpretation interpretation = interpretationOpt.get();
			try {
				request.setInterpretationJson(this.objectMapper.writeValueAsString(interpretation));
			}
			catch (Exception e) {
				log.error("Failed to serialize interpretation to JSON", e);
			}

			if (interpretation.urgency() == UrgencyLevel.EMERGENCY) {
				request.transitionTo(RequestState.STAFF_QUEUED, QueueReason.EMERGENCY);
			}
			else {
				request.transitionTo(RequestState.AWAITING_CONFIRMATION, null);
			}
		}

		this.schedulingRequests.saveAndFlush(request);
	}

	/**
	 * Outcome of {@link #beginInterpretation}: whether to proceed with the AI call and,
	 * if so, the raw text to interpret.
	 */
	public record InterpretationStart(boolean proceed, String rawText) {

		static InterpretationStart stop() {
			return new InterpretationStart(false, null);
		}

		static InterpretationStart proceed(String rawText) {
			return new InterpretationStart(true, rawText == null ? "" : rawText);
		}

	}

	@Async("schedulingTaskExecutor")
	@Transactional
	public void processSolveAsync(Integer requestId) {
		Optional<SchedulingRequest> optionalRequest = this.schedulingRequests.findById(requestId);
		if (optionalRequest.isEmpty()) {
			log.warn("SchedulingRequest {} not found for solving", requestId);
			return;
		}

		SchedulingRequest request = optionalRequest.get();
		if (request.getState().isTerminal() || request.getState() == RequestState.CONFIRMED
				|| request.getState() == RequestState.STAFF_QUEUED) {
			return;
		}

		Optional<Interpretation> interpretationOpt = getInterpretation(request);
		if (interpretationOpt.isEmpty()) {
			request.transitionTo(RequestState.STAFF_QUEUED, QueueReason.AI_UNAVAILABLE);
			this.schedulingRequests.saveAndFlush(request);
			return;
		}

		Interpretation interpretation = interpretationOpt.get();

		if (interpretation.requiredSpecialty() != null && !interpretation.requiredSpecialty().isBlank()) {
			String required = interpretation.requiredSpecialty().trim().toLowerCase();
			java.util.Collection<Vet> allVets = this.vetRepository.findAll();
			boolean hasSpecialist = allVets.stream()
				.anyMatch(vet -> vet.getSpecialties()
					.stream()
					.anyMatch(s -> s.getName() != null && s.getName().toLowerCase().contains(required)));
			if (!hasSpecialist) {
				request.transitionTo(RequestState.STAFF_QUEUED, QueueReason.NO_SPECIALTY_VET);
				this.schedulingRequests.saveAndFlush(request);
				return;
			}
		}

		try {
			Optional<CandidateSlot> bestSlotOpt = this.solverService.findBestSlot(request, interpretation);
			if (bestSlotOpt.isEmpty()) {
				request.transitionTo(RequestState.STAFF_QUEUED, QueueReason.SUGGESTIONS_EXHAUSTED);
				this.schedulingRequests.saveAndFlush(request);
				return;
			}

			CandidateSlot slot = bestSlotOpt.get();
			Vet vet = this.vetRepository.findById(slot.getVetId()).orElse(null);
			if (vet == null) {
				request.transitionTo(RequestState.STAFF_QUEUED, QueueReason.SOLVER_UNAVAILABLE);
				this.schedulingRequests.saveAndFlush(request);
				return;
			}

			Duration holdDuration = Duration
				.ofMinutes(this.availabilityService.getClinicSettings().getHoldDurationMinutes());
			this.holdService.placeHold(request, vet, slot.getStartTime(), slot.getEndTime(), holdDuration);
		}
		catch (Exception ex) {
			log.error("Error during solver processing for request {}: {}", requestId, ex.getMessage(), ex);
			request.transitionTo(RequestState.STAFF_QUEUED, QueueReason.SOLVER_UNAVAILABLE);
			this.schedulingRequests.saveAndFlush(request);
		}
	}

	public Optional<Interpretation> getInterpretation(SchedulingRequest request) {
		if (request.getInterpretationJson() == null || request.getInterpretationJson().isBlank()) {
			return Optional.empty();
		}
		try {
			return Optional
				.ofNullable(this.objectMapper.readValue(request.getInterpretationJson(), Interpretation.class));
		}
		catch (Exception e) {
			log.error("Failed to deserialize interpretation JSON", e);
			return Optional.empty();
		}
	}

}
