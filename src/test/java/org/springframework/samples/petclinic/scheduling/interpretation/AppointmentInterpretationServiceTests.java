/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.DayOfWeek;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.samples.petclinic.appointment.AppointmentRequestStatus;
import org.springframework.samples.petclinic.appointment.AppointmentRequestWorkflowService;
import org.springframework.samples.petclinic.appointment.FallbackReason;
import org.springframework.samples.petclinic.calendar.ClinicSettings;
import org.springframework.samples.petclinic.calendar.ClinicSettingsRepository;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AppointmentInterpretationServiceTests {

	@Mock
	private AppointmentRequestWorkflowService workflowService;

	@Mock
	private AppointmentInterpreter interpreter;

	@Mock
	private ClinicSettingsRepository settingsRepository;

	private AppointmentInterpretationService service;

	@BeforeEach
	void setUp() {
		this.service = new AppointmentInterpretationService(this.workflowService, this.interpreter,
				new AppointmentInterpretationNormalizer(), this.settingsRepository,
				JsonMapper.builder().findAndAddModules().build());
	}

	@Test
	void submittingTextOnlyArmsTheConsentGate() {
		this.service.submitFreeText(7, "My cat needs a check-up");

		verify(this.workflowService).submitFreeText(7, "My cat needs a check-up");
		verifyNoInteractions(this.interpreter);
	}

	@Test
	void consentIsRecordedBeforeTheExactSnapshotIsSentToAi() {
		ClinicSettings settings = new ClinicSettings();
		AppointmentInterpretation interpretation = interpretation(30);
		given(this.workflowService.recordConsent(7)).willReturn("exact consented text");
		given(this.interpreter.interpret("exact consented text")).willReturn(interpretation);
		given(this.settingsRepository.getClinicSettings()).willReturn(settings);

		AppointmentInterpretationService.InterpretationResult result = this.service.grantConsentAndInterpret(7);

		assertThat(result.status()).isEqualTo(AppointmentRequestStatus.INTERPRETED);
		InOrder order = inOrder(this.workflowService, this.interpreter);
		order.verify(this.workflowService).recordConsent(7);
		order.verify(this.interpreter).interpret("exact consented text");
		verify(this.workflowService).completeInterpretation(org.mockito.ArgumentMatchers.eq(7),
				org.mockito.ArgumentMatchers.contains("Europe/Amsterdam"));
	}

	@Test
	void aiOrValidationFailureRoutesToStaff() {
		given(this.workflowService.recordConsent(7)).willReturn("text");
		given(this.interpreter.interpret("text")).willThrow(new IllegalStateException("offline"));

		AppointmentInterpretationService.InterpretationResult result = this.service.grantConsentAndInterpret(7);

		assertThat(result.status()).isEqualTo(AppointmentRequestStatus.QUEUED_FOR_STAFF);
		verify(this.workflowService).markInterpretationFailed(7, FallbackReason.AI_UNAVAILABLE);
		verify(this.workflowService, never()).completeInterpretation(org.mockito.ArgumentMatchers.anyInt(),
				org.mockito.ArgumentMatchers.anyString());
	}

	@Test
	void decliningOrEditingConsentDoesNotCallAi() {
		ClinicSettings settings = new ClinicSettings();
		given(this.settingsRepository.getClinicSettings()).willReturn(settings);

		this.service.declineConsent(7);
		AppointmentInterpretation edited = this.service.editStructuredFields(8, interpretation(500));

		assertThat(edited.estimatedDurationMin()).isEqualTo(120);
		verify(this.workflowService).declineConsent(7);
		verify(this.workflowService).editInterpretation(org.mockito.ArgumentMatchers.eq(8),
				org.mockito.ArgumentMatchers.anyString());
		verifyNoInteractions(this.interpreter);
	}

	private static AppointmentInterpretation interpretation(int duration) {
		return new AppointmentInterpretation("check-up", null, duration,
				List.of(new InterpretationWindow(DayOfWeek.MONDAY, "morning", null, null, null)), List.of(), List.of(),
				null, Urgency.ROUTINE);
	}

}
