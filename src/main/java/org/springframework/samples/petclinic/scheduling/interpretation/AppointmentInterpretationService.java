/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.scheduling.interpretation;

import org.springframework.samples.petclinic.appointment.AppointmentRequestStatus;
import org.springframework.samples.petclinic.appointment.AppointmentRequestWorkflowService;
import org.springframework.samples.petclinic.appointment.FallbackReason;
import org.springframework.samples.petclinic.calendar.ClinicSettings;
import org.springframework.samples.petclinic.calendar.ClinicSettingsRepository;
import org.springframework.stereotype.Service;

import tools.jackson.databind.json.JsonMapper;

@Service
public class AppointmentInterpretationService {

	private final AppointmentRequestWorkflowService workflowService;

	private final AppointmentInterpreter interpreter;

	private final AppointmentInterpretationNormalizer normalizer;

	private final ClinicSettingsRepository settingsRepository;

	private final JsonMapper jsonMapper;

	public AppointmentInterpretationService(AppointmentRequestWorkflowService workflowService,
			AppointmentInterpreter interpreter, AppointmentInterpretationNormalizer normalizer,
			ClinicSettingsRepository settingsRepository, JsonMapper jsonMapper) {
		this.workflowService = workflowService;
		this.interpreter = interpreter;
		this.normalizer = normalizer;
		this.settingsRepository = settingsRepository;
		this.jsonMapper = jsonMapper;
	}

	public void submitFreeText(Integer requestId, String freeText) {
		this.workflowService.submitFreeText(requestId, freeText);
	}

	public InterpretationResult grantConsentAndInterpret(Integer requestId) {
		String consentedText = this.workflowService.recordConsent(requestId);
		AppointmentInterpretation interpreted;
		try {
			interpreted = this.interpreter.interpret(consentedText);
		}
		catch (RuntimeException ex) {
			this.workflowService.markInterpretationFailed(requestId, FallbackReason.AI_UNAVAILABLE);
			return new InterpretationResult(AppointmentRequestStatus.QUEUED_FOR_STAFF, null);
		}
		try {
			ClinicSettings settings = this.settingsRepository.getClinicSettings();
			AppointmentInterpretation normalized = this.normalizer.normalize(interpreted, settings);
			this.workflowService.completeInterpretation(requestId, this.jsonMapper.writeValueAsString(normalized));
			return new InterpretationResult(AppointmentRequestStatus.INTERPRETED, normalized);
		}
		catch (RuntimeException ex) {
			this.workflowService.markInterpretationFailed(requestId, FallbackReason.INCOMPLETE_INTERPRETATION);
			return new InterpretationResult(AppointmentRequestStatus.QUEUED_FOR_STAFF, null);
		}
	}

	public void declineConsent(Integer requestId) {
		this.workflowService.declineConsent(requestId);
	}

	public AppointmentInterpretation editStructuredFields(Integer requestId,
			AppointmentInterpretation editedInterpretation) {
		AppointmentInterpretation normalized = this.normalizer.normalize(editedInterpretation,
				this.settingsRepository.getClinicSettings());
		this.workflowService.editInterpretation(requestId, this.jsonMapper.writeValueAsString(normalized));
		return normalized;
	}

	public void confirm(Integer requestId) {
		this.workflowService.confirmInterpretation(requestId);
	}

	public record InterpretationResult(AppointmentRequestStatus status, AppointmentInterpretation interpretation) {

	}

}
