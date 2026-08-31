package org.springframework.samples.petclinic.audit;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuditService {

	private final AuditEventRepository auditEventRepository;

	public AuditService(AuditEventRepository auditEventRepository) {
		this.auditEventRepository = Objects.requireNonNull(auditEventRepository,
				"auditEventRepository must not be null");
	}

	public AuditEvent recordEvent(Long actorAccountId, String action, String targetType, String targetId,
			String outcome, UUID correlationId, UUID commandId, Long payloadId) {
		Objects.requireNonNull(action, "action must not be null");
		Objects.requireNonNull(targetType, "targetType must not be null");
		Objects.requireNonNull(targetId, "targetId must not be null");
		Objects.requireNonNull(outcome, "outcome must not be null");

		AuditEvent event = new AuditEvent();
		event.setActorAccountId(actorAccountId);
		event.setAction(action);
		event.setTargetType(targetType);
		event.setTargetId(targetId);
		event.setOutcome(outcome);
		event.setCorrelationId(correlationId != null ? correlationId : UUID.randomUUID());
		event.setCommandId(commandId);
		event.setPayloadId(payloadId);
		event.setOccurredAt(Instant.now());

		return this.auditEventRepository.save(event);
	}

	@Transactional(readOnly = true)
	public List<AuditEvent> findEventsForTarget(String targetType, String targetId) {
		return this.auditEventRepository.findByTargetTypeAndTargetIdOrderByOccurredAtDesc(targetType, targetId);
	}

	@Transactional(readOnly = true)
	public List<AuditEvent> findEventsForActor(Long actorAccountId) {
		return this.auditEventRepository.findByActorAccountIdOrderByOccurredAtDesc(actorAccountId);
	}

}
