package org.springframework.samples.petclinic.audit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProtectedPayloadRepository extends JpaRepository<ProtectedPayload, Long> {

	Optional<ProtectedPayload> findByArtifactUuid(UUID artifactUuid);

	List<ProtectedPayload> findByIdGreaterThanOrderByIdAsc(Long id, Pageable pageable);

	List<ProtectedPayload> findAllByOrderByIdAsc();

}
