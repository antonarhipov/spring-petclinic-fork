package org.springframework.samples.petclinic.scheduling.request;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SchedulingRequestRepository extends JpaRepository<SchedulingRequest, Integer> {

	Optional<SchedulingRequest> findByActivePetId(Integer activePetId);

	@Query("select request from SchedulingRequest request, Owner owner join owner.pets pet where request.id = :id and owner.id = :ownerId and request.pet = pet")
	Optional<SchedulingRequest> findByIdAndPetOwnerId(@Param("id") Integer id, @Param("ownerId") Integer ownerId);

	@Query("select request from SchedulingRequest request, Owner owner join owner.pets pet where owner.id = :ownerId and request.pet = pet order by request.createdDate desc, request.createdTime desc")
	List<SchedulingRequest> findByPetOwnerIdOrderByCreatedDateDescCreatedTimeDesc(@Param("ownerId") Integer ownerId);

	long countByActivePetIdIsNotNull();

	@Modifying(flushAutomatically = true)
	@Query("update SchedulingRequest request set request.version = request.version + 1 where request.id = :id and request.version = :version and request.state = :state")
	int claimStaffAction(@Param("id") Integer id, @Param("version") long version, @Param("state") RequestState state);

}
