package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AppointmentRepository extends JpaRepository<Appointment, Integer> {

	@Query(value = """
			SELECT a.* FROM appointments a
			JOIN pets p ON p.id = a.pet_id
			WHERE a.id = :id AND p.owner_id = :ownerId
			""", nativeQuery = true)
	Optional<Appointment> findOwnedById(Integer id, Integer ownerId);

	@EntityGraph(attributePaths = { "pet", "vet" })
	@Query("""
			SELECT appointment FROM Appointment appointment
			WHERE appointment.startAt > :startAt
			AND appointment.status = org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus.CONFIRMED
			AND EXISTS (
				SELECT ownedPet.id FROM Owner owner JOIN owner.pets ownedPet
				WHERE owner.id = :ownerId AND ownedPet.id = appointment.pet.id
			)
			ORDER BY appointment.startAt
			""")
	List<Appointment> findUpcomingForOwner(Integer ownerId, Instant startAt);

	@EntityGraph(attributePaths = { "pet", "vet", "request", "request.currentRevision" })
	@Query("SELECT appointment FROM Appointment appointment WHERE appointment.id = :id")
	Optional<Appointment> findDetailedById(Integer id);

	@EntityGraph(attributePaths = { "pet", "vet", "request", "request.currentRevision" })
	List<Appointment> findByStatusAndStartAtAfterOrderByStartAt(AppointmentStatus status, Instant startAt);

	@EntityGraph(attributePaths = { "pet", "vet" })
	Optional<Appointment> findFirstByRequestIdOrderByIdDesc(Integer requestId);

	List<Appointment> findByVetIdAndStatusAndStartAtBefore(Integer vetId, AppointmentStatus status, Instant end);

}
