package org.springframework.samples.petclinic.scheduling.queue;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.samples.petclinic.scheduling.interpretation.Urgency;

public interface QueueItemRepository extends JpaRepository<QueueItem, Long> {

	Optional<QueueItem> findByRequestId(Long requestId);

	List<QueueItem> findByState(QueueState state);

	List<QueueItem> findByAssigneeAccountId(Long assigneeAccountId);

	@Query("""
			SELECT q FROM QueueItem q
			ORDER BY
			  CASE WHEN q.urgency = org.springframework.samples.petclinic.scheduling.interpretation.Urgency.EMERGENCY_SUSPECTED THEN 0 ELSE 1 END ASC,
			  q.createdAt ASC
			""")
	List<QueueItem> findAllSortedByUrgencyAndAge();

	@Query("""
			SELECT q FROM QueueItem q
			WHERE q.state IN (
			  org.springframework.samples.petclinic.scheduling.queue.QueueState.NEW,
			  org.springframework.samples.petclinic.scheduling.queue.QueueState.IN_REVIEW,
			  org.springframework.samples.petclinic.scheduling.queue.QueueState.AWAITING_OWNER
			)
			ORDER BY
			  CASE WHEN q.urgency = org.springframework.samples.petclinic.scheduling.interpretation.Urgency.EMERGENCY_SUSPECTED THEN 0 ELSE 1 END ASC,
			  q.createdAt ASC
			""")
	List<QueueItem> findActiveItemsSorted();

	@Query("""
			SELECT q FROM QueueItem q
			WHERE (:state IS NULL OR q.state = :state)
			  AND (:assigneeId IS NULL OR q.assigneeAccountId = :assigneeId)
			  AND (:urgency IS NULL OR q.urgency = :urgency)
			  AND (:fallbackReason IS NULL OR q.fallbackReason = :fallbackReason)
			ORDER BY
			  CASE WHEN q.urgency = org.springframework.samples.petclinic.scheduling.interpretation.Urgency.EMERGENCY_SUSPECTED THEN 0 ELSE 1 END ASC,
			  q.createdAt ASC
			""")
	List<QueueItem> findFiltered(@Param("state") QueueState state, @Param("assigneeId") Long assigneeId,
			@Param("urgency") Urgency urgency, @Param("fallbackReason") String fallbackReason);

}
