package org.springframework.samples.petclinic.scheduling.integration;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.scheduling.audit.AuditService;
import org.springframework.samples.petclinic.scheduling.request.RequestRecoveryAuditService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RequestRecoveryAuditTests {

	@Test
	void withdrawnAndRevisedEventsAreAppendOnly() {
		AuditService audit = mock(AuditService.class);
		RequestRecoveryAuditService recovery = new RequestRecoveryAuditService(audit);
		recovery.withdrawn(5L, 2L);
		recovery.offerOutcome(5L, "REQUEST_REVISED", "{\"state\":\"AWAITING_CONSENT\"}");
		verify(audit).record("OWNER", 2L, "REQUEST_WITHDRAWN", "SchedulingRequest", "5", 5L, null,
				"{\"state\":\"CLOSED\"}");
		verify(audit).record("OWNER", null, "REQUEST_REVISED", "Offer", "5", 5L, null,
				"{\"state\":\"AWAITING_CONSENT\"}");
	}

}
