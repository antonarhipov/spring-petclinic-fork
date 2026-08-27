package org.springframework.samples.petclinic.scheduling.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.samples.petclinic.scheduling.model.AuditRecord;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditRecordRepository extends JpaRepository<AuditRecord, Integer> {

	List<AuditRecord> findAllByOrderByOccurredAtDesc();

	List<AuditRecord> findAllByOrderByOccurredAtDesc(Pageable pageable);

	List<AuditRecord> findByTargetEntityTypeAndTargetEntityIdOrderByOccurredAtDesc(String targetEntityType,
			String targetEntityId);

	List<AuditRecord> findByTargetEntityTypeAndTargetEntityIdInOrderByOccurredAtDesc(String targetEntityType,
			Collection<String> targetEntityIds);

	@Query("SELECT a FROM AuditRecord a WHERE "
			+ "(a.targetEntityType = 'SCHEDULING_REQUEST' AND a.targetEntityId = :requestId) "
			+ "OR (a.targetEntityType = 'RESERVATION' AND a.targetEntityId IN :reservationIds) "
			+ "OR (a.targetEntityType = 'APPOINTMENT' AND a.targetEntityId = :appointmentId) "
			+ "ORDER BY a.occurredAt DESC")
	List<AuditRecord> findTimelineForRequest(@Param("requestId") String requestId,
			@Param("reservationIds") Collection<String> reservationIds, @Param("appointmentId") String appointmentId);

}
