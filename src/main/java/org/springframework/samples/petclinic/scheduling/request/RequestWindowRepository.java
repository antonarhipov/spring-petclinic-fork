package org.springframework.samples.petclinic.scheduling.request;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestWindowRepository extends JpaRepository<RequestWindow, Long> {

	List<RequestWindow> findByRequestRevisionId(Long requestRevisionId);

}
