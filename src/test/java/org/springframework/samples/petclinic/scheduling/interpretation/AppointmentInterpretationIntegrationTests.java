/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.DayOfWeek;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.samples.petclinic.appointment.AppointmentRequest;
import org.springframework.samples.petclinic.appointment.AppointmentRequestRepository;
import org.springframework.samples.petclinic.appointment.AppointmentRequestStatus;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "petclinic.demo-seeding=false")
class AppointmentInterpretationIntegrationTests {

	@Autowired
	private AppointmentInterpretationService interpretationService;

	@Autowired
	private AppointmentRequestRepository requestRepository;

	@Autowired
	private OwnerRepository ownerRepository;

	@MockitoBean
	private AppointmentInterpreter interpreter;

	@Test
	void consentedTextPersistsBeforeAiAndTheReviewedInterpretationCanBeConfirmed() {
		AppointmentRequest request = createDraft();
		this.interpretationService.submitFreeText(request.getId(), "My cat needs a check-up on Monday morning");
		verify(this.interpreter, never()).interpret(org.mockito.ArgumentMatchers.anyString());
		given(this.interpreter.interpret("My cat needs a check-up on Monday morning")).willAnswer(invocation -> {
			AppointmentRequest consented = this.requestRepository.findById(request.getId()).orElseThrow();
			assertThat(consented.isConsentFlag()).isTrue();
			assertThat(consented.getConsentAt()).isNotNull();
			assertThat(consented.getConsentTextSnapshot()).isEqualTo(invocation.getArgument(0));
			return interpretation(500);
		});

		AppointmentInterpretationService.InterpretationResult result = this.interpretationService
			.grantConsentAndInterpret(request.getId());

		assertThat(result.status()).isEqualTo(AppointmentRequestStatus.INTERPRETED);
		assertThat(result.interpretation().estimatedDurationMin()).isEqualTo(120);
		AppointmentRequest interpreted = this.requestRepository.findById(request.getId()).orElseThrow();
		assertThat(interpreted.getInterpretationJson()).contains("Europe/Amsterdam", "morning");

		AppointmentInterpretation edited = this.interpretationService.editStructuredFields(request.getId(),
				interpretation(5));
		assertThat(edited.estimatedDurationMin()).isEqualTo(15);
		verify(this.interpreter, times(1)).interpret(org.mockito.ArgumentMatchers.anyString());
		this.interpretationService.confirm(request.getId());
		assertThat(this.requestRepository.findById(request.getId()).orElseThrow().getStatus())
			.isEqualTo(AppointmentRequestStatus.CONFIRMED);
	}

	private AppointmentRequest createDraft() {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		AppointmentRequest request = new AppointmentRequest();
		request.setOwner(owner);
		request.setPet(owner.getPets().getFirst());
		request.setStatus(AppointmentRequestStatus.DRAFT);
		return this.requestRepository.saveAndFlush(request);
	}

	private static AppointmentInterpretation interpretation(int duration) {
		return new AppointmentInterpretation("check-up", null, duration,
				List.of(new InterpretationWindow(DayOfWeek.MONDAY, "morning", null, null, null)), List.of(), List.of(),
				null, Urgency.ROUTINE);
	}

}
