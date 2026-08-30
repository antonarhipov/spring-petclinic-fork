package org.springframework.samples.petclinic.scheduling.request;

import org.springframework.samples.petclinic.scheduling.audit.AuditService;
import org.springframework.stereotype.Service;

@Service
public class RequestRecoveryAuditService {

	private final AuditService audit;

	public RequestRecoveryAuditService(AuditService audit) {
		this.audit = audit;
	}

	public void offerOutcome(Long requestId, String action, String afterJson) {
		this.audit.record("OWNER", null, action, "Offer", String.valueOf(requestId), requestId, null, afterJson);
	}

	public void withdrawn(Long requestId, Long accountId) {
		this.audit.record("OWNER", accountId, "REQUEST_WITHDRAWN", "SchedulingRequest", String.valueOf(requestId),
				requestId, null, "{\"state\":\"CLOSED\"}");
	}

}
