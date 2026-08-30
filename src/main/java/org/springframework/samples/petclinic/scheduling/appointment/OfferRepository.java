package org.springframework.samples.petclinic.scheduling.appointment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OfferRepository extends JpaRepository<Offer, Long> {

	List<Offer> findByRequestRevisionIdOrderByCreatedAtDesc(Long requestRevisionId);

	Optional<Offer> findFirstByRequestRevisionIdAndStatusOrderByCreatedAtDesc(Long requestRevisionId,
			OfferStatus status);

}
