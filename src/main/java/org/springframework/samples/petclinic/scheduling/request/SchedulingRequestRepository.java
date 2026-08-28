package org.springframework.samples.petclinic.scheduling.request;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SchedulingRequestRepository extends JpaRepository<SchedulingRequest, Integer> {

	@EntityGraph(attributePaths = { "pet", "currentRevision", "currentRevision.availabilityWindows" })
	@Query("""
			SELECT request FROM SchedulingRequest request
			JOIN FETCH request.pet pet
			LEFT JOIN FETCH request.currentRevision
			WHERE request.id = :id AND EXISTS (
				SELECT ownedPet.id FROM Owner owner JOIN owner.pets ownedPet
				WHERE owner.id = :ownerId AND ownedPet.id = pet.id
			)
			""")
	Optional<SchedulingRequest> findOwnedById(Integer id, Integer ownerId);

	@EntityGraph(attributePaths = { "pet", "currentRevision", "currentRevision.availabilityWindows" })
	@Query("""
			SELECT request FROM SchedulingRequest request
			WHERE EXISTS (
				SELECT ownedPet.id FROM Owner owner JOIN owner.pets ownedPet
				WHERE owner.id = :ownerId AND ownedPet.id = request.pet.id
			)
			ORDER BY request.id DESC
			""")
	List<SchedulingRequest> findOwnedHistory(Integer ownerId);

	boolean existsByPetIdAndStateIn(Integer petId, List<SchedulingRequestState> states);

}
