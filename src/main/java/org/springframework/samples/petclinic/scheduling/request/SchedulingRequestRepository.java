package org.springframework.samples.petclinic.scheduling.request;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SchedulingRequestRepository extends JpaRepository<SchedulingRequest, Long> {

	List<SchedulingRequest> findByOwnerIdOrderBySubmittedAtDesc(Integer ownerId);

	List<SchedulingRequest> findByPetIdOrderBySubmittedAtDesc(Integer petId);

	List<SchedulingRequest> findByState(RequestState state);

	Optional<SchedulingRequest> findByIdAndOwnerId(Long id, Integer ownerId);

	boolean existsByOwnerId(Integer ownerId);

	boolean existsByPetId(Integer petId);

}
