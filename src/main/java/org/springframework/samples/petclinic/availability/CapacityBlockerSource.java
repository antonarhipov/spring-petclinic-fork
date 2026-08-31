package org.springframework.samples.petclinic.availability;

import java.time.Instant;
import java.util.List;
import org.springframework.samples.petclinic.shared.TimeInterval;

public interface CapacityBlockerSource {

	List<TimeInterval> findVetBlockers(Integer vetId, Instant startAt, Instant endAt);

	List<TimeInterval> findPetBlockers(Integer petId, Instant startAt, Instant endAt);

	List<TimeInterval> findOwnerBlockers(Integer ownerId, Instant startAt, Instant endAt);

}
