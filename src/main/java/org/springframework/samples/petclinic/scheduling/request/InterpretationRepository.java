package org.springframework.samples.petclinic.scheduling.request;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InterpretationRepository extends JpaRepository<Interpretation, Long> {

	Optional<Interpretation> findByTextRevisionId(Long textRevisionId);

}
