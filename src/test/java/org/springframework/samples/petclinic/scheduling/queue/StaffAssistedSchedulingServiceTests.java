package org.springframework.samples.petclinic.scheduling.queue;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.owner.Visit;
import org.springframework.samples.petclinic.owner.VisitCategory;
import org.springframework.samples.petclinic.owner.VisitRepository;
import org.springframework.samples.petclinic.scheduling.appointment.BookingAuthorization;
import org.springframework.samples.petclinic.scheduling.appointment.BookingAuthorizationPolicy;
import org.springframework.samples.petclinic.scheduling.appointment.StaffBookingService;
import org.springframework.samples.petclinic.scheduling.appointment.ReservationService;
import org.springframework.samples.petclinic.scheduling.matching.CandidateSlot;
import org.springframework.samples.petclinic.scheduling.request.ConsentRecord;
import org.springframework.samples.petclinic.scheduling.request.ConsentRecordRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaffAssistedSchedulingServiceTests {

	@Test
	void declinedConsentForbidsLlm() {
		SchedulingRequestRepository requests = mock(SchedulingRequestRepository.class);
		ConsentRecordRepository consents = mock(ConsentRecordRepository.class);
		SchedulingRequest request = new SchedulingRequest();
		request.setActiveTextRevisionId(4L);
		when(requests.findById(1L)).thenReturn(Optional.of(request));
		ConsentRecord consent = new ConsentRecord();
		consent.setDecision("DECLINE");
		when(consents.findFirstByTextRevisionIdOrderByDecidedAtDesc(4L)).thenReturn(Optional.of(consent));
		StaffAssistedSchedulingService service = new StaffAssistedSchedulingService(requests, consents,
				mock(ReservationService.class), mock(StaffBookingService.class), mock(StaffQueueAuditService.class));
		assertThatThrownBy(() -> service.assertLlmForbiddenWhenDeclined(1L)).isInstanceOf(IllegalStateException.class)
			.hasMessage("LLM_FORBIDDEN_AFTER_DECLINE");
	}

	@Test
	void ownerAgreementAndVisitEvidenceAreRequired() {
		VisitRepository visits = mock(VisitRepository.class);
		Visit followUpVisit = new Visit();
		ReflectionTestUtils.setField(followUpVisit, "id", 12);
		followUpVisit.setPetId(7);
		followUpVisit.setDescription("Recheck of ear infection");
		followUpVisit.setCategory(VisitCategory.RECHECK.name());
		when(visits.findById(12)).thenReturn(Optional.of(followUpVisit));
		BookingAuthorizationPolicy policy = new BookingAuthorizationPolicy(visits);
		assertThatThrownBy(() -> policy.validate(null, 7)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> policy
			.validate(new BookingAuthorization("OWNER_AGREEMENT", null, Instant.now(), "PHONE", null), 7))
			.hasMessage("OWNER_AGREEMENT_INCOMPLETE");
		assertThatThrownBy(() -> policy
			.validate(new BookingAuthorization("CLINIC_FOLLOW_UP", 1L, Instant.now(), "VISIT", null), 7))
			.hasMessage("SUPPORTING_VISIT_REQUIRED");
		policy.validate(new BookingAuthorization("OWNER_AGREEMENT", 1L, Instant.now(), "PHONE", null), 7);
		policy.validate(new BookingAuthorization("CLINIC_RECHECK", 1L, Instant.now(), "VISIT", 12), 7);
	}

	@Test
	void bookDelegatesToStaffBookingService() {
		StaffBookingService booking = mock(StaffBookingService.class);
		StaffQueueAuditService audit = mock(StaffQueueAuditService.class);
		when(audit.json("basis", "OWNER_AGREEMENT")).thenReturn("{\"basis\":\"OWNER_AGREEMENT\"}");
		StaffAssistedSchedulingService service = new StaffAssistedSchedulingService(
				mock(SchedulingRequestRepository.class), mock(ConsentRecordRepository.class),
				mock(ReservationService.class), booking, audit);
		CandidateSlot slot = new CandidateSlot("1@t", 1, Instant.parse("2026-03-16T15:00:00Z"),
				Instant.parse("2026-03-16T15:30:00Z"), "STAFF", 0);
		BookingAuthorization auth = new BookingAuthorization("OWNER_AGREEMENT", 2L,
				Instant.parse("2026-03-16T14:00:00Z"), "PHONE", null);
		service.book(1L, 2L, slot, auth, "OWNER_AGREEMENT");
		verify(booking).bookDirect(1L, slot, auth, "OWNER_AGREEMENT");
	}

}
