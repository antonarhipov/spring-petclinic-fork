package org.springframework.samples.petclinic.scheduling.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.samples.petclinic.scheduling.model.StaffClaim;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

@Repository
public interface StaffClaimRepository extends JpaRepository<StaffClaim, Integer> {

	Optional<StaffClaim> findByRequestId(Integer requestId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT c FROM StaffClaim c WHERE c.request.id = :requestId")
	Optional<StaffClaim> findByRequestIdForUpdate(@Param("requestId") Integer requestId);

	List<StaffClaim> findByStaffUsername(String staffUsername);

	List<StaffClaim> findByReclaimableAfterLessThan(Instant now);

}
