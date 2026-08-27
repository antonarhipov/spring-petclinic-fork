package org.springframework.samples.petclinic.scheduling.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.samples.petclinic.scheduling.model.RequestStatus;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

@Repository
public interface SchedulingRequestRepository extends JpaRepository<SchedulingRequest, Integer> {

	@EntityGraph(attributePaths = { "owner", "pet", "pet.type", "requiredSpecialty", "preferredVet" })
	@Query("SELECT r FROM SchedulingRequest r WHERE r.id = :id")
	Optional<SchedulingRequest> findWithDetailsById(@Param("id") Integer id);

	@EntityGraph(attributePaths = { "owner", "pet", "pet.type", "requiredSpecialty", "preferredVet" })
	@Query("SELECT r FROM SchedulingRequest r WHERE r.id = :id AND r.owner.id = :ownerId")
	Optional<SchedulingRequest> findWithDetailsByIdAndOwnerId(@Param("id") Integer id,
			@Param("ownerId") Integer ownerId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT r FROM SchedulingRequest r WHERE r.id = :id")
	Optional<SchedulingRequest> findByIdForUpdate(@Param("id") Integer id);

	List<SchedulingRequest> findByOwnerIdOrderByCreatedAtDesc(Integer ownerId);

	List<SchedulingRequest> findByStatus(RequestStatus status);

	@EntityGraph(attributePaths = { "owner", "pet", "pet.type", "requiredSpecialty", "preferredVet" })
	List<SchedulingRequest> findByStatusInOrderByUrgentDescFirstQueuedAtAsc(List<RequestStatus> statuses);

	Optional<SchedulingRequest> findByIdAndOwnerId(Integer id, Integer ownerId);

	List<SchedulingRequest> findByPetIdAndStatusNotIn(Integer petId, List<RequestStatus> statuses);

	@Query("SELECT r.id FROM SchedulingRequest r WHERE r.retentionDeadline IS NOT NULL AND r.retentionDeadline <= :cutoff AND r.purgedAt IS NULL ORDER BY r.retentionDeadline ASC")
	List<Integer> findOverdueRetentionRequestIds(@Param("cutoff") Instant cutoff, Pageable pageable);

	@Query("SELECT r.id FROM SchedulingRequest r WHERE r.status IN ('STAFF_QUEUED', 'STAFF_OFFERED') AND r.fallbackDeadline IS NOT NULL AND r.fallbackDeadline <= :cutoff ORDER BY r.fallbackDeadline ASC")
	List<Integer> findExpiredFallbackRequestIds(@Param("cutoff") Instant cutoff, Pageable pageable);

	@Query("SELECT r.id FROM SchedulingRequest r WHERE r.status IN ('INTERPRETING', 'MATCHING') ORDER BY r.id ASC")
	List<Integer> findInterruptedRequestIds();

}
