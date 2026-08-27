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
import java.util.Optional;

import org.springframework.samples.petclinic.scheduling.solver.RankedSlot;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentRequestWorkflowService {

	private final AppointmentRequestRepository requestRepository;

	private final AppointmentRepository appointmentRepository;

	private final SlotHoldRepository slotHoldRepository;

	private final RejectedSuggestionRepository rejectedSuggestionRepository;

	private final VetRepository vetRepository;

	private final Clock clock;

	public AppointmentRequestWorkflowService(AppointmentRequestRepository requestRepository,
			AppointmentRepository appointmentRepository, SlotHoldRepository slotHoldRepository,
			RejectedSuggestionRepository rejectedSuggestionRepository, VetRepository vetRepository, Clock clock) {
		this.requestRepository = requestRepository;
		this.appointmentRepository = appointmentRepository;
		this.slotHoldRepository = slotHoldRepository;
		this.rejectedSuggestionRepository = rejectedSuggestionRepository;
		this.vetRepository = vetRepository;
		this.clock = clock;
	}

	@Transactional
	public void beginSuggestion(Integer requestId) {
		AppointmentRequest request = getForUpdate(requestId);
		if (request.getStatus() == AppointmentRequestStatus.CONFIRMED) {
			request.setStatus(AppointmentRequestStatus.SUGGESTING);
		}
		else if (request.getStatus() != AppointmentRequestStatus.SUGGESTING) {
			throw invalidTransition(request, "begin suggestion");
		}
	}

	@Transactional
	public void attachHold(Integer requestId, SlotHold hold) {
		AppointmentRequest request = getForUpdate(requestId);
		if (request.getStatus() != AppointmentRequestStatus.SUGGESTING
				&& request.getStatus() != AppointmentRequestStatus.HELD) {
			throw invalidTransition(request, "attach hold");
		}
		request.offer(hold.getVet(), hold.getStartInstant(), hold.getId());
	}

	@Transactional(readOnly = true)
	public CurrentOffer getCurrentOffer(Integer requestId) {
		AppointmentRequest request = this.requestRepository.findById(requestId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment request not found with id: " + requestId));
		if (request.getStatus() != AppointmentRequestStatus.HELD || request.getSuggestedVet() == null
				|| request.getSuggestedStartInstant() == null) {
			throw invalidTransition(request, "read current offer");
		}
		return new CurrentOffer(request.getSuggestedVet().getId(), request.getSuggestedStartInstant(),
				request.getActiveHoldId());
	}

	@Transactional
	public void rejectCurrent(Integer requestId) {
		AppointmentRequest request = getForUpdate(requestId);
		if (request.getStatus() != AppointmentRequestStatus.HELD || request.getSuggestedVet() == null
				|| request.getSuggestedStartInstant() == null) {
			throw invalidTransition(request, "reject suggestion");
		}

		RejectedSuggestion rejected = new RejectedSuggestion();
		rejected.setRequest(request);
		rejected.setVet(request.getSuggestedVet());
		rejected.setStartInstant(request.getSuggestedStartInstant());
		this.rejectedSuggestionRepository.save(rejected);

		Integer holdId = request.getActiveHoldId();
		request.clearSuggestion();
		request.setStatus(AppointmentRequestStatus.SUGGESTING);
		this.requestRepository.saveAndFlush(request);
		if (holdId != null) {
			this.slotHoldRepository.deleteById(holdId);
		}
	}

	@Transactional
	public void markNoFit(Integer requestId) {
		AppointmentRequest request = getForUpdate(requestId);
		if (request.getStatus() != AppointmentRequestStatus.SUGGESTING) {
			throw invalidTransition(request, "queue request");
		}
		request.clearSuggestion();
		request.setStatus(AppointmentRequestStatus.QUEUED_FOR_STAFF);
	}

	@Transactional
	public void markSuggestionLost(Integer requestId) {
		AppointmentRequest request = getForUpdate(requestId);
		if (request.getStatus() != AppointmentRequestStatus.HELD) {
			throw invalidTransition(request, "lose suggestion");
		}
		Integer holdId = request.getActiveHoldId();
		request.clearSuggestion();
		request.setStatus(AppointmentRequestStatus.SUGGESTING);
		this.requestRepository.saveAndFlush(request);
		if (holdId != null) {
			this.slotHoldRepository.deleteById(holdId);
		}
	}

	@Transactional
	public Optional<Appointment> confirmHeld(Integer requestId) {
		AppointmentRequest request = getForUpdate(requestId);
		if (request.getStatus() != AppointmentRequestStatus.HELD || request.getActiveHoldId() == null) {
			throw invalidTransition(request, "accept suggestion");
		}
		SlotHold hold = this.slotHoldRepository.findById(request.getActiveHoldId()).orElse(null);
		if (hold == null || !hold.getRequest().getId().equals(requestId)
				|| !hold.getExpiresAt().isAfter(this.clock.instant())) {
			return Optional.empty();
		}

		if (slotTakenByAppointment(hold) || slotTakenByAnotherHold(hold)) {
			releaseAndResume(request, hold.getId());
			return Optional.empty();
		}

		Appointment appointment = new Appointment();
		appointment.setRequest(request);
		appointment.setPet(request.getPet());
		appointment.setVet(hold.getVet());
		appointment.setStartInstant(hold.getStartInstant());
		appointment.setDurationMin(hold.getDurationMin());
		appointment.setStatus(AppointmentStatus.SCHEDULED);
		this.appointmentRepository.saveAndFlush(appointment);

		Integer holdId = hold.getId();
		request.clearSuggestion();
		request.setResultingAppointment(appointment);
		request.setStatus(AppointmentRequestStatus.SCHEDULED);
		this.requestRepository.saveAndFlush(request);
		this.slotHoldRepository.deleteById(holdId);
		return Optional.of(appointment);
	}

	private boolean slotTakenByAppointment(SlotHold hold) {
		return this.appointmentRepository.findByVetIdAndStatusNot(hold.getVet().getId(), AppointmentStatus.CANCELLED)
			.stream()
			.anyMatch(appointment -> appointment.overlaps(hold.getStartInstant(), hold.getDurationMin()));
	}

	private boolean slotTakenByAnotherHold(SlotHold hold) {
		Instant start = hold.getStartInstant();
		Instant end = start.plus(Duration.ofMinutes(hold.getDurationMin()));
		return this.slotHoldRepository.findActiveByVetId(hold.getVet().getId(), this.clock.instant())
			.stream()
			.filter(other -> !other.getId().equals(hold.getId()))
			.anyMatch(other -> {
				Instant otherEnd = other.getStartInstant().plus(Duration.ofMinutes(other.getDurationMin()));
				return start.isBefore(otherEnd) && end.isAfter(other.getStartInstant());
			});
	}

	private void releaseAndResume(AppointmentRequest request, Integer holdId) {
		request.clearSuggestion();
		request.setStatus(AppointmentRequestStatus.SUGGESTING);
		this.requestRepository.saveAndFlush(request);
		this.slotHoldRepository.deleteById(holdId);
	}

	private AppointmentRequest getForUpdate(Integer requestId) {
		return this.requestRepository.findByIdForUpdate(requestId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment request not found with id: " + requestId));
	}

	private static IllegalStateException invalidTransition(AppointmentRequest request, String action) {
		return new IllegalStateException("Cannot " + action + " while request is " + request.getStatus());
	}

	public record CurrentOffer(Integer vetId, Instant startInstant, Integer activeHoldId) {

		public RankedSlot asRankedSlot() {
			return new RankedSlot(this.vetId, this.startInstant, 0, false);
		}

	}

}
