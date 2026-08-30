package org.springframework.samples.petclinic.scheduling.availability;

import org.springframework.samples.petclinic.scheduling.audit.AuditService;
import org.springframework.stereotype.Service;

@Service
public class CapacityAuditService {

	private final AuditService audit;

	public CapacityAuditService(AuditService audit) {
		this.audit = audit;
	}

	public void record(Long actorAccountId, String action, String targetType, String targetId, String beforeJson,
			String afterJson) {
		this.audit.record("STAFF", actorAccountId, action, targetType, targetId, null, beforeJson, afterJson);
	}

}
