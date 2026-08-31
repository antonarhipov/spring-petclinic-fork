package org.springframework.samples.petclinic.audit;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KeyRotationRunRepository extends JpaRepository<KeyRotationRun, Long> {

	List<KeyRotationRun> findByStateOrderByCreatedAtDesc(String state);

	Optional<KeyRotationRun> findFirstByTargetKeyIdAndStateInOrderByCreatedAtDesc(String targetKeyId,
			List<String> states);

}
