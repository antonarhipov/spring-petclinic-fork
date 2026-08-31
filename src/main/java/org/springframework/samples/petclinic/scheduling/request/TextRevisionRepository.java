package org.springframework.samples.petclinic.scheduling.request;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TextRevisionRepository extends JpaRepository<TextRevision, Long> {

	List<TextRevision> findByRequestIdOrderByRevisionNumberAsc(Long requestId);

	Optional<TextRevision> findByRequestIdAndRevisionNumber(Long requestId, Integer revisionNumber);

}
