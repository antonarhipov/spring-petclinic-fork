package org.springframework.samples.petclinic.scheduling.queue;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffQueueRepository extends JpaRepository<StaffQueueItem, Long> {

	Optional<StaffQueueItem> findByRequestId(Long requestId);

}
