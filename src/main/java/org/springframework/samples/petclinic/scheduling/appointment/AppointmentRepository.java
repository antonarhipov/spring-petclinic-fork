package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AppointmentRepository extends JpaRepository<Appointment, Integer> {

	@Query(value = """
			SELECT a.* FROM appointments a
			JOIN pets p ON p.id = a.pet_id
			WHERE a.id = :id AND p.owner_id = :ownerId
			""", nativeQuery = true)
	Optional<Appointment> findOwnedById(Integer id, Integer ownerId);

	@Query(value = """
			SELECT a.* FROM appointments a
			JOIN pets p ON p.id = a.pet_id
			WHERE p.owner_id = :ownerId AND a.start_at > :startAt
			ORDER BY a.start_at
			""", nativeQuery = true)
	List<Appointment> findUpcomingForOwner(Integer ownerId, Instant startAt);

	List<Appointment> findByVetIdAndStatusAndStartAtBefore(Integer vetId, AppointmentStatus status, Instant end);

}
