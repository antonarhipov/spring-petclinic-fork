package org.springframework.samples.petclinic.scheduling.request;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowRevisionRepository extends JpaRepository<WorkflowRevision, Long> {

	List<WorkflowRevision> findByRequestIdOrderByRevisionNumberAsc(Long requestId);

	Optional<WorkflowRevision> findByRequestIdAndRevisionNumber(Long requestId, Integer revisionNumber);

}
