/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.staff;

import java.util.Comparator;
import java.util.List;

import org.springframework.samples.petclinic.appointment.AppointmentRequest;
import org.springframework.samples.petclinic.appointment.AppointmentRequestRepository;
import org.springframework.samples.petclinic.appointment.AppointmentRequestStatus;
import org.springframework.samples.petclinic.appointment.AppointmentRequestWorkflowService;
import org.springframework.samples.petclinic.appointment.FallbackReason;
import org.springframework.samples.petclinic.scheduling.interpretation.AppointmentInterpretation;
import org.springframework.samples.petclinic.scheduling.interpretation.Urgency;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

@Service
public class StaffFallbackService {

	private final AppointmentRequestRepository requestRepository;

	private final AppointmentRequestWorkflowService workflowService;

	private final JsonMapper jsonMapper;

	public StaffFallbackService(AppointmentRequestRepository requestRepository,
			AppointmentRequestWorkflowService workflowService, JsonMapper jsonMapper) {
		this.requestRepository = requestRepository;
		this.workflowService = workflowService;
		this.jsonMapper = jsonMapper;
	}

	@Transactional(readOnly = true)
	public List<QueueItem> getQueue() {
		return this.requestRepository.findByStatus(AppointmentRequestStatus.QUEUED_FOR_STAFF)
			.stream()
			.map(this::toQueueItem)
			.sorted(Comparator.comparing(QueueItem::suspectedEmergency).reversed().thenComparing(QueueItem::requestId))
			.toList();
	}

	@Transactional(readOnly = true)
	public QueueItem getQueuedRequest(Integer requestId) {
		AppointmentRequest request = this.requestRepository.findById(requestId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment request not found with id: " + requestId));
		if (request.getStatus() != AppointmentRequestStatus.QUEUED_FOR_STAFF) {
			throw new IllegalStateException("Appointment request is not in the staff queue");
		}
		return toQueueItem(request);
	}

	public void unblock(Integer requestId) {
		this.workflowService.unblock(requestId);
	}

	private QueueItem toQueueItem(AppointmentRequest request) {
		return new QueueItem(request.getId(), request.getOwner().getId(),
				request.getOwner().getFirstName() + " " + request.getOwner().getLastName(), request.getPet().getId(),
				request.getPet().getName(), request.getFreeText(), request.getInterpretationJson(),
				request.getFallbackReason(), isSuspectedEmergency(request.getInterpretationJson()));
	}

	private boolean isSuspectedEmergency(String interpretationJson) {
		if (interpretationJson == null || interpretationJson.isBlank()) {
			return false;
		}
		try {
			AppointmentInterpretation interpretation = this.jsonMapper.readValue(interpretationJson,
					AppointmentInterpretation.class);
			return interpretation.urgency() == Urgency.SUSPECTED_EMERGENCY;
		}
		catch (RuntimeException ex) {
			return false;
		}
	}

	public record QueueItem(Integer requestId, Integer ownerId, String ownerName, Integer petId, String petName,
			String freeText, String interpretationJson, FallbackReason fallbackReason, boolean suspectedEmergency) {
	}

}
