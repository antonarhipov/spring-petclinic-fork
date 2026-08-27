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
import org.springframework.samples.petclinic.scheduling.model.Reservation;
import org.springframework.samples.petclinic.scheduling.model.ReservationStatus;
import org.springframework.samples.petclinic.scheduling.model.ReservationType;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Integer> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT r FROM Reservation r WHERE r.id = :id")
	Optional<Reservation> findByIdForUpdate(@Param("id") Integer id);

	List<Reservation> findByRequestId(Integer requestId);

	@EntityGraph(attributePaths = { "vet", "vet.specialties" })
	@Query("SELECT r FROM Reservation r WHERE r.request.id = :requestId")
	List<Reservation> findWithVetByRequestId(@Param("requestId") Integer requestId);

	Optional<Reservation> findByRequestIdAndStatus(Integer requestId, ReservationStatus status);

	List<Reservation> findByVetIdAndStatus(Integer vetId, ReservationStatus status);

	List<Reservation> findByStatusAndExpiresAtLessThan(ReservationStatus status, Instant expiresAt);

	List<Reservation> findByVetIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(Integer vetId,
			ReservationStatus status, Instant end, Instant start);

	List<Reservation> findByOwnerIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(Integer ownerId,
			ReservationStatus status, Instant end, Instant start);

	List<Reservation> findByPetIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(Integer petId,
			ReservationStatus status, Instant end, Instant start);

	@Query("SELECT r.id FROM Reservation r WHERE r.reservationType = 'GUIDED_HOLD' AND r.status = 'ACTIVE' AND r.expiresAt <= :cutoff ORDER BY r.expiresAt ASC")
	List<Integer> findExpiredGuidedHoldIds(@Param("cutoff") Instant cutoff, Pageable pageable);

	@Query("SELECT r.id FROM Reservation r WHERE r.reservationType = 'STAFF_OFFER' AND r.status = 'ACTIVE' AND r.expiresAt <= :cutoff ORDER BY r.expiresAt ASC")
	List<Integer> findExpiredStaffOfferIds(@Param("cutoff") Instant cutoff, Pageable pageable);

}
