package org.springframework.samples.petclinic.scheduling.queue;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StaffQueueRepository extends JpaRepository<StaffQueueItem, Integer> {

	Optional<StaffQueueItem> findByRequestId(Integer requestId);

	@EntityGraph(attributePaths = { "request", "request.pet", "request.currentRevision" })
	List<StaffQueueItem> findByStateNotOrderByPriorityAscIdAsc(QueueState state);

	@EntityGraph(attributePaths = { "request", "request.pet", "request.currentRevision" })
	@Query("SELECT item FROM StaffQueueItem item WHERE item.id = :id")
	Optional<StaffQueueItem> findDetailedById(Integer id);

}
