package org.springframework.samples.petclinic.scheduling.offer;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OfferRepository extends JpaRepository<Offer, Long> {

	List<Offer> findByRequestIdOrderByStartAtAsc(Long requestId);

	List<Offer> findAllByRequestId(Long requestId);

	Optional<Offer> findByRequestIdAndState(Long requestId, OfferState state);

	Optional<Offer> findByIdAndRequestId(Long id, Long requestId);

	Optional<Offer> findByIdAndOwnerId(Long id, Integer ownerId);

	@Query("SELECT o FROM Offer o WHERE o.petId = :petId AND o.state = 'HELD' AND o.expiresAt > :now")
	List<Offer> findActiveHoldsForPet(@Param("petId") Integer petId, @Param("now") Instant now);

	@Query("SELECT o FROM Offer o WHERE o.vetId = :vetId AND o.state = 'HELD' AND o.expiresAt > :now")
	List<Offer> findActiveHoldsForVet(@Param("vetId") Integer vetId, @Param("now") Instant now);

	@Query("SELECT o FROM Offer o WHERE o.ownerId = :ownerId AND o.state = 'HELD' AND o.expiresAt > :now")
	List<Offer> findActiveHoldsForOwner(@Param("ownerId") Integer ownerId, @Param("now") Instant now);

	@Query("SELECT o FROM Offer o WHERE o.state = 'HELD' AND o.expiresAt <= :now")
	List<Offer> findExpiredHolds(@Param("now") Instant now);

	@Query("SELECT o FROM Offer o WHERE o.vetId = :vetId AND o.state = 'HELD' AND o.expiresAt > :now "
			+ "AND o.startAt < :endAt AND o.endAt > :startAt")
	List<Offer> findOverlappingActiveHoldsForVet(@Param("vetId") Integer vetId, @Param("startAt") Instant startAt,
			@Param("endAt") Instant endAt, @Param("now") Instant now);

}
