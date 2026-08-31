package org.springframework.samples.petclinic.audit;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface PayloadKeyEnvelopeRepository extends JpaRepository<PayloadKeyEnvelope, Long> {

	List<PayloadKeyEnvelope> findByPayloadId(Long payloadId);

	Optional<PayloadKeyEnvelope> findByPayloadIdAndKeyId(Long payloadId, String keyId);

	@Query("select count(p) from ProtectedPayload p join p.activeEnvelope e where e.keyId = :keyId")
	long countActiveReferencesByKeyId(@Param("keyId") String keyId);

}
