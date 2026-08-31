package org.springframework.samples.petclinic.audit;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PayloadKeyEnvelopeRepository extends JpaRepository<PayloadKeyEnvelope, Long> {

	List<PayloadKeyEnvelope> findByPayloadId(Long payloadId);

	Optional<PayloadKeyEnvelope> findByPayloadIdAndKeyId(Long payloadId, String keyId);

}
