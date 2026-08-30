package org.springframework.samples.petclinic.scheduling.request;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivePetRequestRepository extends JpaRepository<ActivePetRequest, Integer> {

	Optional<ActivePetRequest> findByRequestId(Long requestId);

}
