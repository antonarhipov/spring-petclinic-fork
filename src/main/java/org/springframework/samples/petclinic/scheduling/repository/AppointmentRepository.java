package org.springframework.samples.petclinic.scheduling.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.samples.petclinic.scheduling.model.Appointment;
import org.springframework.samples.petclinic.scheduling.model.AppointmentStatus;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Integer> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT a FROM Appointment a WHERE a.id = :id")
	Optional<Appointment> findByIdForUpdate(@Param("id") Integer id);

	List<Appointment> findByOwnerIdOrderByStartTimeDesc(Integer ownerId);

	Optional<Appointment> findByIdAndOwnerId(Integer id, Integer ownerId);

	List<Appointment> findByVetIdAndStatus(Integer vetId, AppointmentStatus status);

	List<Appointment> findByVetIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(Integer vetId,
			AppointmentStatus status, Instant end, Instant start);

	List<Appointment> findByOwnerIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(Integer ownerId,
			AppointmentStatus status, Instant end, Instant start);

	List<Appointment> findByPetIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(Integer petId,
			AppointmentStatus status, Instant end, Instant start);

	List<Appointment> findByOwnerIdAndStatusAndStartTimeGreaterThanEqualOrderByStartTimeAsc(Integer ownerId,
			AppointmentStatus status, Instant startTime);

	List<Appointment> findByOwnerIdAndStatusOrderByStartTimeDesc(Integer ownerId, AppointmentStatus status);

	Optional<Appointment> findByRequestId(Integer requestId);

	Optional<Appointment> findByVisitId(Integer visitId);

}
