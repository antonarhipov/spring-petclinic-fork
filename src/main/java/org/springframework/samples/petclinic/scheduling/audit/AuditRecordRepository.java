package org.springframework.samples.petclinic.scheduling.audit;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, Integer> {

	List<AuditRecord> findByCorrelationIdInOrderByOccurredAtAsc(Collection<String> correlationIds);

	List<AuditRecord> findByTargetTypeAndTargetIdOrderByOccurredAtAsc(String targetType, String targetId);

}
