package org.springframework.samples.petclinic.audit;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuditService {

	private final AuditEventRepository auditEventRepository;

	private final ProtectedPayloadService protectedPayloadService;

	private final ObjectMapper objectMapper;

	public AuditService(AuditEventRepository auditEventRepository, ProtectedPayloadService protectedPayloadService,
			ObjectMapper objectMapper) {
		this.auditEventRepository = Objects.requireNonNull(auditEventRepository,
				"auditEventRepository must not be null");
		this.protectedPayloadService = Objects.requireNonNull(protectedPayloadService,
				"protectedPayloadService must not be null");
		this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
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

	public AuditEvent recordStructuredEvent(Long actorAccountId, String action, String targetType, String targetId,
			String outcome, UUID correlationId, UUID commandId, Object before, Object after) {
		try {
			String snapshot = this.objectMapper.writeValueAsString(new AuditSnapshot(before, after));
			ProtectedPayload payload = this.protectedPayloadService.storeJson("AUDIT_SNAPSHOT", snapshot);
			return recordEvent(actorAccountId, action, targetType, targetId, outcome, correlationId, commandId,
					payload.getId());
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Could not encode the structured audit snapshot", ex);
		}
	}

	@Transactional(readOnly = true)
	public List<AuditEvent> findEventsForTarget(String targetType, String targetId) {
		return this.auditEventRepository.findByTargetTypeAndTargetIdOrderByOccurredAtDesc(targetType, targetId);
	}

	@Transactional(readOnly = true)
	public List<AuditEvent> findEventsForActor(Long actorAccountId) {
		return this.auditEventRepository.findByActorAccountIdOrderByOccurredAtDesc(actorAccountId);
	}

	private record AuditSnapshot(Object before, Object after) {
	}

}
