package org.springframework.samples.petclinic.scheduling.audit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface IntegrationExecutionRepository extends JpaRepository<IntegrationExecution, UUID> {

	Optional<IntegrationExecution> findByTriggerKey(String triggerKey);

	List<IntegrationExecution> findByState(String state);

	List<IntegrationExecution> findByRequestIdOrderByTriggeredAtDesc(Long requestId);

	@Transactional
	@Modifying(clearAutomatically = true)
	@Query("update IntegrationExecution e set e.state = 'RUNNING', e.startedAt = :startedAt where e.id = :id and e.state = 'PENDING'")
	int claimPending(@Param("id") UUID id, @Param("startedAt") Instant startedAt);

}
