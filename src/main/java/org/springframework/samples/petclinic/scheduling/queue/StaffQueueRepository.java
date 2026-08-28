package org.springframework.samples.petclinic.scheduling.queue;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffQueueRepository extends JpaRepository<StaffQueueItem, Integer> {

	Optional<StaffQueueItem> findByRequestId(Integer requestId);

	List<StaffQueueItem> findByStateNotOrderByPriorityAscIdAsc(QueueState state);

}
