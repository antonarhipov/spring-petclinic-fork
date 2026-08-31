package org.springframework.samples.petclinic.scheduling.request;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilityWindowRepository extends JpaRepository<AvailabilityWindow, Long> {

	List<AvailabilityWindow> findByWorkflowRevisionId(Long workflowRevisionId);

}
