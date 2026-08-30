package org.springframework.samples.petclinic.scheduling.queue;

import org.springframework.samples.petclinic.scheduling.appointment.BookingAuthorization;
import org.springframework.samples.petclinic.scheduling.appointment.StaffBookingService;
import org.springframework.samples.petclinic.scheduling.appointment.ReservationService;
import org.springframework.samples.petclinic.scheduling.matching.CandidateSlot;
import org.springframework.samples.petclinic.scheduling.request.ConsentRecord;
import org.springframework.samples.petclinic.scheduling.request.ConsentRecordRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffAssistedSchedulingService {

	private final SchedulingRequestRepository requests;

	private final ConsentRecordRepository consents;

	private final ReservationService reservations;

	private final StaffBookingService booking;

	private final StaffQueueAuditService audit;

	public StaffAssistedSchedulingService(SchedulingRequestRepository requests, ConsentRecordRepository consents,
			ReservationService reservations, StaffBookingService booking, StaffQueueAuditService audit) {
		this.requests = requests;
		this.consents = consents;
		this.reservations = reservations;
		this.booking = booking;
		this.audit = audit;
	}

	public void assertLlmForbiddenWhenDeclined(Long requestId) {
		SchedulingRequest request = this.requests.findById(requestId).orElseThrow();
		ConsentRecord consent = this.consents
			.findFirstByTextRevisionIdOrderByDecidedAtDesc(request.getActiveTextRevisionId())
			.orElse(null);
		if (consent != null && "DECLINE".equals(consent.getDecision())) {
			throw new IllegalStateException("LLM_FORBIDDEN_AFTER_DECLINE");
		}
	}

	@Transactional
	public void holdExactSlot(Long requestId, Long staffAccountId, CandidateSlot slot) {
		this.reservations.acquireExact(requestId, slot, "STAFF", "STAFF_HOLD");
		this.audit.record(staffAccountId, "STAFF_HOLD", requestId, null);
	}

	@Transactional
	public void book(Long requestId, Long staffAccountId, CandidateSlot slot, BookingAuthorization authorization,
			String reason) {
		this.booking.bookDirect(requestId, slot, authorization, reason);
		this.audit.record(staffAccountId, "STAFF_BOOK", requestId, this.audit.json("basis", authorization.basis()));
	}

}
