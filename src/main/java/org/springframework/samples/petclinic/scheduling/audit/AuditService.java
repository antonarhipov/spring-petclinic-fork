package org.springframework.samples.petclinic.scheduling.audit;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

	private final AuditEventRepository events;

	private final Clock clock;

	public AuditService(AuditEventRepository events, Clock clock) {
		this.events = events;
		this.clock = clock;
	}

	@Transactional
	public AuditEvent record(String actorType, Long actorAccountId, String action, String targetType, String targetId,
			Long requestId, String beforeJson, String afterJson) {
		AuditEvent event = new AuditEvent();
		event.setActorType(actorType);
		event.setActorAccountId(actorAccountId);
		event.setOccurredAt(Instant.now(this.clock));
		event.setAction(action);
		event.setTargetType(targetType);
		event.setTargetId(targetId);
		event.setRequestId(requestId);
		event.setBeforeJson(beforeJson);
		event.setAfterJson(afterJson);
		return this.events.save(event);
	}

}
