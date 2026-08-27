package org.springframework.samples.petclinic.scheduling.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.samples.petclinic.scheduling.model.OperationStatus;
import org.springframework.samples.petclinic.scheduling.model.OperationToken;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

@Repository
public interface OperationTokenRepository extends JpaRepository<OperationToken, Integer> {

	Optional<OperationToken> findByToken(String token);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT o FROM OperationToken o WHERE o.token = :token")
	Optional<OperationToken> findByTokenForUpdate(@Param("token") String token);

	List<OperationToken> findByRequestId(Integer requestId);

	List<OperationToken> findByStatusAndDeadlineLessThan(OperationStatus status, Instant deadline);

	long countByRequestOwnerIdAndOperationTypeAndDispatchedAtAfter(Integer ownerId,
			org.springframework.samples.petclinic.scheduling.model.OperationType operationType, Instant after);

	List<OperationToken> findByRequestOwnerIdAndOperationTypeAndDispatchedAtAfterOrderByDispatchedAtAsc(Integer ownerId,
			org.springframework.samples.petclinic.scheduling.model.OperationType operationType, Instant after);

}
