package org.springframework.samples.petclinic.scheduling.request;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ActiveSchedulingRequestRepository extends JpaRepository<ActiveSchedulingRequest, Integer> {

	Optional<ActiveSchedulingRequest> findByRequestId(Long requestId);

	void deleteByRequestId(Long requestId);

}
