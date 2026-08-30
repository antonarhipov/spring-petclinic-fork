package org.springframework.samples.petclinic.scheduling.request;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SchedulingRequestRepository extends JpaRepository<SchedulingRequest, Long> {

	List<SchedulingRequest> findByOwnerIdOrderByUpdatedAtDesc(Integer ownerId);

	Optional<SchedulingRequest> findByIdAndOwnerId(Long id, Integer ownerId);

}
