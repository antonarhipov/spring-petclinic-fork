package org.springframework.samples.petclinic.scheduling.request;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SchedulingRequestRepository extends JpaRepository<SchedulingRequest, Integer> {

	@Query(value = """
			SELECT sr.* FROM scheduling_requests sr
			JOIN pets p ON p.id = sr.pet_id
			WHERE sr.id = :id AND p.owner_id = :ownerId
			""", nativeQuery = true)
	Optional<SchedulingRequest> findOwnedById(Integer id, Integer ownerId);

	@Query(value = """
			SELECT sr.* FROM scheduling_requests sr
			JOIN pets p ON p.id = sr.pet_id
			WHERE p.owner_id = :ownerId ORDER BY sr.id DESC
			""", nativeQuery = true)
	List<SchedulingRequest> findOwnedHistory(Integer ownerId);

	boolean existsByPetIdAndStateIn(Integer petId, List<SchedulingRequestState> states);

}
