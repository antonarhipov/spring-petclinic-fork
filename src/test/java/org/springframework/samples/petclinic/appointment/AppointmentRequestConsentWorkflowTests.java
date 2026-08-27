/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.appointment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.samples.petclinic.vet.VetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AppointmentRequestConsentWorkflowTests {

	private static final Instant NOW = Instant.parse("2026-08-28T10:15:30Z");

	@Mock
	private AppointmentRequestRepository requestRepository;

	@Mock
	private AppointmentRepository appointmentRepository;

	@Mock
	private SlotHoldRepository slotHoldRepository;

	@Mock
	private RejectedSuggestionRepository rejectedSuggestionRepository;

	@Mock
	private VetRepository vetRepository;

	private AppointmentRequestWorkflowService workflowService;

	@BeforeEach
	void setUp() {
		this.workflowService = new AppointmentRequestWorkflowService(this.requestRepository, this.appointmentRepository,
				this.slotHoldRepository, this.rejectedSuggestionRepository, this.vetRepository,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void submittingTextArmsConsentAndRevisionInvalidatesPriorConsentAndInterpretation() {
		AppointmentRequest request = request(AppointmentRequestStatus.INTERPRETED);
		request.setConsentFlag(true);
		request.setConsentAt(NOW.minusSeconds(60));
		request.setConsentTextSnapshot("old text");
		request.setInterpretationJson("{\"old\":true}");
		given(this.requestRepository.findByIdForUpdate(1)).willReturn(Optional.of(request));

		this.workflowService.submitFreeText(1, " revised text ");

		assertThat(request.getStatus()).isEqualTo(AppointmentRequestStatus.AWAITING_CONSENT);
		assertThat(request.getFreeText()).isEqualTo("revised text");
		assertThat(request.isConsentFlag()).isFalse();
		assertThat(request.getConsentAt()).isNull();
		assertThat(request.getConsentTextSnapshot()).isNull();
		assertThat(request.getInterpretationJson()).isNull();
	}

	@Test
	void recordsExactTextSnapshotAndFlushesItBeforeInterpretation() {
		AppointmentRequest request = request(AppointmentRequestStatus.AWAITING_CONSENT);
		request.setFreeText("exact text");
		given(this.requestRepository.findByIdForUpdate(1)).willReturn(Optional.of(request));

		String snapshot = this.workflowService.recordConsent(1);

		assertThat(snapshot).isEqualTo("exact text");
		assertThat(request.isConsentFlag()).isTrue();
		assertThat(request.getConsentAt()).isEqualTo(NOW);
		assertThat(request.getConsentTextSnapshot()).isEqualTo("exact text");
		verify(this.requestRepository).saveAndFlush(request);
	}

	@Test
	void onlyMatchingConsentedTextCanCompleteInterpretation() {
		AppointmentRequest request = request(AppointmentRequestStatus.AWAITING_CONSENT);
		request.setFreeText("changed after consent");
		request.setConsentFlag(true);
		request.setConsentAt(NOW);
		request.setConsentTextSnapshot("original");
		given(this.requestRepository.findByIdForUpdate(1)).willReturn(Optional.of(request));

		assertThatThrownBy(() -> this.workflowService.completeInterpretation(1, "{}"))
			.isInstanceOf(IllegalStateException.class);
		assertThat(request.getStatus()).isEqualTo(AppointmentRequestStatus.AWAITING_CONSENT);
		assertThat(request.getInterpretationJson()).isNull();
	}

	@Test
	void confirmationIsGuardedAndDecliningConsentQueuesForStaff() {
		AppointmentRequest awaiting = request(AppointmentRequestStatus.AWAITING_CONSENT);
		given(this.requestRepository.findByIdForUpdate(1)).willReturn(Optional.of(awaiting));

		this.workflowService.declineConsent(1);

		assertThat(awaiting.getStatus()).isEqualTo(AppointmentRequestStatus.QUEUED_FOR_STAFF);
		assertThat(awaiting.getFallbackReason()).isEqualTo(FallbackReason.DECLINED_CONSENT);
		AppointmentRequest scheduled = request(AppointmentRequestStatus.SCHEDULED);
		given(this.requestRepository.findByIdForUpdate(2)).willReturn(Optional.of(scheduled));
		assertThatThrownBy(() -> this.workflowService.confirmInterpretation(2))
			.isInstanceOf(IllegalStateException.class);
		assertThat(scheduled.getStatus()).isEqualTo(AppointmentRequestStatus.SCHEDULED);
	}

	@Test
	void staffCanUnblockOnlyInterpretedQueuedRequestsAndDirectBookingCompletesThem() {
		AppointmentRequest request = request(AppointmentRequestStatus.QUEUED_FOR_STAFF);
		request.setInterpretationJson("{}");
		request.setFallbackReason(FallbackReason.NO_FEASIBLE_SLOT);
		given(this.requestRepository.findByIdForUpdate(1)).willReturn(Optional.of(request));

		this.workflowService.unblock(1);

		assertThat(request.getStatus()).isEqualTo(AppointmentRequestStatus.SUGGESTING);
		assertThat(request.getFallbackReason()).isNull();

		request.setStatus(AppointmentRequestStatus.QUEUED_FOR_STAFF);
		org.springframework.samples.petclinic.owner.Pet pet = new org.springframework.samples.petclinic.owner.Pet();
		pet.setId(2);
		request.setPet(pet);
		Appointment appointment = new Appointment();
		appointment.setPet(pet);
		this.workflowService.completeByStaff(1, appointment);

		assertThat(request.getStatus()).isEqualTo(AppointmentRequestStatus.SCHEDULED);
		assertThat(request.getResultingAppointment()).isSameAs(appointment);
		assertThat(appointment.getRequest()).isSameAs(request);
	}

	@Test
	void staffCannotUnblockQueuedRequestWithoutInterpretation() {
		AppointmentRequest request = request(AppointmentRequestStatus.QUEUED_FOR_STAFF);
		given(this.requestRepository.findByIdForUpdate(1)).willReturn(Optional.of(request));

		assertThatThrownBy(() -> this.workflowService.unblock(1)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("valid interpretation");
	}

	private static AppointmentRequest request(AppointmentRequestStatus status) {
		AppointmentRequest request = new AppointmentRequest();
		request.setId(1);
		request.setStatus(status);
		return request;
	}

}
