package org.springframework.samples.petclinic.scheduling.request;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestRevisionRepository extends JpaRepository<RequestRevision, Integer> {

	List<RequestRevision> findByRequestIdOrderByRevisionNumberDesc(Integer requestId);

	List<RequestRevision> findByRequestIdOrderByRevisionNumberAsc(Integer requestId);

}
