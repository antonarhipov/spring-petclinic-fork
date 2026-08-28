package org.springframework.samples.petclinic.scheduling.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, Integer> {

}
