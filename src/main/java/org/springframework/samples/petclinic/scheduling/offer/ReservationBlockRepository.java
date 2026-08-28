package org.springframework.samples.petclinic.scheduling.offer;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationBlockRepository extends JpaRepository<ReservationBlock, Integer> {

	boolean existsByResourceTypeAndResourceIdAndSlotStart(String resourceType, Integer resourceId,
			java.time.Instant slotStart);

	List<ReservationBlock> findByOwnerTypeAndOwnerId(String ownerType, Integer ownerId);

	void deleteByOwnerTypeAndOwnerId(String ownerType, Integer ownerId);

}
