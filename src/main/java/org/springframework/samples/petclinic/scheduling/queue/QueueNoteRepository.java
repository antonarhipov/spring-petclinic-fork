package org.springframework.samples.petclinic.scheduling.queue;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QueueNoteRepository extends JpaRepository<QueueNote, Long> {

	List<QueueNote> findByQueueItemIdOrderByCreatedAtAsc(Long queueItemId);

}
