package org.springframework.samples.petclinic.scheduling.request;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestTextRevisionRepository extends JpaRepository<RequestTextRevision, Long> {

	List<RequestTextRevision> findByRequestIdOrderBySequenceAsc(Long requestId);

	int countByRequestId(Long requestId);

}
