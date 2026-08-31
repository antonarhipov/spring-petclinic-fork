package org.springframework.samples.petclinic.shared.command;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CommandRepository extends JpaRepository<CommandRecord, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT c FROM CommandRecord c WHERE c.id = :id")
	Optional<CommandRecord> findByIdForUpdate(@Param("id") UUID id);

}
