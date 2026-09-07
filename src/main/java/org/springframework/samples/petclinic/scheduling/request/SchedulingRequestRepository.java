package org.springframework.samples.petclinic.scheduling.request;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SchedulingRequestRepository extends JpaRepository<SchedulingRequest, Integer> {

	@Query("select request from SchedulingRequest request, Owner owner join owner.pets pet where request.id = :id and owner.id = :ownerId and request.pet = pet")
	Optional<SchedulingRequest> findByIdAndPetOwnerId(@Param("id") Integer id, @Param("ownerId") Integer ownerId);

}
