package org.springframework.samples.petclinic.scheduling.queue;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactAttemptRepository extends JpaRepository<ContactAttempt, Long> {

	List<ContactAttempt> findByQueueItemIdOrderByAttemptedAtAsc(Long queueItemId);

}
