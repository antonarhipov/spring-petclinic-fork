package org.springframework.samples.petclinic.scheduling.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.samples.petclinic.scheduling.model.Rejection;
import org.springframework.stereotype.Repository;

@Repository
public interface RejectionRepository extends JpaRepository<Rejection, Integer> {

	List<Rejection> findByRequestId(Integer requestId);

	boolean existsByRequestIdAndVetIdAndStartTimeAndEndTime(Integer requestId, Integer vetId, Instant startTime,
			Instant endTime);

}
