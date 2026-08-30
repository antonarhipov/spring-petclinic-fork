package org.springframework.samples.petclinic.scheduling.request;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, Long> {

	Optional<ConsentRecord> findFirstByTextRevisionIdOrderByDecidedAtDesc(Long textRevisionId);

}
