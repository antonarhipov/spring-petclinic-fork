package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HoldRepository extends JpaRepository<Hold, Long> {

	Optional<Hold> findByOfferId(Long offerId);

	List<Hold> findByRequestId(Long requestId);

	List<Hold> findByStateAndExpiresAtLessThanEqual(HoldStatus state, Instant expiresAt);

	List<Hold> findByState(HoldStatus state);

}
