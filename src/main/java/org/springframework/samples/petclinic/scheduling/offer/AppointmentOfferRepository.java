package org.springframework.samples.petclinic.scheduling.offer;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AppointmentOfferRepository extends JpaRepository<AppointmentOffer, Integer> {

	@Query(value = """
			SELECT ao.* FROM appointment_offers ao
			JOIN request_revisions rr ON rr.id = ao.revision_id
			JOIN scheduling_requests sr ON sr.id = rr.request_id
			JOIN pets p ON p.id = sr.pet_id
			WHERE ao.id = :id AND p.owner_id = :ownerId
			""", nativeQuery = true)
	Optional<AppointmentOffer> findOwnedById(Integer id, Integer ownerId);

	Optional<AppointmentOffer> findFirstByRevisionIdAndStateOrderByOfferedAtDesc(Integer revisionId, OfferState state);

	List<AppointmentOffer> findByStateAndExpiresAtBefore(OfferState state, Instant instant);

	List<AppointmentOffer> findByState(OfferState state);

	List<AppointmentOffer> findByRevisionId(Integer revisionId);

}
