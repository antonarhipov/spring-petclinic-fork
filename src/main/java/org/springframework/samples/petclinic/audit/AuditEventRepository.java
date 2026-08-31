package org.springframework.samples.petclinic.audit;

import java.util.List;
import org.springframework.data.repository.Repository;

public interface AuditEventRepository extends Repository<AuditEvent, Long> {

	AuditEvent save(AuditEvent auditEvent);

	List<AuditEvent> findByTargetTypeAndTargetIdOrderByOccurredAtDesc(String targetType, String targetId);

	List<AuditEvent> findByActorAccountIdOrderByOccurredAtDesc(Long actorAccountId);

}
