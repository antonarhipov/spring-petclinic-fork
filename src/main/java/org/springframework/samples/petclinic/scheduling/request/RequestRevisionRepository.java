package org.springframework.samples.petclinic.scheduling.request;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestRevisionRepository extends JpaRepository<RequestRevision, Long> {

	List<RequestRevision> findByRequestIdOrderBySequenceAsc(Long requestId);

	int countByRequestId(Long requestId);

}
