package org.springframework.samples.petclinic.scheduling.job;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BackgroundJobRepository extends JpaRepository<BackgroundJob, Long> {

	Optional<BackgroundJob> findByTextRevisionId(Long textRevisionId);

	Optional<BackgroundJob> findByWorkflowRevisionId(Long workflowRevisionId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT j FROM BackgroundJob j WHERE (j.state = 'PENDING' OR (j.state = 'RUNNING' AND j.leaseUntil < :now)) "
			+ "AND (j.availableAt IS NULL OR j.availableAt <= :now) ORDER BY j.id ASC")
	List<BackgroundJob> findAvailableJobsForLease(@Param("now") Instant now);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT j FROM BackgroundJob j WHERE j.state = 'RUNNING' AND j.leaseUntil < :now")
	List<BackgroundJob> findExpiredLeasedJobs(@Param("now") Instant now);

}
