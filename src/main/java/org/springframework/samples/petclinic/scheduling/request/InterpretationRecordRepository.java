package org.springframework.samples.petclinic.scheduling.request;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InterpretationRecordRepository extends JpaRepository<InterpretationRecord, Long> {

	Optional<InterpretationRecord> findFirstByTextRevisionIdOrderByCreatedAtDesc(Long textRevisionId);

}
